// ui/viewmodel/MainViewModel.kt
// 主 ViewModel - 管理整个 app 的状态
// 负责跟电脑后端通信、控制生成流程
package com.sdaiapp.ui.viewmodel

import android.app.Application
import android.content.Context
import android.os.Build
import com.sdaiapp.utils.AppLog as Log
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.File
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sdaiapp.data.model.AspectRatio
import com.sdaiapp.data.model.GenerateParams
import com.sdaiapp.data.model.HistoryItem
import com.sdaiapp.data.model.ModelInfo
import com.sdaiapp.data.model.ServerStatus
import com.sdaiapp.data.model.AppSettings
import com.sdaiapp.data.model.GenerationState
import com.sdaiapp.data.model.GenerationStatus
import com.sdaiapp.data.HistoryRepository
import com.sdaiapp.inference.LocalDreamService
import com.sdaiapp.inference.LocalModelInfo
import com.sdaiapp.inference.ModelConverter
import com.sdaiapp.inference.scanLocalMnnModels
import com.sdaiapp.inference.modelsDir
import com.sdaiapp.ui.i18n.AppLang
import com.sdaiapp.ui.i18n.AppText
import com.sdaiapp.network.ApiClient
import com.sdaiapp.network.BackendStatusResponse
import com.sdaiapp.network.Img2ImgRequest
import com.sdaiapp.network.ModelInfoNet
import com.sdaiapp.network.StartServerRequest
import com.sdaiapp.network.Txt2ImgRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 手机 SoC 自动检测
 *
 * 不同芯片厂商的 NPU/DSP 加速方式完全不同：
 *  - 高通 (Qualcomm) — Hexagon NPU，对应 QNN 后端
 *  - 联发科 (MediaTek) — APU，对应 qnn-npu 但多数情况 MNN CPU 更稳
 *  - 三星 (Exynos) — NPU
 *  - 紫光展锐 (Unisoc) — 无 NPU
 *  - 海思 (HiSilicon) — NPU
 *
 * Build.SOC_MODEL 字段：API 31+ (Android 12+)
 *   - 高通：SM8450、SM8550、SM7325 等（SM 开头）
 *   - 联发科：MT6983、MT6893 等（MT 开头）
 *   - 紫光展锐：ums9230 等
 */
object SocDetector {
    data class SocInfo(
        val vendor: String,         // "高通" / "联发科" / "三星" / "紫光展锐" / "未知"
        val model: String,          // Build.SOC_MODEL
        val supportsNpu: Boolean,   // 是否有可能用 NPU 加速
        val recommendedBackend: String  // 推荐 "mnn" / "qnn"
    )

    fun detect(): SocInfo {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL else "unknown"
        val socLower = soc.lowercase()

        return when {
            // 高通 — Hexagon NPU
            manufacturer.contains("qualcomm") || brand.contains("qualcomm") ||
            soc.startsWith("SM", ignoreCase = true) -> SocInfo(
                vendor = "高通",
                model = soc,
                supportsNpu = true,
                recommendedBackend = "qnn"
            )
            // 联发科 — APU
            manufacturer.contains("mediatek") || brand.contains("mediatek") ||
            soc.startsWith("MT", ignoreCase = true) -> SocInfo(
                vendor = "联发科",
                model = soc,
                supportsNpu = true,
                recommendedBackend = "mnn"  // 联发科 MNN CPU 通常更稳
            )
            // 三星 Exynos
            manufacturer.contains("samsung") || socLower.contains("exynos") ||
            socLower.startsWith("s5e") -> SocInfo(
                vendor = "三星",
                model = soc,
                supportsNpu = true,
                recommendedBackend = "mnn"
            )
            // 紫光展锐
            manufacturer.contains("unisoc") || socLower.contains("ums") ||
            socLower.contains("sc9863") || socLower.contains("t310") ||
            socLower.contains("t606") || socLower.contains("t612") -> SocInfo(
                vendor = "紫光展锐",
                model = soc,
                supportsNpu = false,
                recommendedBackend = "mnn"
            )
            // 海思
            manufacturer.contains("hisilicon") || socLower.contains("kirin") -> SocInfo(
                vendor = "海思",
                model = soc,
                supportsNpu = true,
                recommendedBackend = "mnn"
            )
            else -> SocInfo(
                vendor = "未知",
                model = soc,
                supportsNpu = false,
                recommendedBackend = "mnn"
            )
        }
    }
}

// app 整体状态（一个大对象统一管理）
data class AppUiState(
    // 连接相关
    val isConnected: Boolean = false,
    val pcBackendUrl: String = "http://192.168.0.22:1420",
    val connectionError: String? = null,
    val isCheckingConnection: Boolean = false,
    val backendReady: Boolean = false,
    val backendRunning: Boolean = false,
    val useLocalInference: Boolean = true,  // 默认使用本地 MNN 推理

    // 提示词
    val prompt: String = "and neon shades, wabi sabi, 1 girl, looking at viewer, dynamic angle, from below, from side, relaxing",
    val negativePrompt: String = "blurry, low quality, deformed hands, texts",

    // 生成参数
    // 【优化】默认 384x384 + 8 步 DPM-Solver（速度优先，单图约 1.5 分钟）
    val width: Int = 384,
    val height: Int = 384,
    val steps: Int = 8,  // DPM-Solver 8 步质量 ≈ Euler 20 步
    val cfgScale: Float = 7.5f,  // 标准 SD 1.5 CFG（已移除"每步 rescale"，CFG 恢复标准值）
    val seed: Long = -1L,
    val sampler: String = "euler_a",
    val denoisingStrength: Float = 0.7f,
    val backendType: String = "auto",
    val useGpu: Boolean = true,
    val threads: Int = 4,

    // 手机 SoC 自动检测（启动时一次性）
    val socInfo: SocDetector.SocInfo? = null,

    // 图生图：基础图
    val baseImageB64: String? = null,
    val baseImageBitmap: android.graphics.Bitmap? = null,
    val mode: GenerationMode = GenerationMode.TXT2IMG,

    // 模型
    val availableModels: List<String> = emptyList(),
    val activeModel: String = "",

    // 生成过程
    val isGenerating: Boolean = false,
    val isCancelling: Boolean = false,
    val isModelLoading: Boolean = false,
    val modelLoadPhase: String = "",
    val modelLoadProgress: Float = 0f,
    val modelLoadCurrent: Int = 0,
    val modelLoadTotal: Int = 0,

    // 生成进度
    val currentStep: Int = 0,
    val totalSteps: Int = 0,
    val generationSpeed: String = "",
    val isDecoding: Boolean = false,
    val isCpuFallback: Boolean = false,
    val elapsedTime: Int = 0,
    val estimatedLeftTime: Int = 0,
    val progressPercent: Int = 0,

    // 结果
    val outputImage: android.graphics.Bitmap? = null,
    val outputImageB64: String? = null,
    val outputSeed: Long = -1L,
    val genDuration: Float = 0f,
    val error: String? = null,

    // 图库
    val galleryImages: List<HistoryItem> = emptyList(),

    // 图库筛选 / 批量选择
    val galleryFilter: GalleryFilter = GalleryFilter.ALL,
    val gallerySelectionMode: Boolean = false,
    val gallerySelectedIds: Set<String> = emptySet(),

    // 设置
    val autoSaveImages: Boolean = true,
    val saveHistory: Boolean = true,
    val buildTime: String = "2026-06-10",
    // 兼容老 ConstraintsScreen
    val useTaesd: Boolean = true,
    val useFlashAttn: Boolean = true,
    val vaeTiling: Boolean = true,
    val vaeOnCpu: Boolean = false,
    val batchCount: Int = 1,
    val showAdvanced: Boolean = false,

    // 遥测（电脑端实时硬件数据）
    val telemetryCpuUsage: Double = 0.0,
    val telemetryRamUsedGb: Double = 0.0,
    val telemetryRamTotalGb: Double = 0.0,
    val telemetryGpuName: String = "",
    val telemetryVramUsedGb: Double = 0.0,
    val telemetryVramTotalGb: Double = 0.0,
    val telemetryHasGpu: Boolean = false,
    val telemetryGpuUsage: Double = 0.0,  // PC GPU utilization %

    // 本地 MNN 推理
    val localMnnModels: List<LocalModelInfo> = emptyList(),
    val selectedMnnModel: String = "",
    val isMnnLoaded: Boolean = false,
    val isMnnGenerating: Boolean = false,

    // 本地手机硬件监控
    val localCpuUsage: Float = 0f,          // 手机 CPU 使用率 0~100
    val localRamUsedGb: Float = 0f,         // 手机已用内存 GB
    val localRamTotalGb: Float = 0f,        // 手机总内存 GB
    val localGpuUsage: Float = -1f,         // 手机 GPU 使用率 0~100（-1=读不到；MIUI 14 永远 -1）
    val localGpuFreqMhz: Int = -1,          // 手机 GPU 频率 MHz（-1=读不到）
    val localCpuTemp: Float = -1f,          // 手机 CPU 温度 ℃（-1=读不到）
    val localNativeHeapMb: Int = 0,         // App native heap 大小（MB），SD 推理时显著增长

    // 频道（生成参数模板）
    val channels: List<com.sdaiapp.ui.screens.ChannelConfig> = emptyList(),
    val activeChannelId: String = "",       // 当前激活的频道 id（空=未选任何模板）

    // 语言
    val language: AppLang = AppLang.ZH
) {
    // 老字段兼容（ModelsScreen 用）
    val selectedModel: String get() = activeModel
}

