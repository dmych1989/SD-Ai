package com.sdaiapp.inference

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 把用户上传的 SD 1.5 .safetensors 模型在 app 内转成本地 MNN 模型。
 *
 * 原理（参考 xororz/local-dream 同名函数）：
 *   1. 复制 .safetensors 到 models/<name>/model.safetensors
 *   2. 复制 assets/cvtbase/ 下的 6 个 .mnn/tokenizer.json 模板到模型目录
 *      （clip_skip_1/2.mnn 是空权重模板，加载后 native 会自动找同名 .weight）
 *   3. 根据 clipSkip 把 clip_skip_X.mnn 复制成 clip.mnn（匹配本 app buildCommand 的 --clip 参数名）
 *   4. 调 libstable_diffusion_core.so --convert <modelDir> [clipSkip]，native 进程读
 *      .safetensors、按 SDStructure 顺序写 .mnn.weight + 提取 token_emb.bin / pos_emb.bin
 *   5. native 写完写一个 "finished" 标记文件 → 本函数检测到 finished 即认为成功
 *   6. 清理临时文件（model.safetensors、clip_skip_X.mnn）
 *
 * 注意：本函数不保证转换后的模型能完美工作（可能因为模型架构差异 / v-prediction 标记等失败），
 * 但 SD 1.5 / SDXL 基础权重都能跑通，CLIP skip=2 适合写实+动漫多数模型。
 */
object ModelConverter {
    private const val TAG = "ModelConverter"

