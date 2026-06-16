// inference/LocalDreamService.kt
// 本地 AI 图像生成服务 —— 启动 libstable_diffusion_core.so 子进程 + HTTP 通信
//
// 架构（参考 D:\GitHub\off-grid-mobile-ai\android\app\src\main\java\ai\offgridmobile\localdream\LocalDreamModule.kt）：
//   LocalDreamService
//     → 启动 libstable_diffusion_core.so 子进程（HTTP server on localhost:18081）
//     → HTTP POST + SSE 解析 → 拿到图片
//     → 自动获得 DPM 调度器、OpenCL WIDE 调优、QNN NPU 等所有 local-dream 内置优化
package com.sdaiapp.inference

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Base64
import com.sdaiapp.utils.AppLog as Log
import com.sdaiapp.utils.CrashLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * LocalDream 图像生成服务
 *
 * 启动 libstable_diffusion_core.so 作为子进程，进程内跑 HTTP server 在 localhost:18081
 * 通过 HTTP POST 发送生成请求，通过 SSE 解析实时进度
 */
class LocalDreamService(private val context: Context) {

    companion object {
        private const val TAG = "LocalDream"
        private const val EXECUTABLE_NAME = "libstable_diffusion_core.so"
        private const val RUNTIME_DIR = "runtime_libs"
        private const val SERVER_PORT = 18081
        private const val MNN_OPENCL_TUNING_MODE = "WIDE"
        private const val DEFAULT_HTTP_TIMEOUT_MS = 600_000L  // 10 分钟

        // 默认生成参数（参考 off-grid-mobile-ai 的 getConstants）
        // 【优化】默认 8 步 + 384x384：DPM-Solver 8 步能达 Euler 20 步质量
        //         384² ≈ 56% 的 512² 计算量，再叠加 DPM 调度器，单图从 5 分钟压到约 1.5 分钟
        const val DEFAULT_STEPS = 8
        const val DEFAULT_GUIDANCE_SCALE = 7.5f
        const val DEFAULT_WIDTH = 384
        const val DEFAULT_HEIGHT = 384
        val SUPPORTED_WIDTHS = listOf(128, 192, 256, 320, 384, 448, 512)
        val SUPPORTED_HEIGHTS = listOf(128, 192, 256, 320, 384, 448, 512)

        // 输出目录
        fun outputDir(context: Context): File =
            File(context.filesDir, "generated_images").also { it.mkdirs() }
    }

    // 子进程
    private var serverProcess: Process? = null
    private var currentModelPath: String? = null
    private var currentBackend: String? = null
    private var isServerReady = false
    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var monitorJob: Job? = null
    private val generationCancelled = AtomicBoolean(false)
    private var activeGenerationConnection: HttpURLConnection? = null

    // 模型目录解析（CPU 用 clip.mnn / unet.mnn，NPU 用 .bin）
    internal fun resolveModelDir(dir: File, isCpu: Boolean): File? {
        val markerFile = if (isCpu) "unet.mnn" else "unet.bin"
        if (File(dir, markerFile).exists()) return dir

        fun searchDir(current: File, depth: Int): File? {
            if (depth > 3) return null
            current.listFiles()?.filter { it.isDirectory }?.forEach { subDir ->
                if (File(subDir, markerFile).exists()) {
                    Log.d(TAG, "Found $markerFile in: ${subDir.absolutePath}")
                    return subDir
                }
                val deeper = searchDir(subDir, depth + 1)
                if (deeper != null) return deeper
            }
            return null
        }

        return searchDir(dir, 0)
    }

    // SD1.5 模型固定 768
    private fun detectTextEmbeddingSize(): String = "768"

