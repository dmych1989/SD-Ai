// inference/LocalModelUtils.kt
// 本地模型目录管理 + 扫描工具（替代老的 MnnInferenceEngine 的工具方法）
package com.sdaiapp.inference

import android.content.Context
import com.sdaiapp.utils.AppLog as Log
import java.io.File

/**
 * 本地模型根目录（应用私有 filesDir/models/）
 */
fun modelsDir(context: Context): File =
    File(context.filesDir, "models").also { it.mkdirs() }

/**
 * 本地模型外部存储目录（优先用外部，方便用户导入）
 */
fun externalModelsDir(context: Context): File? =
    context.getExternalFilesDir("models")?.also { it.mkdirs() }

/**
 * 扫描本地模型目录，按目录分组为模型组
 *
 * local-dream 模型目录结构（参考 D:\GitHub\off-grid-mobile-ai\SD1.5MNN\AbsoluteReality）：
 *   clip_v2.mnn
 *   unet.mnn
 *   vae_decoder.mnn
 *   vae_encoder.mnn
 *   tokenizer.json
 *   pos_emb.bin / token_emb.bin（嵌入表）
 */
fun scanLocalMnnModels(context: Context): List<LocalModelInfo> {
    val results = mutableListOf<LocalModelInfo>()
    val seenDirs = mutableSetOf<String>()

    val rootDirs = mutableListOf<File>()

    // 1. 应用内部存储
    val internalDir = modelsDir(context)
    if (internalDir.exists()) rootDirs.add(internalDir)

    // 2. 应用外部存储
    val externalDir = externalModelsDir(context)
    if (externalDir != null && externalDir.exists()) rootDirs.add(externalDir)

    // 3. 公共下载目录
    try {
        val downloadDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
        if (downloadDir.exists()) {
            val dlModels = File(downloadDir, "models")
            if (dlModels.exists()) rootDirs.add(dlModels)
            val dlMnn = File(downloadDir, "MNN")
            if (dlMnn.exists()) rootDirs.add(dlMnn)
        }
    } catch (e: Exception) {
        Log.w("MnnScan", "Download dir scan failed: ${e.message}")
    }

    // 4. SD卡根目录
    try {
        val sdcard = android.os.Environment.getExternalStorageDirectory()
        if (sdcard.exists()) {
            val sdModels = File(sdcard, "models")
            if (sdModels.exists()) rootDirs.add(sdModels)
            val sdMnn = File(sdcard, "MNN")
            if (sdMnn.exists()) rootDirs.add(sdMnn)
            val sdAiModels = File(sdcard, "SD-AI/models")
            if (sdAiModels.exists()) rootDirs.add(sdAiModels)
        }
    } catch (e: Exception) {
        Log.w("MnnScan", "SD card scan failed: ${e.message}")
    }

    // 5. 应用外部存储根
    try {
        val extStorage = context.getExternalFilesDir(null)
        if (extStorage != null && extStorage.exists()) {
            val extModels = File(extStorage, "models")
            if (extModels.exists() && !rootDirs.contains(extModels)) rootDirs.add(extModels)
        }
    } catch (e: Exception) {
        Log.w("MnnScan", "External files dir scan failed: ${e.message}")
    }

    for (rootDir in rootDirs) {
        Log.d("MnnScan", "扫描根目录：${rootDir.absolutePath}")

        // 情况1：子目录里有 .mnn 文件
        rootDir.listFiles()?.forEach { subDir ->
            if (!subDir.isDirectory) return@forEach
            val mnnFiles = walkMnnFiles(subDir)
            if (mnnFiles.isEmpty()) return@forEach

            val dirKey = subDir.absolutePath
            if (seenDirs.contains(dirKey)) return@forEach
            seenDirs.add(dirKey)

            val group = buildModelGroup(subDir, mnnFiles)
            if (group != null) {
                Log.d("MnnScan", "  → 模型组：${group.name}（完整=${group.isComplete}，${group.sizeBytes/1024/1024}MB）")
                results.add(group)
            }
        }

        // 情况2：根目录直接有 .mnn 文件
        val rootMnnFiles = rootDir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() == "mnn" }
            ?: emptyList()
        if (rootMnnFiles.isNotEmpty()) {
            val dirKey = rootDir.absolutePath + "_root"
            if (!seenDirs.contains(dirKey)) {
                seenDirs.add(dirKey)
                val group = buildModelGroup(rootDir, rootMnnFiles)
                if (group != null) {
                    Log.d("MnnScan", "  → 根目录模型组：${group.name}（完整=${group.isComplete}，${group.sizeBytes/1024/1024}MB）")
                    results.add(group)
                }
            }
        }
    }

    Log.d("MnnScan", "合计找到 ${results.size} 个模型组")
    return results
}

/** 递归扫描 .mnn 文件 */
private fun walkMnnFiles(dir: File): List<File> {
    if (!dir.exists() || !dir.isDirectory) return emptyList()
    val result = mutableListOf<File>()
    dir.listFiles()?.forEach { f ->
        if (f.isDirectory) result.addAll(walkMnnFiles(f))
        else if (f.isFile && f.extension.lowercase() == "mnn") result.add(f)
    }
    return result
}

/** 分类 .mnn 文件属于哪个子模型 */
private fun classifyMnnFile(file: File): String {
    val name = file.name.lowercase()
    return when {
        name.contains("text_encoder") || name.contains("clip") -> "text_encoder"
        name.contains("unet") -> "unet"
        name.contains("vae") -> "vae"
        else -> "unknown"
    }
}

/** 构建模型组信息 */
private fun buildModelGroup(dir: File, mnnFiles: List<File>): LocalModelInfo? {
    if (mnnFiles.isEmpty()) return null

    var hasText = false
    var hasUnet = false
    var hasVae = false
    var totalSize = 0L

    dir.listFiles()?.forEach { f ->
        totalSize += f.length()
        if (f.isFile && f.extension.lowercase() == "mnn") {
            when (classifyMnnFile(f)) {
                "text_encoder" -> hasText = true
                "unet" -> hasUnet = true
                "vae" -> hasVae = true
            }
        }
    }

    val isComplete = hasUnet && hasVae  // text_encoder 可选（local-dream 用内置 BPE）
    val groupName = dir.name

    return LocalModelInfo(
        name = groupName,
        dirPath = dir.absolutePath,
        sizeBytes = totalSize,
        hasTextEncoder = hasText,
        hasUnet = hasUnet,
        hasVae = hasVae,
        isComplete = isComplete
    )
}

/**
 * 本地模型信息
 */
data class LocalModelInfo(
    val name: String,           // 模型组名称（目录名）
    val dirPath: String,        // 模型目录路径
    val sizeBytes: Long,        // 总大小
    val hasTextEncoder: Boolean,
    val hasUnet: Boolean,
    val hasVae: Boolean,
    val isComplete: Boolean
)
