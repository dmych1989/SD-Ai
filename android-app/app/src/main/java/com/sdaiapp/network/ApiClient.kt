// network/ApiClient.kt
// 跟电脑端后端对话的网络客户端
// 后端是 sd-vulkan / sd-cuda，默认在 8080 端口
package com.sdaiapp.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readBytes
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// 整个 app 共用一个 ApiClient 实例
object ApiClient {

    // 后端地址，电脑端跑的程序监听的端口
    private const val DEFAULT_PORT = 1420
    private const val DEFAULT_HOST = "192.168.0.22"
    private const val DEFAULT_URL = "http://$DEFAULT_HOST:$DEFAULT_PORT"

    // 当前连接的后端地址
    @Volatile
    private var baseUrl: String = DEFAULT_URL

    // http 客户端
    private var client: HttpClient? = null

    // json 解析：忽略未知字段，宽松格式
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
    }

    // 初始化或更新后端地址
    fun init(url: String) {
        // 自动补全协议头和端口
        val fixedUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
            "http://$url"
        } else url
        val withPort = if (fixedUrl.contains(":") &&
            !fixedUrl.substringAfter("://").substringBefore("/").contains(":")
        ) {
            "$fixedUrl:$DEFAULT_PORT"
        } else fixedUrl
        baseUrl = withPort.trimEnd('/')
        client?.close()
        client = createClient()
    }

    fun disconnect() {
        client?.close()
        client = null
    }

    fun getBaseUrl(): String = baseUrl

    // 设置新地址
    fun setBaseUrl(url: String) {
        init(url)
    }

    private fun getClient(): HttpClient {
        return client ?: createClient().also { client = it }
    }

    private fun createClient(): HttpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(json)
        }
        install(Logging) {
            level = LogLevel.INFO
        }
        install(HttpTimeout) {
            // 大模型加载很慢，给 5 分钟
            requestTimeoutMillis = 300_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 300_000
        }
        // 允许明文 HTTP（局域网连接电脑后端需要）
        engine {
            requestTimeout = 300_000
        }
    }

    // ==================== 健康检查 ====================

    // 快速检查后端是否在线，返回 true/false
    suspend fun isHealthy(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val resp = getClient().get("$baseUrl/api/health")
            resp.status.isSuccess()
        }.getOrDefault(false)
    }

    // 获取完整健康信息
    suspend fun getHealth(): HealthResponse? = withContext(Dispatchers.IO) {
        runCatching {
            getClient().get("$baseUrl/api/health").body<HealthResponse>()
        }.getOrNull()
    }

    // 获取后端状态（运行中/加载中/就绪）
    suspend fun getBackendStatus(): BackendStatusResponse = withContext(Dispatchers.IO) {
        runCatching {
            getClient().get("$baseUrl/api/backend-status").body<BackendStatusResponse>()
        }.getOrElse { BackendStatusResponse(ready = false, running = false) }
    }

    // 硬件信息（CPU/GPU/内存）
    suspend fun getHardwareSpecs(): HardwareSpecsResponse? = withContext(Dispatchers.IO) {
        runCatching {
            getClient().get("$baseUrl/api/hardware-specs").body<HardwareSpecsResponse>()
        }.getOrNull()
    }

    // 实时硬件使用情况
    suspend fun getTelemetry(): TelemetryResponse? = withContext(Dispatchers.IO) {
        runCatching {
            getClient().get("$baseUrl/api/telemetry").body<TelemetryResponse>()
        }.getOrNull()
    }

    // 可用的后端类型（cuda/vulkan/cpu）
    suspend fun getBackendOptions(): BackendOptionsResponse? = withContext(Dispatchers.IO) {
        runCatching {
            getClient().get("$baseUrl/api/backend-options").body<BackendOptionsResponse>()
        }.getOrNull()
    }

    // ==================== 图像生成 ====================

    // 文生图（输入文字，输出图片）
    suspend fun txt2Img(request: Txt2ImgRequest): Txt2ImgResponse = withContext(Dispatchers.IO) {
        val resp = getClient().post("$baseUrl/v1/images/generations") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (!resp.status.isSuccess()) {
            throw Exception("文生图失败：HTTP ${resp.status.value}")
        }
        resp.body<Txt2ImgResponse>()
    }

    // 图生图（输入文字 + 基础图，输出图片）
    suspend fun img2Img(request: Img2ImgRequest): Txt2ImgResponse = withContext(Dispatchers.IO) {
        val resp = getClient().post("$baseUrl/sdapi/v1/img2img") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (!resp.status.isSuccess()) {
            throw Exception("图生图失败：HTTP ${resp.status.value}")
        }
        resp.body<Txt2ImgResponse>()
    }

    // 获取生成进度（每秒钟问一次）
    suspend fun getGenerationProgress(): GenerationProgressResponse = withContext(Dispatchers.IO) {
        runCatching {
            getClient().get("$baseUrl/api/generation-progress").body<GenerationProgressResponse>()
        }.getOrElse { GenerationProgressResponse(active = false) }
    }

    // ==================== 模型管理 ====================

    // 列出所有本地模型
    suspend fun listModels(): List<ModelInfoNet> = withContext(Dispatchers.IO) {
        runCatching {
            val resp: ModelsListResponse = getClient().get("$baseUrl/api/models").body()
            resp.models
        }.getOrElse { emptyList() }
    }

    // 启动/重启后端（带模型和参数）
    suspend fun restartBackend(request: StartServerRequest): String = withContext(Dispatchers.IO) {
        val resp = getClient().post("$baseUrl/api/restart-backend") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (!resp.status.isSuccess()) {
            throw Exception("启动后端失败：HTTP ${resp.status.value}")
        }
        resp.bodyAsText()
    }

    // 停止后端
    suspend fun stopBackend(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val resp = getClient().post("$baseUrl/api/stop-backend")
            resp.status.isSuccess()
        }.getOrDefault(false)
    }

    // 删除模型文件
    suspend fun deleteModel(filename: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val resp = getClient().post("$baseUrl/api/delete-model") {
                contentType(ContentType.Application.Json)
                setBody(mapOf("filename" to filename))
            }
            resp.status.isSuccess()
        }.getOrDefault(false)
    }

    // 从网络下载模型
    suspend fun downloadModel(url: String): DownloadResponse = withContext(Dispatchers.IO) {
        runCatching {
            getClient().post("$baseUrl/api/download-model") {
                contentType(ContentType.Application.Json)
                setBody(mapOf("url" to url))
            }.body<DownloadResponse>()
        }.getOrElse { DownloadResponse(success = false, message = "请求失败") }
    }

    // 取消下载
    suspend fun cancelDownload(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val resp = getClient().post("$baseUrl/api/cancel-download")
            resp.status.isSuccess()
        }.getOrDefault(false)
    }

    // 下载进度
    suspend fun getDownloadProgress(): DownloadProgressResponse = withContext(Dispatchers.IO) {
        runCatching {
            getClient().get("$baseUrl/api/download-progress").body<DownloadProgressResponse>()
        }.getOrElse { DownloadProgressResponse(active = false) }
    }

    // ==================== 图片输出 ====================

    // 列出已生成的图片
    suspend fun listOutputs(): List<OutputInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val resp: OutputsResponse = getClient().get("$baseUrl/api/outputs").body()
            resp.outputs
        }.getOrElse { emptyList() }
    }

    // 把生成的图片保存到电脑端
    suspend fun saveOutput(imageB64: String, metadata: Map<String, String>): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val resp = getClient().post("$baseUrl/api/save-output") {
                    contentType(ContentType.Application.Json)
                    setBody(SaveOutputRequest(image = imageB64, metadata = metadata))
                }
                resp.status.isSuccess()
            }.getOrDefault(false)
        }

    // 删除输出
    suspend fun deleteOutputs(outputs: List<String>): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val resp = getClient().post("$baseUrl/api/delete-outputs") {
                contentType(ContentType.Application.Json)
                setBody(mapOf("outputs" to outputs))
            }
            resp.status.isSuccess()
        }.getOrDefault(false)
    }

    // 获取图片字节数据（用于显示）
    suspend fun fetchImageBytes(url: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            getClient().get(url).readBytes()
        }.getOrNull()
    }
}

