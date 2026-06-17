// ui/i18n/AppText.kt
// 所有页面文字的翻译。一个文件搞定中英文切换
package com.sdaiapp.ui.i18n

// 当前用哪种语言
enum class AppLang { ZH, EN }

// 全部文字都放在这个对象里
class AppText(val lang: AppLang) {

    // 选语言
    companion object {
        fun of(lang: AppLang) = AppText(lang)
    }

    // ====== 通用 ======
    val appName get() = ifCN("SD-AI 本地生图", "SD-AI Local Gen")
    val ok get() = ifCN("确定", "OK")
    val cancel get() = ifCN("取消", "Cancel")
    val save get() = ifCN("保存", "Save")
    val delete get() = ifCN("删除", "Delete")
    val share get() = ifCN("分享", "Share")
    val copy get() = ifCN("复制", "Copy")
    val refresh get() = ifCN("刷新", "Refresh")
    val loading get() = ifCN("加载中...", "Loading...")
    val done get() = ifCN("完成", "Done")
    val failed get() = ifCN("失败", "Failed")
    val error get() = ifCN("错误", "Error")
    val search get() = ifCN("搜索", "Search")
    val add get() = ifCN("添加", "Add")

    // ====== 导航 ======
    val tabGenerate get() = ifCN("生成", "Generate")
    val tabGallery get() = ifCN("图库", "Gallery")
    val tabModels get() = ifCN("模型", "Models")
    val tabParams get() = ifCN("参数", "Params")
    val tabChannels get() = ifCN("频道", "Channels")
    val tabSettings get() = ifCN("设置", "Settings")

    // ====== 生成页 ======
    val generateTitle get() = ifCN("AI 图像生成", "AI Image Generator")
    val connected get() = ifCN("已连接电脑端", "Connected to PC")
    val disconnected get() = ifCN("未连接电脑端", "Not connected")
    val modelLoading get() = ifCN("正在加载模型...", "Loading model...")
    val noModelTip get() = ifCN("暂无可用模型，请先在模型页下载", "No models available")
    val startGenerate get() = ifCN("开始生成", "Start Generate")
    val stopGenerate get() = ifCN("停止生成", "Stop Generate")
    val generating get() = ifCN("正在生成...", "Generating...")
    val cancelling get() = ifCN("正在取消...", "Cancelling...")
    val generatingResult get() = ifCN("生成结果", "Result")
    val waitingGen get() = ifCN("等待生成...", "Waiting...")
    val savedToAlbum get() = ifCN("已保存到相册", "Saved to album")
    val saveFailed get() = ifCN("保存失败", "Save failed")
    val copyFailed get() = ifCN("复制失败", "Copy failed")
    val shareFailed get() = ifCN("分享失败", "Share failed")
    val imageCopied get() = ifCN("图像信息已复制", "Image info copied")
    val online get() = ifCN("在线", "Online")
    val offline get() = ifCN("离线", "Offline")
    val promptLabel get() = ifCN("提示词", "Prompt")
    val promptPlaceholder get() = ifCN("描述你想要生成的图像...", "Describe what you want to create...")
    val negativePromptLabel get() = ifCN("负面提示词", "Negative Prompt")
    val negativePromptPlaceholder get() = ifCN("不想出现的内容...", "Things you don't want...")
    val paramsLabel get() = ifCN("参数设置", "Parameters")
    val stepsLabel get() = ifCN("采样步数", "Steps")
    val seedLabel get() = ifCN("种子", "Seed")
    val seedRandom get() = ifCN("随机", "Random")
    val selectModel get() = ifCN("选择模型", "Select Model")
    val localMnnModel get() = ifCN("本地 MNN 模型", "Local MNN Model")
    val mnnCount get() = ifCN("个", "items")
    val noMnnModel get() = ifCN("未找到 .mnn 模型", "No .mnn model found")
    val mnnDirTip get() = ifCN("模型放到 Download/MNN/ 或 SD-AI/models/ 目录下（含 unet.mnn + vae.mnn）", "Place model in Download/MNN/ or SD-AI/models/ (with unet.mnn + vae.mnn)")
    val loadModel get() = ifCN("加载模型", "Load Model")
    val loaded get() = ifCN("已加载", "Loaded")
    val modelIncomplete get() = ifCN("模型不完整（缺 UNet/VAE）", "Model incomplete (missing UNet/VAE)")
    val remotePc get() = ifCN("远程 PC", "Remote PC")
    val localMnn get() = ifCN("本地 MNN", "Local MNN")
    val decoding get() = ifCN("正在解码...", "Decoding...")
    val elapsed get() = ifCN("已用", "Elapsed")
    val remaining get() = ifCN("剩余", "Remaining")
    val speed get() = ifCN("速度", "Speed")
    val denoisingStrength get() = ifCN("去噪强度", "Denoising Strength")
    val denoisingTip get() = ifCN("低 = 接近原图，高 = 更多创意自由", "Low = close to original, High = more freedom")
    val baseImage get() = ifCN("基础图", "Base Image")
    val selectImage get() = ifCN("选择图片（PNG/JPG/WEBP）", "Select Image (PNG/JPG/WEBP)")