// 生成模式
enum class GenerationMode { TXT2IMG, IMG2IMG }

// 图库筛选类型
enum class GalleryFilter { ALL, FAVORITES, LAST_7_DAYS, HIGH_RES }

class MainViewModel(application: Application) : AndroidViewModel(application) {

    // 主状态
    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    // 兼容老代码的旧状态流（让现有 screens 不报错）
    val generateParams: StateFlow<GenerateParams> = MutableStateFlow(GenerateParams()).asStateFlow()
    val generationState: StateFlow<GenerationState> = MutableStateFlow(GenerationState()).asStateFlow()
    val serverStatus: StateFlow<ServerStatus> = MutableStateFlow(ServerStatus()).asStateFlow()
    val settings: StateFlow<AppSettings> = MutableStateFlow(AppSettings()).asStateFlow()
    val historyItems: StateFlow<List<HistoryItem>> = MutableStateFlow(emptyList<HistoryItem>()).asStateFlow()
    val selectedAspectRatio: StateFlow<AspectRatio> = MutableStateFlow(AspectRatio.presets[0]).asStateFlow()

    // 老 ModelsScreen 用的：实时模型列表（用 uiState 里的字符串列表转 ModelInfo）
    val availableModels: StateFlow<List<ModelInfo>> = _uiState
        .map { state: AppUiState ->
            state.availableModels.map { name: String ->
                ModelInfo(
                    name = name,
                    path = "",
                    type = "sd",
                    isSelected = name == state.activeModel
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList<ModelInfo>())

    // 进度轮询任务
    private var progressJob: Job? = null

    // 遥测轮询任务
    private var telemetryJob: Job? = null

    // 历史记录仓库（关 APP 重开不丢）
    private val historyRepo = HistoryRepository(application)

    // LocalDream 本地推理服务（libstable_diffusion_core.so 子进程 + HTTP）
    private val localDream = LocalDreamService(application)

    init {
        // 启动时加载本地历史记录
        viewModelScope.launch(Dispatchers.IO) {
            historyRepo.historyFlow.collect { items ->
                _uiState.update { it.copy(galleryImages = items) }
            }
        }
        // 扫描本地 MNN 模型
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            val models = scanLocalMnnModels(ctx)
            _uiState.update { it.copy(localMnnModels = models) }
        }
        // 启动本地硬件监控轮询
        startLocalHardwarePolling()
        // 启动时自动检查连接
        testConnection(_uiState.value.pcBackendUrl)
        // 启动时检测 SoC（高通/联发科/三星/紫光展锐/海思）
        val soc = SocDetector.detect()
        Log.i("MainVM", "SoC detected: ${soc.vendor} ${soc.model} → 推荐后端: ${soc.recommendedBackend} (NPU=${soc.supportsNpu})")
        _uiState.update { it.copy(socInfo = soc) }
        // 初始化默认频道（生成参数模板）
        initDefaultChannels()
    }

    // ==================== 连接管理 ====================

    // 测试连接
    fun testConnection(url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isCheckingConnection = true, connectionError = null) }
            try {
                ApiClient.init(url)
                val healthy = ApiClient.isHealthy()
                if (healthy) {
                    val status = ApiClient.getBackendStatus()
                    _uiState.update {
                        it.copy(
                            isConnected = true,
                            pcBackendUrl = ApiClient.getBaseUrl(),
                            isCheckingConnection = false,
                            connectionError = null,
                            backendReady = status.ready,
                            backendRunning = status.running,
                            activeModel = status.settings?.model ?: it.activeModel
                        )
                    }
                    // 连接成功后自动加载模型列表 + 启动遥测轮询
                    loadAvailableModels()
                    startTelemetryPolling()
                } else {
                    _uiState.update {
                        it.copy(
                            isConnected = false,
                            isCheckingConnection = false,
                            connectionError = "无法连接到后端，请确认电脑端已启动 sd-vulkan/sd-cuda"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isConnected = false,
                        isCheckingConnection = false,
                        connectionError = "连接失败：${e.message}"
                    )
                }
            }
        }
    }

    // 简化版：带回调的测试连接（给设置页用）
    fun testConnectionWithCallback(url: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isCheckingConnection = true, connectionError = null) }
            try {
                ApiClient.init(url)
                val healthy = ApiClient.isHealthy()
                if (healthy) {
                    val status = ApiClient.getBackendStatus()
                    _uiState.update {
                        it.copy(
                            isConnected = true,
                            pcBackendUrl = ApiClient.getBaseUrl(),
                            isCheckingConnection = false,
                            connectionError = null,
                            backendReady = status.ready,
                            backendRunning = status.running
                        )
                    }
                    loadAvailableModels()
                    startTelemetryPolling()
                    onResult(true, "连接成功！")
                } else {
                    _uiState.update {
                        it.copy(
                            isConnected = false,
                            isCheckingConnection = false,
                            connectionError = "无法连接到后端"
                        )
                    }
                    onResult(false, "无法连接到后端，请确认电脑端程序已启动")
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isConnected = false,
                        isCheckingConnection = false,
                        connectionError = "连接失败：${e.message}"
                    )
                }
                onResult(false, "连接失败：${e.message}")
            }
        }
    }

    fun disconnect() {
        stopTelemetryPolling()
        _uiState.update {
            it.copy(
                isConnected = false,
                backendReady = false,
                backendRunning = false,
                telemetryCpuUsage = 0.0,
                telemetryRamUsedGb = 0.0,
                telemetryRamTotalGb = 0.0,
                telemetryGpuName = "",
                telemetryVramUsedGb = 0.0,
                telemetryVramTotalGb = 0.0,
                telemetryHasGpu = false,
                telemetryGpuUsage = 0.0
            )
        }
    }

    fun updateServerUrl(url: String) {
        _uiState.update { it.copy(pcBackendUrl = url) }
    }

    // ==================== 提示词 ====================

    fun updatePrompt(prompt: String) {
        _uiState.update { it.copy(prompt = prompt) }
    }

    fun updateNegativePrompt(negativePrompt: String) {
        _uiState.update { it.copy(negativePrompt = negativePrompt) }
    }

    // ==================== 参数 ====================

    fun updateWidth(width: Int) {
        _uiState.update { it.copy(width = width, height = it.height) }
    }

    fun updateHeight(height: Int) {
        _uiState.update { it.copy(height = height) }
    }

    fun updateSteps(steps: Int) {
        _uiState.update { it.copy(steps = steps) }
    }

    fun updateCfgScale(cfgScale: Float) {
        _uiState.update { it.copy(cfgScale = cfgScale) }
    }

    fun updateSeed(seed: Long) {
        _uiState.update { it.copy(seed = seed) }
    }

    fun updateSampler(sampler: String) {
        _uiState.update { it.copy(sampler = sampler) }
    }

    fun updateDenoisingStrength(value: Float) {
        _uiState.update { it.copy(denoisingStrength = value) }
    }

    fun updateBackendType(type: String) {
        _uiState.update { it.copy(backendType = type) }
        syncBackendSettings()
    }

    fun updateUseGpu(use: Boolean) {
        _uiState.update { it.copy(useGpu = use) }
        syncBackendSettings()
    }

    fun updateThreads(threads: Int) {
        _uiState.update { it.copy(threads = threads) }
        syncBackendSettings()
    }

    fun updateAspectRatio(ratio: AspectRatio) {
        _uiState.update { it.copy(width = ratio.width, height = ratio.height) }
    }

    // ==================== 图生图：基础图 ====================

    fun setBaseImage(b64: String?, bitmap: android.graphics.Bitmap?) {
        _uiState.update {
            it.copy(
                baseImageB64 = b64,
                baseImageBitmap = bitmap,
                mode = if (b64 != null) GenerationMode.IMG2IMG else GenerationMode.TXT2IMG
            )
        }
    }

    fun clearBaseImage() {
        _uiState.update {
            it.copy(
                baseImageB64 = null,
                baseImageBitmap = null,
                mode = GenerationMode.TXT2IMG
            )
        }
    }

    fun updateMode(mode: GenerationMode) {
        _uiState.update {
            it.copy(
                mode = mode,
                baseImageB64 = if (mode == GenerationMode.TXT2IMG) null else it.baseImageB64,
                baseImageBitmap = if (mode == GenerationMode.TXT2IMG) null else it.baseImageBitmap
            )
        }
    }

    /** 从图片路径设置基础图（图生图模式），用于画廊"图生图"按钮 */
    fun setBaseImageFromPath(imagePath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bitmap = BitmapFactory.decodeFile(imagePath)
                if (bitmap != null) {
                    val baos = java.io.ByteArrayOutputStream()
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 95, baos)
                    val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                    _uiState.update {
                        it.copy(
                            baseImageB64 = b64,
                            baseImageBitmap = bitmap,
                            mode = GenerationMode.IMG2IMG
                        )
                    }
                } else {
                    // 可能是 base64 数据而非文件路径
                    _uiState.update {
                        it.copy(
                            baseImageB64 = imagePath,
                            baseImageBitmap = null,
                            mode = GenerationMode.IMG2IMG
                        )
                    }
                }
            } catch (e: Exception) {
                // 如果文件读取失败，尝试当作 base64 使用
                _uiState.update {
                    it.copy(
                        baseImageB64 = imagePath,
                        baseImageBitmap = null,
                        mode = GenerationMode.IMG2IMG
                    )
                }
            }
        }
    }

    // ==================== 模型 ====================

    fun loadAvailableModels() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val models = ApiClient.listModels()
                _uiState.update {
                    it.copy(availableModels = models.map { m -> m.filename })
                }
            } catch (e: Exception) {
                // 拿不到也不报错，保持空列表
            }
        }
    }

    fun selectModel(filename: String) {
        val previous = _uiState.value.activeModel
        _uiState.update { it.copy(activeModel = filename) }
        // 根据模型名自动调整参数
        autoApplyModelParams(filename)

        // PC 远程模式：切换模型时通知 PC 后端重启并加载新模型
        // （否则 PC 端 sd-vulkan.exe 还跑着上一个模型，生成结果不对）
        if (filename != previous && !_uiState.value.useLocalInference && _uiState.value.isConnected) {
            restartBackendWithModel()
        }
    }

    /**
     * PC 模式下把启动参数（useGpu/backendType/threads/vaeTiling/vaeOnCpu）同步到 PC 后端
     * 这些参数在 sd-vulkan 启动后无法热改，必须重启进程才生效
     * 仅在 PC 模式 + 已连接 + 已加载模型 时调用
     */
    private fun syncBackendSettings() {
        val state = _uiState.value
        if (state.useLocalInference) return
        if (!state.isConnected) return
        if (state.activeModel.isBlank()) return
        if (state.isModelLoading) return  // 已经在加载中，跳过避免叠加
        // 1.5 秒去抖：用户连续拖动 slider 时只触发一次
        viewModelScope.launch(Dispatchers.Main) {
            kotlinx.coroutines.delay(1500)
            if (_uiState.value.isModelLoading) return@launch
            restartBackendWithModel()
        }
    }

    /** 根据模型名自动调整最佳参数 */
    private fun autoApplyModelParams(modelName: String) {
        val lower = modelName.lowercase()
        when {
            // lightning 系列：推荐 4 步，CFG 1.0
            lower.contains("lightning") || lower.contains("hyper") -> {
                _uiState.update {
                    it.copy(steps = 4, cfgScale = 1.0f, sampler = "euler_a")
                }
            }
            // turbo 系列：推荐 8 步，CFG 1.0
            lower.contains("turbo") || lower.contains("tcd") -> {
                _uiState.update {
                    it.copy(steps = 8, cfgScale = 1.0f)
                }
            }
            // LCM 系列：推荐 4 步
            lower.contains("lcm") -> {
                _uiState.update {
                    it.copy(steps = 4, cfgScale = 1.0f)
                }
            }
            // SDXL 系列：推荐 20 步，CFG 7.0
            lower.contains("xl") || lower.contains("sdxl") -> {
                _uiState.update {
                    it.copy(steps = 20, cfgScale = 7.0f)
                }
            }
            // MNN 量化模型（int8 等）：手机上推荐少步
            lower.contains("int8") || lower.contains("quant") -> {
                _uiState.update {
                    it.copy(steps = 4, cfgScale = 1.0f, sampler = "euler_a")
                }
            }
        }
    }

    // 启动/重启后端（带指定模型）
    fun restartBackendWithModel() {
        val state = _uiState.value
        if (state.activeModel.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isModelLoading = true, modelLoadProgress = 0f, modelLoadPhase = "正在启动后端...") }
            try {
                ApiClient.restartBackend(
                    StartServerRequest(
                        model = state.activeModel,
                        steps = state.steps,
                        cfgScale = state.cfgScale,
                        sampler = state.sampler,
                        threads = state.threads,
                        useGpu = state.useGpu,
                        backendType = state.backendType,
                        vaeTiling = true,
                        vaeOnCpu = false
                    )
                )
                // 轮询等待后端就绪
                pollBackendReady()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isModelLoading = false,
                        error = "启动后端失败：${e.message}"
                    )
                }
            }
        }
    }

    // 轮询后端是否加载完成
    private suspend fun pollBackendReady() {
        val startTime = System.currentTimeMillis()
        val timeout = 180_000L // 最多等 3 分钟
        while (viewModelScope.isActive && System.currentTimeMillis() - startTime < timeout) {
            val status = ApiClient.getBackendStatus()
            if (status.error != null) {
                _uiState.update {
                    it.copy(
                        isModelLoading = false,
                        backendReady = false,
                        error = "后端错误：${status.error}"
                    )
                }
                return
            }
            if (status.ready) {
                _uiState.update {
                    it.copy(
                        isModelLoading = false,
                        backendReady = true,
                        backendRunning = true,
                        modelLoadProgress = 1f
                    )
                }
                return
            }
            // 更新加载进度
            val loading = status.loading
            _uiState.update {
                it.copy(
                    modelLoadProgress = loading?.progress ?: it.modelLoadProgress,
                    modelLoadPhase = loading?.phase ?: "加载模型中...",
                    modelLoadCurrent = loading?.current ?: 0,
                    modelLoadTotal = loading?.total ?: 0
                )
            }
            delay(1000)
        }
        // 超时
        _uiState.update {
            it.copy(
                isModelLoading = false,
                error = "模型加载超时"
            )
        }
    }

    fun stopBackend() {
        viewModelScope.launch(Dispatchers.IO) {
            ApiClient.stopBackend()
            _uiState.update {
                it.copy(backendReady = false, backendRunning = false)
            }
        }
    }

    fun deleteModel(filename: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = ApiClient.deleteModel(filename)
            if (ok) loadAvailableModels()
        }
    }

    // 下载模型
    fun downloadModel(url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                ApiClient.downloadModel(url)
                pollDownloadProgress()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "下载失败：${e.message}") }
            }
        }
    }

    fun cancelDownload() {
        viewModelScope.launch(Dispatchers.IO) {
            ApiClient.cancelDownload()
        }
    }

    private suspend fun pollDownloadProgress() {
        while (viewModelScope.isActive) {
            val progress = ApiClient.getDownloadProgress()
            if (!progress.active) {
                loadAvailableModels()
                return
            }
            if (progress.error != null) {
                _uiState.update { it.copy(error = "下载错误：${progress.error}") }
                return
            }
            delay(1000)
        }
    }

    // ==================== 核心：图像生成 ====================

    // 文生图 / 图生图 统一入口
    fun generateImage() {
        val state = _uiState.value
        if (state.isGenerating) return
        if (state.prompt.isBlank()) {
            _uiState.update { it.copy(error = "请先输入提示词") }
            return
        }
        if (state.activeModel.isEmpty()) {
            _uiState.update { it.copy(error = "请先选择模型") }
            return
        }
        if (state.useLocalInference && state.isMnnLoaded) {
            // ── 路径B：LocalDream 本地推理（libstable_diffusion_core.so 子进程） ──
            generateWithMNN()
            return
        }
        if (!state.isConnected) {
            _uiState.update { it.copy(error = "未连接到后端，也未加载本地模型。请连接电脑端或加载 MNN 模型。") }
            return
        }

        // PC 模式：如果后端还没加载模型（activeModel 为空 或 backendReady=false），
        // 自动调 restartBackendWithModel() 加载，避免 sd-vulkan 拒绝服务
        if (state.activeModel.isBlank() || !state.backendReady) {
            if (state.activeModel.isBlank()) {
                // 从已加载的模型列表选第一个
                val firstModel = state.availableModels.firstOrNull()
                if (firstModel != null) {
                    _uiState.update { it.copy(activeModel = firstModel) }
                } else {
                    _uiState.update { it.copy(error = "请先在模型页面选择要用的模型") }
                    return
                }
            }
            restartBackendWithModel()
            _uiState.update { it.copy(error = "正在加载模型，请稍后再点生成（10-30秒）") }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val startMs = System.currentTimeMillis()
            _uiState.update {
                it.copy(
                    isGenerating = true,
                    error = null,
                    outputImage = null,
                    outputImageB64 = null,
                    outputSeed = -1,
                    currentStep = 0,
                    totalSteps = state.steps,
                    progressPercent = 0,
                    generationSpeed = "",
                    isDecoding = false,
                    isCpuFallback = false,
                    elapsedTime = 0,
                    estimatedLeftTime = 0
                )
            }

            // 启动进度轮询
            startProgressPolling()

            try {
                val finalSeed = if (state.seed == -1L) System.currentTimeMillis() else state.seed

                val response = if (state.mode == GenerationMode.IMG2IMG && state.baseImageB64 != null) {
                    // 图生图
                    val req = Img2ImgRequest(
                        initImages = listOf(state.baseImageB64),
                        prompt = state.prompt,
                        negativePrompt = state.negativePrompt,
                        denoisingStrength = state.denoisingStrength,
                        steps = state.steps,
                        cfgScale = state.cfgScale,
                        seed = finalSeed,
                        width = state.width,
                        height = state.height,
                        samplerName = state.sampler
                    )
                    ApiClient.img2Img(req)
                } else {
                    // 文生图
                    val req = Txt2ImgRequest(
                        prompt = state.prompt,
                        negativePrompt = state.negativePrompt,
                        n = 1,
                        size = "${state.width}x${state.height}",
                        responseFormat = "b64_json",
                        steps = state.steps,
                        cfgScale = state.cfgScale,
                        seed = finalSeed,
                        samplerName = state.sampler
                    )
                    ApiClient.txt2Img(req)
                }

                val data = response.data.firstOrNull()
                if (data == null || data.b64Json.isEmpty()) {
                    throw Exception("服务器没有返回图片数据")
                }

                // 解码 base64 为图片
                val bytes = Base64.decode(data.b64Json, Base64.DEFAULT)
                val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bitmap == null) {
                    throw Exception("图片解码失败")
                }

                val duration = (System.currentTimeMillis() - startMs) / 1000f

                // 【关键】先把 base64 写成 PNG 文件，再传文件路径到图库
                //   之前错误地把 base64 字符串当 filePath 存，AsyncImage(File(b64)) 找不到
                val pcFile = saveBase64ToFile(data.b64Json)

                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        outputImage = bitmap,
                        outputImageB64 = data.b64Json,
                        outputSeed = if (data.seed > 0) data.seed else finalSeed,
                        genDuration = duration,
                        progressPercent = 100,
                        currentStep = state.steps
                    )
                }

                // 自动保存到图库（传文件路径，不再传 base64）
                if (pcFile != null) {
                    addToGallery(state, pcFile, finalSeed, duration)
                }

                // 让服务器也存一份
                ApiClient.saveOutput(
                    imageB64 = data.b64Json,
                    metadata = mapOf(
                        "prompt" to state.prompt,
                        "negative_prompt" to state.negativePrompt,
                        "seed" to finalSeed.toString(),
                        "steps" to state.steps.toString(),
                        "cfg_scale" to state.cfgScale.toString(),
                        "width" to state.width.toString(),
                        "height" to state.height.toString(),
                        "sampler" to state.sampler
                    )
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        error = e.message ?: "生成失败"
                    )
                }
            } finally {
                stopProgressPolling()
            }
        }
    }

    // 进度轮询
    private fun startProgressPolling() {
        stopProgressPolling()
        progressJob = viewModelScope.launch(Dispatchers.IO) {
            val startMs = System.currentTimeMillis()
            while (isActive) {
                val progress = ApiClient.getGenerationProgress()
                if (progress.active) {
                    val pct = if (progress.steps > 0) {
                        (progress.step * 100 / progress.steps).coerceIn(0, 99)
                    } else 0
                    val elapsed = ((System.currentTimeMillis() - startMs) / 1000).toInt()
                    val eta = if (progress.step > 0 && progress.steps > 0) {
                        val avg = elapsed.toDouble() / progress.step
                        ((progress.steps - progress.step) * avg).toInt()
                    } else 0
                    _uiState.update {
                        it.copy(
                            currentStep = progress.step,
                            totalSteps = progress.steps,
                            progressPercent = pct,
                            generationSpeed = progress.speed,
                            isDecoding = progress.decoding,
                            elapsedTime = elapsed,
                            estimatedLeftTime = eta
                        )
                    }
                }
                delay(1000)
            }
        }
    }

    private fun stopProgressPolling() {
        progressJob?.cancel()
        progressJob = null
    }

    // 取消生成
    fun cancelGeneration() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isCancelling = true) }
            try {
                ApiClient.stopBackend()
                delay(500)
                // 用当前模型重新启动
                val state = _uiState.value
                if (state.activeModel.isNotEmpty()) {
                    ApiClient.restartBackend(
                        StartServerRequest(
                            model = state.activeModel,
                            steps = state.steps,
                            cfgScale = state.cfgScale,
                            sampler = state.sampler,
                            threads = state.threads,
                            useGpu = state.useGpu,
                            backendType = state.backendType
                        )
                    )
                }
            } catch (e: Exception) {
                // 取消失败不影响重置状态
            } finally {
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        isCancelling = false,
                        currentStep = 0,
                        progressPercent = 0,
                        generationSpeed = "",
                        isDecoding = false
                    )
                }
                stopProgressPolling()
            }
        }
    }

    // ==================== 图库 ====================

    /**
     * 保存到图库（统一存文件路径，不再存 base64）
     * @param filePath PNG 文件绝对路径（已存在）
     * @param b64Thumbnail 缩略图 base64（可选，用于列表快速预览，不存盘）
     */
    private fun addToGallery(
        state: AppUiState,
        filePath: String,
        seed: Long,
        duration: Float,
    ) {
        val item = HistoryItem(
            id = System.currentTimeMillis().toString(),
            imagePath = filePath,  // 【修复】之前误存 base64，AsyncImage 用它当 File 找不到
            prompt = state.prompt,
            negativePrompt = state.negativePrompt,
            params = GenerateParams(
                prompt = state.prompt,
                negativePrompt = state.negativePrompt,
                width = state.width,
                height = state.height,
                steps = state.steps,
                cfgScale = state.cfgScale,
                seed = seed,
                modelName = state.activeModel,
                scheduler = state.sampler
            )
        )
        // 持久化到本地（DataStore 会通过 Flow 自动同步到 UI）
        viewModelScope.launch(Dispatchers.IO) {
            historyRepo.addItem(item)
        }
    }

    /** 把 base64 写为 PNG 文件（PC 后端路径用，返回文件路径） */
    private fun saveBase64ToFile(b64: String): String? {
        return try {
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            val ctx = getApplication<Application>()
            val outDir = java.io.File(ctx.filesDir, "generated_images").apply { mkdirs() }
            val file = java.io.File(outDir, "${System.currentTimeMillis()}-${java.util.UUID.randomUUID()}.png")
            file.outputStream().use { it.write(bytes) }
            file.absolutePath
        } catch (e: Exception) {
            Log.e("Gallery", "saveBase64ToFile 失败", e)
            null
        }
    }

    fun deleteGalleryItem(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            historyRepo.deleteItem(id)
        }
    }

    fun clearGallery() {
        viewModelScope.launch(Dispatchers.IO) {
            historyRepo.clearAll()
        }
    }

    // ==================== 图库筛选 + 批量选择 ====================

    /** 切换筛选条件（全部 / 收藏 / 最近 7 天 / 高清 ≥768px）*/
    fun setGalleryFilter(filter: GalleryFilter) {
        _uiState.update { it.copy(galleryFilter = filter) }
    }

    /**
     * 筛选后的图库列表
     * - ALL: 全部
     * - LAST_7_DAYS: 7 天内
     * - HIGH_RES: 宽高都 ≥ 768
     * - FAVORITES: prompt 含 "favorite"/"收藏"（简单关键字，实际可扩展收藏功能）
     */
    fun getFilteredGallery(): List<HistoryItem> {
        val state = _uiState.value
        val all = state.galleryImages
        return when (state.galleryFilter) {
            GalleryFilter.ALL -> all
            GalleryFilter.LAST_7_DAYS -> {
                val sevenDaysAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
                all.filter { it.timestamp >= sevenDaysAgo }
            }
            GalleryFilter.HIGH_RES -> all.filter { it.params.width >= 768 && it.params.height >= 768 }
            GalleryFilter.FAVORITES -> all.filter {
                it.prompt.contains("收藏", ignoreCase = true) || it.prompt.contains("favorite", ignoreCase = true)
            }
        }
    }

    /** 进入/退出批量选择模式 */
    fun setGallerySelectionMode(enabled: Boolean) {
        _uiState.update {
            if (enabled) it.copy(gallerySelectionMode = true, gallerySelectedIds = emptySet())
            else it.copy(gallerySelectionMode = false, gallerySelectedIds = emptySet())
        }
    }

    /** 切换单个图片的选中状态（多选模式下点图片触发） */
    fun toggleGallerySelection(id: String) {
        _uiState.update {
            val newSet = if (id in it.gallerySelectedIds) it.gallerySelectedIds - id
            else it.gallerySelectedIds + id
            it.copy(gallerySelectedIds = newSet)
        }
    }

    /** 全选当前筛选结果 */
    fun selectAllGallery() {
        val visible = getFilteredGallery()
        _uiState.update { it.copy(gallerySelectedIds = visible.map { img -> img.id }.toSet()) }
    }

    /** 反选当前筛选结果 */
    fun invertGallerySelection() {
        val visible = getFilteredGallery()
        val visibleIds = visible.map { it.id }.toSet()
        _uiState.update {
            it.copy(gallerySelectedIds = visibleIds - it.gallerySelectedIds)
        }
    }

    /** 删除所有选中的图片（不删文件本身，只删历史记录） */
    fun deleteSelectedGalleryItems() {
        val ids = _uiState.value.gallerySelectedIds
        if (ids.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            ids.forEach { id -> historyRepo.deleteItem(id) }
        }
        _uiState.update { it.copy(gallerySelectionMode = false, gallerySelectedIds = emptySet()) }
    }

    fun loadFromGallery(item: HistoryItem) {
        _uiState.update {
            it.copy(
                prompt = item.prompt,
                negativePrompt = item.negativePrompt,
                width = item.params.width,
                height = item.params.height,
                steps = item.params.steps,
                cfgScale = item.params.cfgScale,
                seed = item.params.seed
            )
        }
    }

    // ==================== 设置 ====================

    fun toggleAutoSave() {
        _uiState.update { it.copy(autoSaveImages = !it.autoSaveImages) }
    }

    fun toggleSaveHistory() {
        _uiState.update { it.copy(saveHistory = !it.saveHistory) }
    }

    fun toggleLanguage() {
        val current = _uiState.value.language
        val next = if (current == AppLang.ZH) AppLang.EN else AppLang.ZH
        _uiState.update { it.copy(language = next) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    // ==================== 老方法兼容层 ====================
    // 给现有的 screens 调用，新代码用上面那些方法即可

    fun toggleInferenceMode() {
        _uiState.update { current ->
            val newLocal = !current.useLocalInference
            // 切换步数：本地=8（DPM-Solver），远程=20
            val newSteps = if (newLocal) 8 else 20
            current.copy(useLocalInference = newLocal, steps = newSteps)
        }
    }

    fun resetSettings() {
        _uiState.update { AppUiState() }
    }

    fun connectToServer(url: String) {
        testConnection(url)
    }

    fun disconnectServer() {
        disconnect()
    }

    fun setUseLocalInference(value: Boolean) {
        _uiState.update { current ->
            val newSteps = if (value) 4 else 20
            current.copy(useLocalInference = value, steps = newSteps)
        }
    }

    fun toggleLocalMNN(enabled: Boolean) {
        setUseLocalInference(enabled)
    }

    fun updateMNNModelPath(path: String) {
        // 占位
    }

    fun initMNNEngine() {
        // 占位
    }

    fun refreshModels() {
        loadAvailableModels()
    }

    fun loadAvailableModelsCompat() {
        loadAvailableModels()
    }

    fun startGeneration() {
        generateImage()
    }

    fun cancelGenerationCompat() {
        cancelGeneration()
    }

    fun updateAspectRatioCompat(ratio: AspectRatio) {
        updateAspectRatio(ratio)
    }

    fun updateScheduler(scheduler: String) {
        updateSampler(scheduler)
    }

    fun updateBatchCount(count: Int) {
        _uiState.update { it.copy(batchCount = count) }
    }

    fun updateUseTaesd(value: Boolean) {
        _uiState.update { it.copy(useTaesd = value) }
    }

    fun updateUseFlashAttn(value: Boolean) {
        _uiState.update { it.copy(useFlashAttn = value) }
    }

    fun updateVaeTiling(value: Boolean) {
        _uiState.update { it.copy(vaeTiling = value) }
    }

    fun updateVaeOnCpu(value: Boolean) {
        _uiState.update { it.copy(vaeOnCpu = value) }
    }

    fun toggleAdvanced() {
        _uiState.update { it.copy(showAdvanced = !it.showAdvanced) }
    }

    // ==================== 频道（生成参数模板）====================

    /**
     * 用一组默认模板初始化频道列表（仅在列表为空时调用）
     * 默认模板不可删除（isDefault=true）
     */
    fun initDefaultChannels() {
        if (_uiState.value.channels.isNotEmpty()) return
        val defaults = listOf(
            com.sdaiapp.ui.screens.ChannelConfig(
                id = "default",
                name = "默认",
                icon = "🎨",
                description = "通用默认参数（手机优化）",
                width = 384, height = 384,
                steps = 8, cfgScale = 7.0f,
                sampler = "dpm++2m",
                isDefault = true
            ),
            com.sdaiapp.ui.screens.ChannelConfig(
                id = "hd",
                name = "高清模式",
                icon = "✨",
                description = "高质量 768×768，更多细节（速度较慢）",
                width = 768, height = 768,
                steps = 20, cfgScale = 7.0f,
                sampler = "dpm++2m"
            ),
            com.sdaiapp.ui.screens.ChannelConfig(
                id = "fast",
                name = "快速生成",
                icon = "⚡",
                description = "低步数快速出图（适合预览构图）",
                width = 384, height = 384,
                steps = 4, cfgScale = 6.0f,
                sampler = "dpm++2m"
            ),
            com.sdaiapp.ui.screens.ChannelConfig(
                id = "portrait",
                name = "竖版人像",
                icon = "👤",
                description = "竖版人像优化 512×768",
                width = 512, height = 768,
                steps = 12, cfgScale = 7.5f,
                sampler = "dpm++2m"
            )
        )
        _uiState.update { it.copy(channels = defaults, activeChannelId = "default") }
        // 默认模板自动应用
        defaults.firstOrNull { it.id == "default" }?.let { applyChannel(it) }
    }

    /**
     * 保存（新建或更新）一个频道配置到 ViewModel
     * @param channel 编辑后的 channel 完整对象（id 已确定）
     */
    fun saveChannel(channel: com.sdaiapp.ui.screens.ChannelConfig) {
        _uiState.update {
            val list = it.channels.toMutableList()
            val idx = list.indexOfFirst { c -> c.id == channel.id }
            if (idx >= 0) {
                list[idx] = channel
            } else {
                list.add(channel)
            }
            it.copy(channels = list)
        }
    }

    /**
     * 删除一个频道（默认模板不可删）
     */
    fun deleteChannel(id: String) {
        _uiState.update {
            val ch = it.channels.find { c -> c.id == id }
            if (ch?.isDefault == true) {
                it  // 默认模板不可删
            } else {
                it.copy(
                    channels = it.channels.filter { c -> c.id != id },
                    activeChannelId = if (it.activeChannelId == id) "" else it.activeChannelId
                )
            }
        }
    }

    /**
     * 激活一个频道（同时把频道里的所有参数应用到当前生成状态）
     * 这是修复"默认模板与实际参数没关联、无法激活别的模板"的关键
     */
    fun activateChannel(id: String) {
        val ch = _uiState.value.channels.find { it.id == id } ?: return
        _uiState.update { it.copy(activeChannelId = id) }
        applyChannel(ch)
    }

    /**
     * 把频道的所有参数应用到底层生成状态
     * 让用户切到频道后，去生成页面看参数标签就能看到尺寸/步数/CFG 都变了
     */
    private fun applyChannel(ch: com.sdaiapp.ui.screens.ChannelConfig) {
        _uiState.update {
            it.copy(
                width = ch.width,
                height = ch.height,
                steps = ch.steps,
                cfgScale = ch.cfgScale,
                sampler = ch.sampler,
                seed = ch.seed.toLong(),
                useGpu = ch.useGpu,
                useTaesd = ch.useTaesd,
                useFlashAttn = ch.useFlashAttn,
                vaeTiling = ch.vaeTiling,
                threads = ch.threads
            )
        }
        Log.i("Channel", "应用频道 [${ch.name}]: ${ch.width}×${ch.height} ${ch.steps}步 CFG${ch.cfgScale} ${ch.sampler}")
    }

    fun loadFromHistoryCompat(item: HistoryItem) {
        loadFromGallery(item)
    }

    fun toggleFavorite(itemId: String) {
        // 占位
    }

    fun deleteHistoryItem(id: String) {
        deleteGalleryItem(id)
    }

    fun clearHistory() {
        clearGallery()
    }

    // 给老版 SettingsScreen 用的带回调测试连接
    fun testConnection(url: String, onResult: (Boolean, String) -> Unit) {
        testConnectionWithCallback(url, onResult)
    }

    // ==================== 遥测轮询 ====================

    private fun startTelemetryPolling() {
        stopTelemetryPolling()
        telemetryJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(3000) // 每3秒刷新一次
                val telemetry = ApiClient.getTelemetry()
                if (telemetry != null) {
                    _uiState.update {
                        it.copy(
                            telemetryCpuUsage = telemetry.cpuUsage,
                            telemetryRamUsedGb = telemetry.ramUsedGb,
                            telemetryRamTotalGb = telemetry.ramTotalGb,
                            telemetryGpuName = telemetry.gpuName,
                            telemetryVramUsedGb = telemetry.vramUsedGb,
                            telemetryVramTotalGb = telemetry.vramTotalGb,
                            telemetryHasGpu = telemetry.gpuName.isNotEmpty(),
                            telemetryGpuUsage = telemetry.gpuUsage
                        )
                    }
                }
            }
        }
    }

    private fun stopTelemetryPolling() {
        telemetryJob?.cancel()
        telemetryJob = null
    }

    // ==================== MNN 本地推理 ====================

    /** 重新扫描本地 .mnn 模型 */
    fun rescanMnnModels() {
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            val models = scanLocalMnnModels(ctx)
            _uiState.update { it.copy(localMnnModels = models) }
        }
    }

    /** 导入 zip 压缩包模型，自解压到模型目录 */
    fun importZipModel(zipUri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isModelLoading = true, modelLoadPhase = "正在解压模型...") }
            try {
                val ctx = getApplication<Application>()
                val modelsDir = modelsDir(ctx)
                modelsDir.mkdirs()

                // 从 URI 打开输入流
                val inputStream = ctx.contentResolver.openInputStream(zipUri)
                    ?: throw Exception("无法打开文件")

                // 获取文件名
                val cursor = ctx.contentResolver.query(zipUri, null, null, null, null)
                var zipFileName = "model.zip"
                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex >= 0) zipFileName = it.getString(nameIndex)
                    }
                }

                // 去掉 .zip 后缀作为模型目录名
                val modelDirName = zipFileName.removeSuffix(".zip").removeSuffix(".ZIP")
                val targetDir = File(modelsDir, modelDirName)
                targetDir.mkdirs()

                // 解压 zip 到目标目录
                java.util.zip.ZipInputStream(inputStream).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val entryName = entry.name
                            // 跳过 macOS 的 __MACOSX 目录
                            if (entryName.contains("__MACOSX")) {
                                entry = zis.nextEntry
                                continue
                            }
                            // 取文件名（去掉目录路径）
                            val fileName = File(entryName).name
                            val outFile = File(targetDir, fileName)
                            outFile.outputStream().use { fos ->
                                val buf = ByteArray(8192)
                                var len: Int
                                while (zis.read(buf).also { len = it } > 0) {
                                    fos.write(buf, 0, len)
                                }
                            }
                        }
                        entry = zis.nextEntry
                    }
                }

                // 解压完成，重新扫描
                val models = scanLocalMnnModels(ctx)
                _uiState.update {
                    it.copy(
                        localMnnModels = models,
                        isModelLoading = false,
                        modelLoadPhase = "",
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isModelLoading = false,
                        modelLoadPhase = "",
                        error = "导入模型失败：${e.message}"
                    )
                }
            }
        }
    }

    /**
     * 导入用户选中的 SD 1.5 .safetensors 模型，在 app 内自动转成 MNN 格式。
     * 调用 ModelConverter.convertSafetensors 走 native --convert 流程。
     */
    fun importSafetensorsModel(
        fileUri: android.net.Uri,
        modelName: String? = null,
        clipSkip: Int = 2
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            val finalName = (modelName?.takeIf { it.isNotBlank() }
                ?: "Custom_${System.currentTimeMillis() / 1000}").trim()

            _uiState.update {
                it.copy(isModelLoading = true, modelLoadPhase = "准备转换 $finalName ...")
            }

            val resultDir = ModelConverter.convertSafetensors(
                context = ctx,
                modelName = finalName,
                fileUri = fileUri,
                clipSkip = clipSkip,
                onProgress = { phase ->
                    _uiState.update { it.copy(modelLoadPhase = phase) }
                }
            )

            if (resultDir != null) {
                // 成功：重新扫描本地模型列表
                val models = scanLocalMnnModels(ctx)
                _uiState.update {
                    it.copy(
                        localMnnModels = models,
                        isModelLoading = false,
                        modelLoadPhase = "✓ 已导入 $finalName",
                        error = null
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isModelLoading = false,
                        modelLoadPhase = "✗ 转换失败（仅支持 SD 1.5 架构）"
                    )
                }
            }
        }
    }

    /** 删除本地 MNN 模型目录 */
    fun deleteMnnModel(modelName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val model = _uiState.value.localMnnModels.find { it.name == modelName }
            if (model != null) {
                val dir = File(model.dirPath)
                if (dir.exists()) {
                    dir.deleteRecursively()
                }
                // 重新扫描
                val ctx = getApplication<Application>()
                val models = scanLocalMnnModels(ctx)
                _uiState.update {
                    it.copy(
                        localMnnModels = models,
                        selectedMnnModel = if (modelName == it.selectedMnnModel) "" else it.selectedMnnModel
                    )
                }
            }
        }
    }

    /** 重命名本地 MNN 模型（重命名目录，避开已存在的名字） */
    fun renameMnnModel(oldName: String, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            val models = _uiState.value.localMnnModels
            val target = models.find { it.name == oldName }
                ?: run {
                    _uiState.update { it.copy(error = "找不到模型：$oldName") }
                    return@launch
                }
            val cleanNew = newName.replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_").trim('_')
            if (cleanNew.isEmpty()) {
                _uiState.update { it.copy(error = "新名称无效") }
                return@launch
            }
            if (cleanNew == oldName) return@launch
            // 检查重名
            if (models.any { it.name == cleanNew && it.name != oldName }) {
                _uiState.update { it.copy(error = "已存在同名模型：$cleanNew") }
                return@launch
            }
            val oldDir = File(target.dirPath)
            val newDir = File(oldDir.parentFile, cleanNew)
            if (!oldDir.exists() || !oldDir.renameTo(newDir)) {
                _uiState.update { it.copy(error = "重命名失败：可能目录被占用或权限不足") }
                return@launch
            }
            // 重新扫描 + 修正 selectedMnnModel
            val refreshed = scanLocalMnnModels(ctx)
            _uiState.update {
                it.copy(
                    localMnnModels = refreshed,
                    selectedMnnModel = if (it.selectedMnnModel == oldName) cleanNew else it.selectedMnnModel,
                    error = null,
                    modelLoadPhase = "✓ 已重命名为 $cleanNew"
                )
            }
        }
    }

    /** 选择本地 MNN 模型 */
    fun selectMnnModel(name: String) {
        _uiState.update { it.copy(selectedMnnModel = name) }
    }

    /** 加载选中的 MNN 模型（启动 libstable_diffusion_core.so 子进程） */
    fun loadMnnModel() {
        val model = _uiState.value.localMnnModels.find { it.name == _uiState.value.selectedMnnModel }
            ?: return
        if (!model.isComplete) {
            _uiState.update { it.copy(error = "模型不完整：缺少 UNet 或 VAE 子模型，请确认目录下有 unet.mnn 和 vae_decoder.mnn") }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            // 【自动检测】根据 SoC + 用户选择确定 backend
            // 优先级：用户选择 > SoC 推荐 > 默认 mnn
            val state = _uiState.value
            val soc = state.socInfo
            val userChoice = state.backendType.lowercase()
            val selectedBackend = when {
                userChoice == "mnn" || userChoice == "cpu" -> "mnn"
                userChoice == "qnn" || userChoice == "npu" -> "qnn"
                userChoice == "auto" || userChoice.isBlank() ->
                    soc?.recommendedBackend ?: "mnn"
                else -> "mnn"
            }
            val phaseMsg = if (selectedBackend == "qnn") {
                "启动 LocalDream (NPU 加速: ${soc?.vendor ?: "未知"} ${soc?.model ?: ""})..."
            } else {
                "启动 LocalDream (CPU: ${soc?.vendor ?: "未知"} ${soc?.model ?: ""})..."
            }
            _uiState.update { it.copy(isModelLoading = true, modelLoadPhase = phaseMsg) }
            val (ok, err) = localDream.loadModel(model.dirPath, backend = selectedBackend)
            _uiState.update {
                it.copy(
                    isMnnLoaded = ok,
                    isModelLoading = false,
                    modelLoadPhase = if (ok) "LocalDream 已就绪 (后端: $selectedBackend)" else "",
                    error = if (!ok) "LocalDream 启动失败：$err" else null
                )
            }
        }
    }

    /** 卸载已加载的 LocalDream 模型（杀掉子进程，释放内存） */
    fun unloadMnnModel() {
        viewModelScope.launch(Dispatchers.IO) {
            localDream.unloadModel()
        }
        _uiState.update {
            it.copy(
                isMnnLoaded = false,
                selectedMnnModel = ""
            )
        }
    }

    /** 用 LocalDream 本地生成图片（DPM 调度器 + OpenCL WIDE 调优） */
    fun generateWithMNN() {
        val state = _uiState.value
        if (!state.isMnnLoaded) {
            _uiState.update { it.copy(error = "请先加载模型") }
            return
        }
        if (state.prompt.isBlank()) {
            _uiState.update { it.copy(error = "请先输入提示词") }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val startMs = System.currentTimeMillis()
            _uiState.update {
                it.copy(
                    isMnnGenerating = true,
                    isGenerating = true,
                    error = null,
                    outputImage = null,
                    outputImageB64 = null,
                    progressPercent = 0,
                    currentStep = 0,
                    totalSteps = state.steps,
                    generationSpeed = "LocalDream"
                )
            }

            try {
                val finalSeed = if (state.seed == -1L) System.currentTimeMillis() else state.seed

                val imagePath = localDream.generateImage(
                    prompt = state.prompt,
                    negativePrompt = state.negativePrompt,
                    steps = state.steps,
                    cfgScale = state.cfgScale,
                    seed = finalSeed,
                    width = state.width,
                    height = state.height,
                    onProgress = { pct, step, total ->
                        _uiState.update {
                            it.copy(
                                progressPercent = (pct * 100).toInt(),
                                currentStep = step,
                                totalSteps = total,
                                elapsedTime = ((System.currentTimeMillis() - startMs) / 1000).toInt()
                            )
                        }
                    },
                )

                if (imagePath == null) {
                    throw Exception("LocalDream 推理返回空结果")
                }

                // 读取生成的图片文件
                val bitmap = BitmapFactory.decodeFile(imagePath)
                if (bitmap == null) {
                    throw Exception("图片解码失败")
                }

                // 转 base64
                val baos = java.io.ByteArrayOutputStream()
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 95, baos)
                val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)

                val duration = (System.currentTimeMillis() - startMs) / 1000f

                _uiState.update {
                    it.copy(
                        isMnnGenerating = false,
                        isGenerating = false,
                        outputImage = bitmap,
                        outputImageB64 = b64,
                        outputSeed = finalSeed,
                        genDuration = duration,
                        progressPercent = 100,
                        currentStep = state.steps
                    )
                }

                // 自动保存到图库（LocalDream：直接传回的文件路径）
                addToGallery(state, imagePath, finalSeed, duration)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isMnnGenerating = false,
                        isGenerating = false,
                        error = "LocalDream 推理失败：${e.message}"
                    )
                }
            }
        }
    }

    /** 取消 LocalDream 生成 */
    fun cancelMnnGeneration() {
        localDream.cancelGeneration()
        _uiState.update {
            it.copy(
                isMnnGenerating = false,
                isGenerating = false,
                progressPercent = 0,
                currentStep = 0
            )
        }
    }

    // ==================== 本地硬件监控 ====================

    private var localHwJob: Job? = null

    /** 启动本地硬件监控轮询（每 2 秒读取一次） */
    private fun startLocalHardwarePolling() {
        stopLocalHardwarePolling()
        localHwJob = viewModelScope.launch(Dispatchers.IO) {
            var tick = 0
            while (isActive) {
                try {
                    val ctx = getApplication<Application>()
                    val snap = com.sdaiapp.utils.LocalHardwareMonitor.snapshot(ctx)
                    _uiState.update {
                        it.copy(
                            localCpuUsage = snap.cpuUsage,
                            localRamUsedGb = snap.ramUsedGb,
                            localRamTotalGb = snap.ramTotalGb,
                            localGpuUsage = snap.gpuUsage,
                            localGpuFreqMhz = snap.gpuFreqMhz,
                            localCpuTemp = snap.cpuTemp,
                            localNativeHeapMb = (android.os.Debug.getNativeHeapAllocatedSize() / 1024L / 1024L).toInt()
                        )
                    }
                    // 前 3 次 tick 输出详细日志，方便排查"显示 —"问题
                    if (tick < 3) {
                        Log.i("HwMonitor", "tick=$tick CPU=${snap.cpuUsage}% RAM=${snap.ramUsedGb}/${snap.ramTotalGb}G GPU=${snap.gpuUsage}% freq=${snap.gpuFreqMhz}MHz T=${snap.cpuTemp}°C")
                    }
                    tick++
                } catch (_: Exception) {}
                delay(2000)
            }
        }
    }

    private fun stopLocalHardwarePolling() {
        localHwJob?.cancel()
        localHwJob = null
    }

    override fun onCleared() {
        super.onCleared()
        stopProgressPolling()
        stopTelemetryPolling()
        stopLocalHardwarePolling()
        localDream.release()
    }
}