// ==================== 请求体和响应体 ====================

// 文生图请求
@Serializable
data class Txt2ImgRequest(
    val prompt: String,
    @SerialName("negative_prompt") val negativePrompt: String = "",
    val n: Int = 1,
    val size: String = "1024x1024",
    @SerialName("response_format") val responseFormat: String = "b64_json",
    val steps: Int = 4,
    @SerialName("cfg_scale") val cfgScale: Float = 1.0f,
    val seed: Long = -1,
    // sd-vulkan (stable-diffusion.cpp) 的 OpenAI 兼容 API 期望字段名是 "sampler_name"
    // 之前误用 "sample_method" 导致 PC 端忽略，用默认 euler_a —— 这就是"按 PC 端默认设置生图"的根因
    @SerialName("sampler_name") val samplerName: String = "euler_a"
)

// 图生图请求
@Serializable
data class Img2ImgRequest(
    @SerialName("init_images") val initImages: List<String>,
    val prompt: String,
    @SerialName("negative_prompt") val negativePrompt: String = "",
    @SerialName("denoising_strength") val denoisingStrength: Float = 0.7f,
    val steps: Int = 20,
    @SerialName("cfg_scale") val cfgScale: Float = 7.0f,
    val seed: Long = -1,
    val width: Int = 512,
    val height: Int = 512,
    @SerialName("sampler_name") val samplerName: String = "euler_a",
    @SerialName("batch_size") val batchSize: Int = 1,
    @SerialName("n_iter") val nIter: Int = 1,
    @SerialName("send_images") val sendImages: Boolean = true,
    @SerialName("save_images") val saveImages: Boolean = false
)