    /**
     * 构建启动命令
     * 关键点：始终传 "clip.mnn"，二进制会自己检测 clip_v2.mnn
     */
    internal fun buildCommand(
        executable: File,
        modelDir: File,
        runtimeDir: File,
        isCpu: Boolean,
    ): List<String> {
        val embeddingSize = detectTextEmbeddingSize()
        Log.d(TAG, "Detected text_embedding_size: $embeddingSize")

        return if (isCpu) {
            // MNN CPU 路径：传 .mnn 文件
            mutableListOf(
                executable.absolutePath,
                "--clip", File(modelDir, "clip.mnn").absolutePath,
                "--unet", File(modelDir, "unet.mnn").absolutePath,
                "--vae_decoder", File(modelDir, "vae_decoder.mnn").absolutePath,
                "--tokenizer", File(modelDir, "tokenizer.json").absolutePath,
                "--port", SERVER_PORT.toString(),
                "--text_embedding_size", embeddingSize,
                "--cpu",
            ).also { cmd ->
                val vaeEncoder = File(modelDir, "vae_encoder.mnn")
                if (vaeEncoder.exists()) {
                    cmd.addAll(listOf("--vae_encoder", vaeEncoder.absolutePath))
                }
            }
        } else {
            // QNN NPU 路径：传 .bin 文件
            val hasMnnClip = File(modelDir, "clip.mnn").exists() || File(modelDir, "clip_v2.mnn").exists()
            val clipFile = if (hasMnnClip) "clip.mnn" else "clip.bin"

            mutableListOf(
                executable.absolutePath,
                "--clip", File(modelDir, clipFile).absolutePath,
                "--unet", File(modelDir, "unet.bin").absolutePath,
                "--vae_decoder", File(modelDir, "vae_decoder.bin").absolutePath,
                "--tokenizer", File(modelDir, "tokenizer.json").absolutePath,
                "--backend", File(runtimeDir, "libQnnHtp.so").absolutePath,
                "--system_library", File(runtimeDir, "libQnnSystem.so").absolutePath,
                "--port", SERVER_PORT.toString(),
                "--text_embedding_size", embeddingSize,
            ).also { cmd ->
                if (hasMnnClip) {
                    cmd.add("--use_cpu_clip")
                }
                val vaeEncoder = File(modelDir, "vae_encoder.bin")
                if (vaeEncoder.exists()) {
                    cmd.addAll(listOf("--vae_encoder", vaeEncoder.absolutePath))
                }
            }
        }
    }

