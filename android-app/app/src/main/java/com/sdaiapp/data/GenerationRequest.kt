// data/GenerationRequest.kt
package com.sdaiapp.data

import kotlinx.serialization.Serializable

@Serializable
data class GenerationRequest(
    val prompt: String,
    val negativePrompt: String = "",
    val width: Int = 512,
    val height: Int = 512,
    val steps: Int = 20,
    val cfgScale: Float = 7.5f,
    val seed: Long = -1,
    val sampler: String = "euler_a",
    val modelName: String = "",
    val mode: String = "local",   // "local" | "remote"
    val remoteUrl: String = "",
)

@Serializable
data class GenerationResult(
    val success: Boolean,
    val imagePath: String? = null,
    val errorMessage: String? = null,
    val elapsedMs: Long = 0,
)
