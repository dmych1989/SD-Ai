// network/PcBackendClient.kt
// 远程模式：通过 WiFi 连接 PC 端 Stable Diffusion 后端
package com.sdaiapp.network

import com.sdaiapp.utils.AppLog as Log
import com.sdaiapp.data.GenerationRequest
import com.sdaiapp.data.GenerationResult
import com.sdaiapp.utils.CrashLogger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * PC 后端 HTTP 客户端
 *
 * PC 端运行 start.bat 后，监听 http://0.0.0.0:1420
 * 手机与 PC 需在同一 WiFi 网段，或 PC 开启移动热点
 *
 * 使用方式：
 * val client = PcBackendClient("192.168.1.42", 1420)
 * val models = client.getModels()
 * val result = client.generate(request)
 * client.close()
 */
class PcBackendClient(
    private val host: String,
    private val port: Int = 1420
) {
    companion object {
        private const val TAG = "PcBackendClient"
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val httpClient = HttpClient(Android) {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 300_000   // 生成请求 5 分钟超时
            connectTimeoutMillis = 10_000
        }
    }

    private val baseUrl get() = "http://$host:$port"

    /** 健康检查 */
    suspend fun checkHealth(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val resp = httpClient.get("$baseUrl/api/health")
            resp.status.isSuccess()
        } catch (e: Exception) {
            CrashLogger.logException(e, "checkHealth($host:$port)")
            false
        }
    }

    /** 获取模型列表 */
    suspend fun getModels(): List<PcModelDto> = withContext(Dispatchers.IO) {
        return@withContext try {
            val resp = httpClient.get("$baseUrl/api/models")
            if (resp.status.isSuccess()) {
                resp.body<List<PcModelDto>>()
            } else emptyList()
        } catch (e: Exception) {
            CrashLogger.logException(e, "getModels")
            emptyList()
        }
    }

    /** 获取系统资源监控 */
    suspend fun getTelemetry(): PcTelemetryDto? = withContext(Dispatchers.IO) {
        return@withContext try {
            val resp = httpClient.get("$baseUrl/api/telemetry")
            if (resp.status.isSuccess()) resp.body<PcTelemetryDto>() else null
        } catch (e: Exception) {
            CrashLogger.logException(e, "getTelemetry")
            null
        }
    }

    /** 获取后端状态 */
    suspend fun getBackendStatus(): PcBackendStatusDto? = withContext(Dispatchers.IO) {
        return@withContext try {
            val resp = httpClient.get("$baseUrl/api/backend-status")
            if (resp.status.isSuccess()) resp.body<PcBackendStatusDto>() else null
        } catch (e: Exception) {
            CrashLogger.logException(e, "getBackendStatus")
            null
        }
    }

    /** 文生图（远程调用 PC 后端）*/
    suspend fun generate(request: GenerationRequest): GenerationResult =
        withContext(Dispatchers.IO) {

            val startTime = System.currentTimeMillis()
            CrashLogger.logInfo("Remote generate: ${request.prompt}")

            return@withContext try {
                val sdRequest = SdGenerateRequestDto(
                    prompt = request.prompt,
                    negative_prompt = request.negativePrompt,
                    width = request.width,
                    height = request.height,
                    steps = request.steps,
                    cfg_scale = request.cfgScale,
                    seed = if (request.seed < 0) (-1).toLong() else request.seed,
                    sampler_name = request.sampler
                )

                val resp = httpClient.post("$baseUrl/v1/images/generations") {
                    contentType(ContentType.Application.Json)
                    setBody(sdRequest)
                }

                if (!resp.status.isSuccess()) {
                    val err = resp.bodyAsText()
                    CrashLogger.logError("Remote generate failed: $err")
                    return@withContext GenerationResult(
                        success = false,
                        errorMessage = "远程生成失败（${resp.status}）：$err"
                    )
                }

                // 响应格式：{ "data": [{ "url": "..." }] } 或 { "images": [...] }
                val bodyText = resp.bodyAsText()
                CrashLogger.logInfo("Remote response: $bodyText")

                // TODO: 根据实际响应格式解析图片 URL 并下载
                // 暂返回成功占位
                val elapsed = System.currentTimeMillis() - startTime
                GenerationResult(
                    success = true,
                    imagePath = null,   // TODO: 下载图片到本地 outputs/
                    elapsedMs = elapsed
                )

            } catch (e: Exception) {
                val elapsed = System.currentTimeMillis() - startTime
                CrashLogger.logException(e, "remote generate after ${elapsed}ms")
                GenerationResult(
                    success = false,
                    errorMessage = "远程调用异常：${e.message}",
                    elapsedMs = elapsed
                )
            }
        }

    /** 重启 PC 后端 */
    suspend fun restartBackend(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val resp = httpClient.post("$baseUrl/api/restart-backend")
            resp.status.isSuccess()
        } catch (e: Exception) {
            CrashLogger.logException(e, "restartBackend")
            false
        }
    }

    /** 停止 PC 后端 */
    suspend fun stopBackend(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val resp = httpClient.post("$baseUrl/api/stop-backend")
            resp.status.isSuccess()
        } catch (e: Exception) {
            CrashLogger.logException(e, "stopBackend")
            false
        }
    }

    fun close() {
        httpClient.close()
    }
}

// ── DTO 定义 ──────────────────────────────────────────

@Serializable
data class PcModelDto(
    val name: String,
    val path: String,
    val size: Long,
    val is_loaded: Boolean
)

@Serializable
data class PcTelemetryDto(
    val cpu_usage: Float,
    val ram_used: Long,
    val ram_total: Long,
    val vram_used: Long? = null,
    val vram_total: Long? = null,
    val gpu_usage: Float? = null
)

@Serializable
data class PcBackendStatusDto(
    val running: Boolean,
    val backend: String? = null,   // "cuda" / "vulkan" / "cpu"
    val model_loaded: String? = null
)

@Serializable
data class SdGenerateRequestDto(
    val prompt: String,
    val negative_prompt: String = "",
    val width: Int = 512,
    val height: Int = 512,
    val steps: Int = 20,
    val cfg_scale: Float = 7.5f,
    val seed: Long = -1,
    val sampler_name: String = "euler_a"
)