    /**
     * @param context Android context（用 applicationContext 即可）
     * @param modelName 用户起的模型名（用作 models/ 下的子目录名）
     * @param fileUri 用户选中的 .safetensors 文件 Uri
     * @param clipSkip 1 或 2（动漫/二次元一般用 2，写实用 1，参考 SD 训练时的 clip_skip）
     * @param onProgress 主线程回调，显示当前阶段文案
     * @return 成功返回模型目录，失败返回 null
     */
    suspend fun convertSafetensors(
        context: Context,
        modelName: String,
        fileUri: Uri,
        clipSkip: Int = 2,
        onProgress: (String) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val modelId = modelName.replace(" ", "").ifBlank { "custom_model" }

        try {
            withContext(Dispatchers.Main) { onProgress("准备模型目录...") }

            val modelsDir = File(app.filesDir, "models")
            if (!modelsDir.exists()) modelsDir.mkdirs()

            val modelDir = File(modelsDir, modelId)
            if (modelDir.exists()) modelDir.deleteRecursively()
            modelDir.mkdirs()

            // ========== Step 1: 复制 .safetensors ==========
            withContext(Dispatchers.Main) { onProgress("复制模型文件 (约 1.9GB)...") }
            val input = app.contentResolver.openInputStream(fileUri)
                ?: throw Exception("无法打开所选文件")
            val modelFile = File(modelDir, "model.safetensors")
            input.use { ins ->
                modelFile.outputStream().use { out -> ins.copyTo(out) }
            }
            Log.i(TAG, "复制 safetensors 完毕: ${modelFile.length() / 1024 / 1024}MB → ${modelFile.absolutePath}")

            // ========== Step 2: 复制 cvtbase assets 模板 ==========
            withContext(Dispatchers.Main) { onProgress("复制转换模板...") }
            copyAssetsRecursively(app, "cvtbase", modelDir)
            Log.i(TAG, "cvtbase 模板复制完毕")

            // ========== Step 3: 选 CLIP skip，重命名 ==========
            // 重要：native binary 期望的是 "clip_v2.mnn"（不是 "clip.mnn"）
            // 见 xororz SafeTensor2MNN.hpp:351 generateClipModel 写出的就是 clip_v2.mnn
            // patchModel 也会读 dir/clip_v2.mnn 骨架文件；没有它就直接 return 失败
            // 同时复制 clip.mnn 是给 app 加载时 LocalDreamService 的存在性检查用（向后兼容）
            val clipSourceName = if (clipSkip == 2) "clip_skip_2.mnn" else "clip_skip_1.mnn"
            val clipSource = File(modelDir, clipSourceName)
            if (!clipSource.exists()) {
                throw Exception("cvtbase 模板缺少 $clipSourceName，请检查 assets 完整性")
            }
            // 1) native 转换需要 clip_v2.mnn 骨架（patchModel 会读它填充小权重）
            val clipV2Target = File(modelDir, "clip_v2.mnn")
            clipSource.copyTo(clipV2Target, overwrite = true)
            // 2) 兼容旧 buildCommand（部分代码可能直接检查 clip.mnn）
            val clipLegacy = File(modelDir, "clip.mnn")
            if (!clipLegacy.exists()) {
                clipSource.copyTo(clipLegacy, overwrite = true)
            }
            Log.i(TAG, "CLIP skip=$clipSkip 已就绪: ${clipV2Target.absolutePath} (+兼容 clip.mnn)")

            // ========== Step 4: 调 native --convert ==========
            withContext(Dispatchers.Main) { onProgress("正在转换 (native MNN 量化)...") }
            val nativeDir = app.applicationInfo.nativeLibraryDir
            val executable = File(nativeDir, "libstable_diffusion_core.so")
            if (!executable.exists()) {
                throw Exception("找不到 native 库: ${executable.absolutePath}")
            }

            val command = mutableListOf(
                executable.absolutePath,
                "--convert",
                modelDir.absolutePath
            )
            if (clipSkip == 2) {
                command += "--clip_skip_2"
            }

            val env = mutableMapOf<String, String>()
            val systemLibPaths = listOf(
                nativeDir,
                "/system/lib64",
                "/vendor/lib64",
                "/vendor/lib64/egl"
            ).joinToString(":")
            env["LD_LIBRARY_PATH"] = systemLibPaths
            env["DSP_LIBRARY_PATH"] = nativeDir

            Log.i(TAG, "启动转换进程: ${command.joinToString(" ")}")
            val process = ProcessBuilder(command).apply {
                directory(File(nativeDir))
                redirectErrorStream(true)
                environment().putAll(env)
            }.start()

            // 读取 stdout 实时显示进度
            process.inputStream.bufferedReader().use { reader ->
                reader.lineSequence().forEach { line ->
                    Log.i(TAG, "convert> $line")
                    withContext(Dispatchers.Main) {
                        // 简化进度文案：截取关键阶段
                        val phase = when {
                            line.contains("Reading", true) -> "读取权重..."
                            line.contains("Writing", true) -> "写 .mnn.weight..."
                            line.contains("Quantiz", true) -> "量化..."
                            line.contains("Embedding", true) -> "提取 embedding 表..."
                            else -> line.take(60)
                        }
                        onProgress(phase)
                    }
                }
            }

            val exitCode = process.waitFor()
            Log.i(TAG, "转换进程退出码: $exitCode")

            // ========== Step 5: 检查 finished 标记 ==========
            val finishedFile = File(modelDir, "finished")
            if (!finishedFile.exists()) {
                // 失败：清理
                val stderr = "exitCode=$exitCode, 没有 finished 标记"
                Log.e(TAG, "转换失败: $stderr")
                modelDir.deleteRecursively()
                return@withContext null
            }

            // ========== Step 6: 清理临时文件 ==========
            // 保留：clip.mnn, unet.mnn, vae_decoder.mnn, vae_encoder.mnn,
            //       tokenizer.json, token_emb.bin, pos_emb.bin, *.mnn.weight
            // 删除：model.safetensors (2GB), clip_skip_1/2.mnn (模板)
            if (modelFile.exists()) modelFile.delete()
            listOf("clip_skip_1.mnn", "clip_skip_2.mnn").forEach { name ->
                val f = File(modelDir, name)
                if (f.exists()) f.delete()
            }
            Log.i(TAG, "转换成功: ${modelDir.absolutePath}")

            withContext(Dispatchers.Main) { onProgress("✓ 转换完成") }
            modelDir
        } catch (e: Exception) {
            Log.e(TAG, "转换异常", e)
            val modelDir = File(File(app.filesDir, "models"), modelId)
            if (modelDir.exists()) modelDir.deleteRecursively()
            withContext(Dispatchers.Main) { onProgress("✗ 失败：${e.message}") }
            null
        }
    }

    /**
     * 递归复制 assets 下的子目录/文件到目标目录。
     * xororz 原版：assets.list() 判断是文件还是目录。
     */
    private fun copyAssetsRecursively(context: Context, assetPath: String, targetDir: File) {
        val am = context.assets
        val entries = am.list(assetPath) ?: return

        if (entries.isEmpty()) {
            // 是文件
            try {
                am.open(assetPath).use { input ->
                    val fileName = assetPath.substringAfterLast("/")
                    File(targetDir, fileName).outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "跳过 asset 文件: $assetPath (${e.message})")
            }
        } else {
            // 是目录
            for (entry in entries) {
                val subPath = "$assetPath/$entry"
                val subEntries = am.list(subPath) ?: emptyArray()
                if (subEntries.isEmpty()) {
                    // 文件
                    try {
                        am.open(subPath).use { input ->
                            File(targetDir, entry).outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "跳过 asset 文件: $subPath (${e.message})")
                    }
                } else {
                    // 子目录
                    val subDir = File(targetDir, entry)
                    subDir.mkdirs()
                    copyAssetsRecursively(context, subPath, subDir)
                }
            }
        }
    }
}