    // ====== 图库页 ======
    val galleryEmpty get() = ifCN("图库为空", "Gallery is empty")
    val galleryEmptyHint get() = ifCN("生成的图片将显示在这里", "Generated images will appear here")
    val clearGallery get() = ifCN("清空图库", "Clear Gallery")
    val confirmClear get() = ifCN("确定要清空所有历史图片吗？", "Delete all history images?")
    val duration get() = ifCN("耗时", "Time")
    val size get() = ifCN("尺寸", "Size")

    // ====== 模型页 ======
    val modelManage get() = ifCN("模型管理", "Model Manager")
    val modelUrl get() = ifCN("模型 URL", "Model URL")
    val startDownload get() = ifCN("开始下载", "Start Download")
    val stop get() = ifCN("停止", "Stop")
    val importingModel get() = ifCN("导入模型", "Import Model")
    val deleteModel get() = ifCN("删除模型", "Delete Model")
    val copyPath get() = ifCN("复制路径", "Copy Path")
    val copyLink get() = ifCN("复制下载链接", "Copy Link")

    // ====== 参数页 ======
    val advanced get() = ifCN("高级选项", "Advanced")
    val useTaesd get() = ifCN("使用 TAESD（更快解码）", "TAESD (faster decode)")
    val useFlashAttn get() = ifCN("使用 Flash Attention", "Flash Attention")
    val vaeTiling get() = ifCN("VAE 分块（节省显存）", "VAE Tiling (save VRAM)")
    val vaeOnCpu get() = ifCN("VAE 放到 CPU 运行", "VAE on CPU")

    // ====== 设置页 ======
    val settingsTitle get() = ifCN("设置", "Settings")
    val connection get() = ifCN("连接", "Connection")
    val pcAddress get() = ifCN("电脑端地址", "PC Address")
    val portLabel get() = ifCN("端口", "Port")
    val testConnection get() = ifCN("测试连接", "Test Connection")
    val connecting get() = ifCN("连接中...", "Connecting...")
    val connectedOk get() = ifCN("连接成功！", "Connected!")
    val connectionFailed get() = ifCN("连接失败", "Connection failed")
    val disconnect get() = ifCN("断开连接", "Disconnect")
    val autoSave get() = ifCN("生成后自动保存", "Auto-save images")
    val saveHistory get() = ifCN("保存历史记录", "Save history")
    val language get() = ifCN("语言", "Language")
    val languageZh get() = ifCN("中文", "中文")
    val languageEn get() = ifCN("English", "English")
    val appVersion get() = ifCN("版本", "Version")
    val buildTime get() = ifCN("构建时间", "Build Time")
    val aboutAuthor get() = ifCN("作者", "Author")
    val aboutGitHub get() = ifCN("项目主页", "GitHub Repo")
    val exportLogs get() = ifCN("导出日志", "Export Logs")
    val viewLogs get() = ifCN("查看日志", "View Logs")
    val clearLogs get() = ifCN("清除日志", "Clear Logs")
    val sendLogs get() = ifCN("发送日志", "Send Logs")
    val permissionRequired get() = ifCN("需要权限", "Permission Required")
    val logSaved get() = ifCN("日志已保存", "Log saved")
    val logSaveFailed get() = ifCN("日志保存失败", "Log save failed")

    // ====== 错误提示 ======
    val needPrompt get() = ifCN("请先输入提示词", "Please enter a prompt")
    val needModel get() = ifCN("请先选择模型", "Please select a model")
    val needConnection get() = ifCN("未连接到后端", "Not connected to server")
    val needMnnModel get() = ifCN("请先加载 MNN 模型", "Please load a MNN model first")
    val backendStarting get() = ifCN("正在启动后端...", "Starting backend...")
    val backendError get() = ifCN("后端错误", "Backend error")
    val timeout get() = ifCN("超时", "Timeout")
    val mnnLoadFailed get() = ifCN("MNN 模型加载失败", "MNN model load failed")
    val mnnGenFailed get() = ifCN("MNN 推理失败", "MNN inference failed")
    val cpuFallback get() = ifCN("检测到 CPU 降级，生成会变慢", "CPU fallback detected, generation will be slower")

    // ====== 遥测 ======
    val cpu get() = ifCN("CPU", "CPU")
    val ram get() = ifCN("内存", "RAM")
    val gpu get() = ifCN("GPU", "GPU")

    // ====== 参数自动同步 ======
    val autoApplyParams get() = ifCN("已根据模型类型自动调整参数", "Auto-adjusted params for model type")

    private fun ifCN(zh: String, en: String) = if (lang == AppLang.ZH) zh else en
}