// 文生图/图生图返回
@Serializable
data class Txt2ImgResponse(
    val data: List<ImageData> = emptyList()
)

@Serializable
data class ImageData(
    @SerialName("b64_json") val b64Json: String = "",
    val url: String? = null,
    val seed: Long = -1
)

// 后端健康检查返回
@Serializable
data class HealthResponse(
    val ok: Boolean = false,
    val build: String? = null,
    val issues: List<String> = emptyList(),
    val checks: Map<String, String> = emptyMap(),
    val ports: List<Int> = emptyList()
)

// 后端状态
@Serializable
data class BackendStatusResponse(
    val ready: Boolean = false,
    val running: Boolean = false,
    val loading: LoadingInfo? = null,
    val settings: BackendSettings? = null,
    val error: String? = null,
    val port: Int? = null
)

@Serializable
data class LoadingInfo(
    val progress: Float = 0f,
    val phase: String = "",
    val speed: String = "",
    val current: Int = 0,
    val total: Int = 0,
    val model: String = "",
    @SerialName("backendMode") val backendMode: String = "",
    val device: String = ""
)

@Serializable
data class BackendSettings(
    val model: String? = null,
    val steps: Int? = null,
    @SerialName("cfg_scale") val cfgScale: Float? = null,
    val sampler: String? = null,
    val threads: Int? = null,
    @SerialName("use_gpu") val useGpu: Boolean? = null
)

// 硬件信息
@Serializable
data class HardwareSpecsResponse(
    @SerialName("os_name") val osName: String = "",
    @SerialName("cpu_name") val cpuName: String = "",
    @SerialName("cpu_cores_physical") val cpuCoresPhysical: Int = 0,
    @SerialName("cpu_cores_logical") val cpuCoresLogical: Int = 0,
    @SerialName("ram_total_gb") val ramTotalGb: Double = 0.0,
    @SerialName("gpu_name") val gpuName: String = ""
)

// 实时硬件使用
@Serializable
data class TelemetryResponse(
    @SerialName("cpu_usage") val cpuUsage: Double = 0.0,
    @SerialName("ram_used_gb") val ramUsedGb: Double = 0.0,
    @SerialName("ram_total_gb") val ramTotalGb: Double = 0.0,
    @SerialName("gpu_name") val gpuName: String = "",
    @SerialName("gpu_usage") val gpuUsage: Double = 0.0,
    @SerialName("vram_used_gb") val vramUsedGb: Double = 0.0,
    @SerialName("vram_total_gb") val vramTotalGb: Double = 0.0
)

// 可用后端
@Serializable
data class BackendOptionsResponse(
    val options: List<BackendOption> = emptyList(),
    @SerialName("cudaAvailable") val cudaAvailable: Boolean = false,
    @SerialName("vulkanAvailable") val vulkanAvailable: Boolean = false,
    @SerialName("defaultBackendType") val defaultBackendType: String = "cpu"
)

@Serializable
data class BackendOption(
    val id: String = "",
    val label: String = "",
    val available: Boolean = false
)

// 模型信息
@Serializable
data class ModelInfoNet(
    val filename: String = "",
    @SerialName("sizeBytes") val sizeBytes: Long = 0,
    val size: String = ""
)

@Serializable
data class ModelsListResponse(
    val models: List<ModelInfoNet> = emptyList()
)

// 启动后端请求
@Serializable
data class StartServerRequest(
    val model: String,
    val steps: Int = 20,
    @SerialName("cfgScale") val cfgScale: Float = 7.0f,
    val sampler: String = "euler_a",
    val threads: Int = 4,
    @SerialName("use_gpu") val useGpu: Boolean = true,
    @SerialName("backend_type") val backendType: String = "auto",
    @SerialName("vae_tiling") val vaeTiling: Boolean = true,
    @SerialName("vae_on_cpu") val vaeOnCpu: Boolean = false
)

// 下载返回
@Serializable
data class DownloadResponse(
    val success: Boolean = false,
    val message: String? = null,
    val filename: String? = null
)

// 下载进度
@Serializable
data class DownloadProgressResponse(
    val active: Boolean = false,
    val filename: String? = null,
    val progress: Double = 0.0,
    val speed: String? = null,
    val eta: String? = null,
    val error: String? = null
)

// 生成进度
@Serializable
data class GenerationProgressResponse(
    val active: Boolean = false,
    val step: Int = 0,
    val steps: Int = 0,
    val speed: String = "",
    val decoding: Boolean = false,
    val error: String? = null
)

// 输出信息
@Serializable
data class OutputInfo(
    val url: String = "",
    val prompt: String = "",
    val seed: Long = 0,
    val timestamp: String? = null
)

@Serializable
data class OutputsResponse(
    val outputs: List<OutputInfo> = emptyList()
)

// 保存输出请求
@Serializable
data class SaveOutputRequest(
    val image: String,
    val metadata: Map<String, String> = emptyMap()
)
