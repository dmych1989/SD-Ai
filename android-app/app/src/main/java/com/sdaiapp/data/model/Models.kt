// data/model/Models.kt
// 数据模型 - 对应 WebUI 的 state 结构
package com.sdaiapp.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/**
 * 服务器状态
 */
@Parcelize
data class ServerStatus(
    val deviceName: String = "",
    val gpuName: String = "",
    val gpuMemoryTotal: Long = 0L,
    val gpuMemoryUsed: Long = 0L,
    val isConnected: Boolean = false,
    val serverUrl: String = ""
) : Parcelable

/**
 * 生成参数 - 对应 WebUI 的 generateParams
 */
@Parcelize
@Serializable
data class GenerateParams(
    val prompt: String = "",
    val negativePrompt: String = "",
    val width: Int = 1024,
    val height: Int = 1024,
    val steps: Int = 20,
    val cfgScale: Float = 7.0f,
    val seed: Long = -1L,
    val batchCount: Int = 1,
    val batchSize: Int = 1,
    val modelName: String = "",
    val scheduler: String = "Euler"
) : Parcelable

/**
 * 图像约束选项 - 对应 WebUI 的 aspect_ratios
 */
@Parcelize
data class AspectRatio(
    val label: String,
    val width: Int,
    val height: Int
) : Parcelable {
    companion object {
        val presets = listOf(
            AspectRatio("1:1 (正方形)", 1024, 1024),
            AspectRatio("16:9 (横版)", 1344, 768),
            AspectRatio("9:16 (竖版)", 768, 1344),
            AspectRatio("3:2", 1152, 768),
            AspectRatio("2:3", 768, 1152),
            AspectRatio("4:3", 1024, 768),
            AspectRatio("21:9 (带鱼)", 1680, 720)
        )
    }
}

/**
 * 模型信息
 */
@Parcelize
data class ModelInfo(
    val name: String,
    val path: String,
    val type: String = "sd", // sd, sdxl, ssd, lora
    val isSelected: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val size: Long = 0L
) : Parcelable

/**
 * 调度器/采样器选项
 */
@Parcelize
data class SchedulerOption(
    val name: String,
    val displayName: String
) : Parcelable {
    companion object {
        val all = listOf(
            SchedulerOption("Euler", "Euler"),
            SchedulerOption("Euler a", "Euler Ancestral"),
            SchedulerOption("DPM++ 2M", "DPM++ 2M"),
            SchedulerOption("DPM++ 2M Karras", "DPM++ 2M Karras"),
            SchedulerOption("DPM++ SDE", "DPM++ SDE"),
            SchedulerOption("DPM++ SDE Karras", "DPM++ SDE Karras"),
            SchedulerOption("DDIM", "DDIM"),
            SchedulerOption("PLMS", "PLMS"),
            SchedulerOption("UniPC", "UniPC")
        )
    }
}

/**
 * 历史记录项
 */
@Parcelize
@Serializable
data class HistoryItem(
    val id: String,
    val imagePath: String,
    val prompt: String,
    val negativePrompt: String = "",
    val params: GenerateParams,
    val timestamp: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false
) : Parcelable

/**
 * 生成状态
 */
enum class GenerationStatus {
    IDLE,
    LOADING_MODEL,
    GENERATING,
    COMPLETED,
    FAILED
}

/**
 * 生成状态信息
 */
@Parcelize
data class GenerationState(
    val status: GenerationStatus = GenerationStatus.IDLE,
    val progress: Float = 0f,
    val currentStep: Int = 0,
    val totalSteps: Int = 20,
    val currentImagePath: String? = null,
    val previewUrl: String? = null,
    val errorMessage: String? = null,
    val estimatedTimeRemaining: Long = 0L
) : Parcelable

/**
 * 应用设置
 */
@Parcelize
data class AppSettings(
    val serverUrl: String = "http://192.168.1.100:7860",
    val useLocalMNN: Boolean = false,
    val mnnModelPath: String = "",
    val autoSaveHistory: Boolean = true,
    val maxHistoryItems: Int = 100,
    val notificationsEnabled: Boolean = true
) : Parcelable