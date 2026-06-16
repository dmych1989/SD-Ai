// data/ModelInfo.kt
package com.sdaiapp.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ModelInfo(
    val name: String,          // 文件名（如 "deliberate_v3.mnn"）
    val displayName: String,    // 显示名称
    val path: String,           // 文件绝对路径
    val sizeMb: Long,          // 文件大小（MB）
    val isLoaded: Boolean = false,
    val supportsImg2Img: Boolean = false,
) : Parcelable