    /**
     * 构建子进程环境变量
     * 关键：MNN_OPENCL_TUNING=WIDE 让 MNN OpenCL 选择更稳定的内核
     */
    internal fun buildEnvironment(runtimeDir: File): Map<String, String> {
        val env = mutableMapOf<String, String>()

        val systemLibPaths = mutableListOf(
            runtimeDir.absolutePath,
            "/system/lib64",
            "/vendor/lib64",
            "/vendor/lib64/egl",
        )

        try {
            val maliSymlink = File("/system/vendor/lib64/egl/libGLES_mali.so")
            if (maliSymlink.exists()) {
                val realPath = maliSymlink.canonicalPath
                val soc = realPath.split("/").getOrNull(realPath.split("/").size - 2)
                if (soc != null) {
                    listOf("/vendor/lib64/$soc", "/vendor/lib64/egl/$soc").forEach { path ->
                        if (!systemLibPaths.contains(path)) systemLibPaths.add(path)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve Mali paths: ${e.message}")
        }

        env["LD_LIBRARY_PATH"] = systemLibPaths.joinToString(":")
        env["DSP_LIBRARY_PATH"] = runtimeDir.absolutePath
        env["ADSP_LIBRARY_PATH"] = runtimeDir.absolutePath
        // 【关键】OpenCL WIDE 调优 —— off-grid-mobile-ai 验证的优化
        env["MNN_OPENCL_TUNING"] = MNN_OPENCL_TUNING_MODE

        return env
    }

    // 准备 runtime 目录：复制可执行文件到 app 私有目录（确保有执行权限）
    private fun prepareRuntimeDir(): File {
        val runtimeDir = File(context.filesDir, RUNTIME_DIR).apply {
            if (!exists()) mkdirs()
        }

        // 复制可执行文件（如果 nativeLibDir 里的 .so 没有执行权限）
        val nativeDir = context.applicationInfo.nativeLibraryDir
        val nativeFile = File(nativeDir, EXECUTABLE_NAME)
        val runtimeFile = File(runtimeDir, EXECUTABLE_NAME)

        if (!runtimeFile.exists() || runtimeFile.length() != nativeFile.length()) {
            try {
                nativeFile.inputStream().use { input ->
                    runtimeFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d(TAG, "Copied $EXECUTABLE_NAME to runtime directory")
            } catch (e: IOException) {
                Log.w(TAG, "Failed to copy executable: ${e.message}")
            }
        }
        runtimeFile.setReadable(true, true)
        runtimeFile.setExecutable(true, true)
        runtimeDir.setReadable(true, true)
        runtimeDir.setExecutable(true, true)
        return runtimeDir
    }

    /**
     * 加载模型（启动子进程）
     * @param modelPath 模型目录路径
     * @param backend "mnn" / "qnn" / "auto"
     * @return (成功?, 错误信息)
     */
    suspend fun loadModel(modelPath: String, backend: String = "mnn"): Pair<Boolean, String?> =
        withContext(Dispatchers.IO) {
            try {
                val rawModelDir = File(modelPath)
                if (!rawModelDir.exists() || !rawModelDir.isDirectory) {
                    return@withContext false to "模型目录不存在: $modelPath"
                }

                val normalizedBackend = when (backend.lowercase()) {
                    "mnn", "cpu" -> "mnn"
                    "qnn", "npu" -> "qnn"
                    else -> "mnn"  // 联发科默认走 mnn
                }

                val cpuModelDir = resolveModelDir(rawModelDir, true)
                val qnnModelDir = resolveModelDir(rawModelDir, false)
                val (selectedBackend, modelDir) = when (normalizedBackend) {
                    "mnn" -> cpuModelDir?.let { "mnn" to it }
                    "qnn" -> qnnModelDir?.let { "qnn" to it }
                    else -> null
                } ?: run {
                    val contents = rawModelDir.listFiles()?.map { it.name }?.joinToString(", ") ?: "empty"
                    return@withContext false to "找不到模型文件: $contents"
                }

                Log.d(TAG, "Backend: requested=$normalizedBackend, selected=$selectedBackend")
                Log.d(TAG, "Model dir: ${modelDir.absolutePath}")

                // 如果已经加载相同模型，跳过
                if (currentModelPath == modelPath && serverProcess?.isAlive == true && isServerReady) {
                    return@withContext true to null
                }

                // 停止旧进程
                stopServer()

                // 启动
                val result = startWithFallback(modelPath, selectedBackend, modelDir, cpuModelDir)
                return@withContext if (result.first) true to null
                else false to result.second
            } catch (e: Exception) {
                Log.e(TAG, "loadModel failed", e)
                CrashLogger.logException(e, "LocalDream loadModel")
                return@withContext false to "加载失败: ${e.message}"
            }
        }

    // 启动并带回退
    private suspend fun startWithFallback(
        modelPath: String, backend: String, modelDir: File, cpuModelDir: File?
    ): Pair<Boolean, String?> {
        val result = tryStartServer(modelPath, modelDir, backend, backend == "mnn")
        if (result.first) return result

        // NPU 失败时回退到 CPU
        if (backend == "qnn" && cpuModelDir != null) {
            Log.w(TAG, "QNN failed, falling back to MNN: ${result.second}")
            stopServer()
            val fallback = tryStartServer(modelPath, cpuModelDir, "mnn", true)
            if (fallback.first) {
                Log.i(TAG, "Successfully fell back to MNN")
                return fallback
            }
            return false to "QNN失败: ${result.second}; MNN也失败: ${fallback.second}"
        }
        return result
    }

    // 启动子进程
    private suspend fun tryStartServer(
        modelPath: String, modelDir: File, backend: String, isCpu: Boolean
    ): Pair<Boolean, String?> {
        val runtimeDir = prepareRuntimeDir()

        // 找可执行文件：先 nativeLibDir（系统可能没执行权限），后 runtime_libs
        val nativeDir = context.applicationInfo.nativeLibraryDir
        val nativeFile = File(nativeDir, EXECUTABLE_NAME)
        val runtimeFile = File(runtimeDir, EXECUTABLE_NAME)

        val executableFile = when {
            nativeFile.exists() -> {
                Log.d(TAG, "Using executable from nativeLibraryDir: ${nativeFile.absolutePath}")
                nativeFile
            }
            runtimeFile.exists() -> {
                Log.d(TAG, "Using executable from runtime_libs: ${runtimeFile.absolutePath}")
                runtimeFile
            }
            else -> {
                return false to "找不到可执行文件: $EXECUTABLE_NAME（应位于 $nativeDir 或 $runtimeDir）"
            }
        }

        // 检查可执行权限
        if (!executableFile.canExecute()) {
            Log.w(TAG, "Executable lacks execute permission, attempting chmod")
            try {
                executableFile.setExecutable(true, false)
                executableFile.setReadable(true, false)
            } catch (e: Exception) {
                return false to "可执行文件无执行权限: ${e.message}"
            }
        }

        val command = buildCommand(executableFile, modelDir, runtimeDir, isCpu)
        val env = buildEnvironment(runtimeDir)

        val modelFiles = modelDir.listFiles()?.map { "${it.name} (${it.length()/1024}KB)" }?.joinToString(", ")
        Log.d(TAG, "Model dir contents: [$modelFiles]")
        Log.d(TAG, "Model dir: ${modelDir.absolutePath}")
        Log.d(TAG, "Backend: $backend (isCpu=$isCpu)")
        Log.d(TAG, "COMMAND: ${command.joinToString(" ")}")
        Log.d(TAG, "LD_LIBRARY_PATH=${env["LD_LIBRARY_PATH"]}")
        Log.d(TAG, "MNN_OPENCL_TUNING=${env["MNN_OPENCL_TUNING"]}")

        // 设备诊断信息
        val deviceInfo = buildString {
            append("Device: ${Build.MANUFACTURER} ${Build.MODEL}, ")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) append("SoC=${Build.SOC_MODEL}, ")
            append("SDK=${Build.VERSION.SDK_INT}, ABI=${Build.SUPPORTED_ABIS.joinToString(",")}")
        }
        Log.d(TAG, deviceInfo)

        try {
            val processBuilder = ProcessBuilder(command).apply {
                directory(executableFile.parentFile)
                redirectErrorStream(true)
                environment().putAll(env)
            }
            serverProcess = processBuilder.start()
            // API 26+ 才支持 pid()，为兼容去掉（alive 已经够用）
            Log.i(TAG, "子进程已启动，alive=${serverProcess?.isAlive}")
        } catch (e: IOException) {
            val cause = e.cause?.message ?: "no cause"
            return false to "启动子进程 IO 异常：${e.javaClass.simpleName}: ${e.message}（cause: $cause）。请检查 LD_LIBRARY_PATH 包含 libMNN.so"
        } catch (e: SecurityException) {
            return false to "启动子进程安全限制：${e.message}。可执行文件权限可能不够"
        } catch (e: Exception) {
            return false to "启动子进程失败：${e.javaClass.simpleName}: ${e.message}"
        }

        currentModelPath = modelPath
        currentBackend = backend
        isServerReady = false

        // 监控 stdout
        startMonitor()

        // 等待 server 就绪
        val timeoutMs = if (isCpu) 180_000L else 120_000L
        Log.d(TAG, "等待 server 就绪（${timeoutMs/1000}s 超时）...")

        // 每 5 秒心跳一次，让用户看到进度
        val heartbeatJob = coroutineScope.launch(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < timeoutMs) {
                delay(5_000)
                val elapsed = (System.currentTimeMillis() - startTime) / 1000
                val alive = serverProcess?.isAlive == true
                Log.d(TAG, "心跳：${elapsed}s 已过，子进程 alive=$alive, serverReady=$isServerReady")
                if (!alive) break
            }
        }

        val ready = waitForServer(timeoutMs)
        heartbeatJob.cancel()

        return if (ready) {
            isServerReady = true
            Log.i(TAG, "✅ Server ready on port $SERVER_PORT (backend: $backend)")
            true to null
        } else {
            val alive = serverProcess?.isAlive == true
            if (alive) {
                Log.e(TAG, "⏱ Server 在 ${timeoutMs/1000}s 内未就绪。可能原因：")
                Log.e(TAG, "  1. OpenCL WIDE 调优首次运行（编译内核，1-2 分钟）")
                Log.e(TAG, "  2. 模型加载慢（大模型要 30s+）")
                Log.e(TAG, "  3. 模型文件损坏或与本地 dream 版本不兼容")
                false to "Server ${timeoutMs/1000}s 内未就绪。查看 logcat 搜 'LocalDream' 标签看子进程输出"
            } else {
                val exitCode = try { serverProcess?.exitValue() } catch (_: Exception) { null }
                val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL else "unknown"
                Log.e(TAG, "💥 子进程退出（exitCode=$exitCode）。可能原因：")
                Log.e(TAG, "  1. 模型文件缺失或路径错（看上面的 'Model dir contents'）")
                Log.e(TAG, "  2. libMNN.so 找不到（看 LD_LIBRARY_PATH）")
                Log.e(TAG, "  3. OpenCL 在本设备（$soc）不可用")
                Log.e(TAG, "  4. libstable_diffusion_core.so 与 local-dream 版本不兼容")
                false to "子进程退出（code=$exitCode, 设备=$soc）。看 logcat 搜 'LocalDream' 找具体原因"
            }
        }
    }

    // 轮询健康检查
    private suspend fun waitForServer(timeoutMs: Long): Boolean {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (serverProcess?.isAlive != true) {
                Log.w(TAG, "Server died while waiting for ready")
                return false
            }
            try {
                val url = URL("http://127.0.0.1:$SERVER_PORT/health")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 1000
                conn.readTimeout = 1000
                conn.requestMethod = "GET"
                val code = conn.responseCode
                conn.disconnect()
                if (code == 200) return true
            } catch (_: Exception) { /* not ready */ }
            delay(500)
        }
        return false
    }

    // 监控 stdout
    private fun startMonitor() {
        monitorJob?.cancel()
        monitorJob = coroutineScope.launch(Dispatchers.IO) {
            try {
                serverProcess?.inputStream?.bufferedReader()?.use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        Log.i(TAG, "[server] $line")
                    }
                }
                val exitCode = serverProcess?.waitFor() ?: -1
                Log.i(TAG, "Server process exited with code: $exitCode")
                isServerReady = false
            } catch (e: Exception) {
                Log.e(TAG, "Monitor error", e)
            }
        }
    }

    // 停止 server
    fun stopServer() {
        monitorJob?.cancel()
        monitorJob = null
        serverProcess?.let { proc ->
            try {
                proc.destroy()
                if (!proc.waitFor(5, TimeUnit.SECONDS)) {
                    proc.destroyForcibly()
                }
                Log.i(TAG, "Server stopped")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping server: ${e.message}")
            }
        }
        serverProcess = null
        currentModelPath = null
        currentBackend = null
        isServerReady = false
    }

    // 卸载模型
    suspend fun unloadModel() = withContext(Dispatchers.IO) {
        stopServer()
    }

    // 模型是否已加载
    fun isModelLoaded(): Boolean = isServerReady && serverProcess?.isAlive == true

    // 检查 server 健康
    private fun checkServerHealth(): Boolean = try {
        val url = URL("http://127.0.0.1:$SERVER_PORT/health")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 3000
        conn.readTimeout = 3000
        conn.requestMethod = "GET"
        val code = conn.responseCode
        conn.disconnect()
        code == 200
    } catch (e: Exception) {
        Log.w(TAG, "Health check failed: ${e.message}")
        false
    }

    // 构建生成请求 body —— 关键：DPM 调度器 + useOpenCL
    private fun buildGenerationBody(
        prompt: String,
        negativePrompt: String,
        steps: Int,
        cfgScale: Float,
        seed: Long,
        width: Int,
        height: Int,
        previewInterval: Int = 2,
    ): JSONObject = JSONObject().apply {
        put("prompt", prompt)
        put("negative_prompt", negativePrompt)
        put("steps", steps)
        put("cfg", cfgScale.toDouble())
        put("seed", if (seed == -1L) (Math.random() * 2147483647).toLong() else seed)
        put("width", width)
        put("height", height)
        // 【关键】DPM 调度器，远快于 Euler
        put("scheduler", "dpm")
        put("show_diffusion_process", true)
        put("show_diffusion_stride", previewInterval)
    }

    /**
     * 生成图片 —— 核心入口
     * @param onProgress 进度回调 (0~1)
     * @return 输出图片路径
     */
    suspend fun generateImage(
        prompt: String,
        negativePrompt: String = "",
        steps: Int = DEFAULT_STEPS,
        cfgScale: Float = DEFAULT_GUIDANCE_SCALE,
        seed: Long = -1L,
        width: Int = DEFAULT_WIDTH,
        height: Int = DEFAULT_HEIGHT,
        onProgress: ((Float, Int, Int) -> Unit)? = null,
    ): String? = withContext(Dispatchers.IO) {
        if (!isServerReady || serverProcess?.isAlive != true) {
            Log.e(TAG, "Server not ready")
            return@withContext null
        }
        if (!checkServerHealth()) {
            isServerReady = false
            Log.e(TAG, "Server not responding")
            return@withContext null
        }

        generationCancelled.set(false)
        var connection: HttpURLConnection? = null

        try {
            val body = buildGenerationBody(prompt, negativePrompt, steps, cfgScale, seed, width, height)
            Log.i(TAG, "Generate: ${body.toString().take(200)}...")

            val url = URL("http://127.0.0.1:$SERVER_PORT/generate")
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "text/event-stream")
                connectTimeout = 10_000
                readTimeout = DEFAULT_HTTP_TIMEOUT_MS.toInt()
            }
            activeGenerationConnection = connection
            connection.outputStream.use { it.write(body.toString().toByteArray()); it.flush() }

            val responseCode = connection.responseCode
            if (responseCode != 200) {
                val errorBody = try {
                    connection.errorStream?.bufferedReader()?.readText() ?: "no error"
                } catch (_: Exception) { "unknown error" }
                Log.e(TAG, "Server returned $responseCode: $errorBody")
                return@withContext null
            }

            // 解析 SSE 流
            val result = parseSseStream(connection, onProgress)
            if (result == null) {
                Log.e(TAG, "No complete result from server")
                return@withContext null
            }

            // 保存最终图片
            val imageId = UUID.randomUUID().toString()
            val outDir = outputDir(context)
            val outPath = File(outDir, "$imageId.png").absolutePath
            saveRgbToPng(result.base64Rgb, result.width, result.height, outPath)
            Log.i(TAG, "Image saved: $outPath (${result.generationTimeMs}ms)")
            outPath
        } catch (e: java.io.EOFException) {
            val alive = serverProcess?.isAlive == true
            Log.e(TAG, "EOFException, server alive: $alive", e)
            if (!alive) isServerReady = false
            null
        } catch (e: Exception) {
            Log.e(TAG, "Generate failed: ${e.javaClass.simpleName}", e)
            null
        } finally {
            activeGenerationConnection = null
            connection?.disconnect()
        }
    }

    // SSE 数据类
    private data class CompleteResult(
        val base64Rgb: String, val width: Int, val height: Int,
        val seed: Long, val generationTimeMs: Long,
    )

    // 解析 SSE 流
    private fun parseSseStream(
        connection: HttpURLConnection,
        onProgress: ((Float, Int, Int) -> Unit)?,
    ): CompleteResult? {
        var completeData: JSONObject? = null

        BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (generationCancelled.get()) {
                    Log.i(TAG, "Generation cancelled")
                    return null
                }
                val trimmed = line!!.trim()
                if (!trimmed.startsWith("data: ")) continue

                try {
                    val data = JSONObject(trimmed.substring(6))
                    when (data.optString("type")) {
                        "progress" -> {
                            val step = data.getInt("step")
                            val total = data.getInt("total_steps")
                            val pct = step.toFloat() / total.toFloat()
                            onProgress?.invoke(pct, step, total)
                        }
                        "complete" -> completeData = data
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse SSE data: ${e.message}")
                }
            }
        }

        if (generationCancelled.get()) return null
        val finalComplete = completeData ?: return null

        return CompleteResult(
            base64Rgb = finalComplete.getString("image"),
            width = finalComplete.getInt("width"),
            height = finalComplete.getInt("height"),
            seed = finalComplete.optLong("seed", 0),
            generationTimeMs = finalComplete.optLong("generation_time_ms", 0),
        )
    }

    // 取消生成
    fun cancelGeneration() {
        generationCancelled.set(true)
        activeGenerationConnection?.let {
            try { it.disconnect() } catch (_: Exception) {}
        }
        activeGenerationConnection = null
    }

    // 列出已生成的图片
    fun listGeneratedImages(): List<File> =
        outputDir(context).listFiles()?.filter { it.extension == "png" } ?: emptyList()

    // 删图
    fun deleteGeneratedImage(id: String): Boolean {
        val file = File(outputDir(context), "$id.png")
        return file.exists() && file.delete()
    }

    // 保存 RGB 到 PNG
    private fun saveRgbToPng(base64Rgb: String, width: Int, height: Int, outputPath: String) {
        val rgbBytes = Base64.decode(base64Rgb, Base64.DEFAULT)
        val expectedSize = width * height * 3
        if (rgbBytes.size != expectedSize) {
            throw IllegalArgumentException("RGB size ${rgbBytes.size} != expected $expectedSize")
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        for (i in 0 until width * height) {
            val idx = i * 3
            val r = rgbBytes[idx].toInt() and 0xFF
            val g = rgbBytes[idx + 1].toInt() and 0xFF
            val b = rgbBytes[idx + 2].toInt() and 0xFF
            pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        File(outputPath).parentFile?.mkdirs()
        FileOutputStream(outputPath).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()
    }

    // 释放资源
    fun release() {
        coroutineScope.cancel()
        stopServer()
    }
}
