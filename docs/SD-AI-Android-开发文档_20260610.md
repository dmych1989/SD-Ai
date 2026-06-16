# SD-AI Android 应用开发文档

> 基于 WebUI (React/Vite) 功能规格，Kotlin + Jetpack Compose 原生实现  
> 最后更新：2026-06-10

---

## 目录

1. [项目概述](#1-项目概述)
2. [技术架构](#2-技术架构)
3. [后端 API 接口规范](#3-后端-api-接口规范)
4. [UI 功能规格（对照 WebUI）](#4-ui-功能规格对照-webui)
5. [数据模型定义](#5-数据模型定义)
6. [状态管理](#6-状态管理)
7. [网络层实现](#7-网络层实现)
8. [本地推理（MNN）](#8-本地推理mnn)
9. [各页面详细设计](#9-各页面详细设计)
10. [主题与设计系统](#10-主题与设计系统)
11. [持久化方案](#11-持久化方案)
12. [构建与部署](#12-构建与部署)
13. [功能对照检查表](#13-功能对照检查表)
14. [开发路线图](#14-开发路线图)

---

## 1. 项目概述

### 1.1 基本信息

| 项目 | 值 |
|------|-----|
| 应用名称 | SD-AI |
| Android 包名 | com.sdaiapp |
| 开发语言 | Kotlin |
| UI 框架 | Jetpack Compose + Material 3 |
| 最低 SDK | 26 (Android 8.0) |
| 目标 SDK | 36 (Android 14+) |
| 构建工具 | Gradle 8.5 + AGP 8.3.0 |
| Kotlin 版本 | 1.9.22 |
| Compose BOM | 2024.06.00 |
| 目标设备 | 红米 K50 (天玑 8100, Mali-G610, Android 14) |

### 1.2 核心功能

1. **AI 图像生成** — 文生图 (txt2img) + 图生图 (img2img)
2. **模型管理** — 激活/卸载/删除/下载模型
3. **参数控制** — 分辨率、步数、CFG、采样器、种子等
4. **频道系统** — 预设参数组合，一键切换
5. **图库管理** — 生成历史、保存、复制、删除
6. **双模式运行** — PC 后端远程 API + 本地 MNN 推理

### 1.3 项目目录结构

```
android-app/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── jniLibs/
│       │   └── arm64-v8a/           # MNN 预编译 .so (9个)
│       └── java/com/sdaiapp/
│           ├── SDApp.kt              # Application 类
│           ├── data/
│           │   ├── GenerationRequest.kt
│           │   ├── ModelInfo.kt
│           │   └── model/
│           │       └── Models.kt     # 数据模型
│           ├── inference/
│           │   └── MnnInferenceEngine.kt  # MNN 推理引擎
│           ├── network/
│           │   ├── ApiClient.kt      # HTTP API 客户端
│           │   ├── MNNEngine.kt      # MNN 引擎封装
│           │   └── PcBackendClient.kt # PC 后端客户端
│           ├── service/
│           │   └── LogcatService.kt
│           ├── ui/
│           │   ├── MainActivity.kt   # 入口 Activity
│           │   ├── Navigation.kt     # 导航 + 侧边栏
│           │   ├── screens/
│           │   │   ├── GenerateScreen.kt
│           │   │   ├── ModelsScreen.kt
│           │   │   ├── ChannelsScreen.kt
│           │   │   ├── ConstraintsScreen.kt
│           │   │   ├── GalleryScreen.kt
│           │   │   └── SettingsScreen.kt
│           │   ├── theme/
│           │   │   ├── Color.kt
│           │   │   ├── Theme.kt
│           │   │   └── Type.kt
│           │   └── viewmodel/
│           │       └── MainViewModel.kt
│           └── utils/
│               └── CrashLogger.kt
├── gradle/
│   └── wrapper/
└── build.gradle.kts
```

---

## 2. 技术架构

### 2.1 整体架构图

```
┌─────────────────────────────────────────────────┐
│                   Android App                    │
│                                                  │
│  ┌──────────┐  ┌──────────┐  ┌───────────────┐ │
│  │ Compose  │  │ ViewModel│  │  Data Layer   │ │
│  │   UI     │◄─┤ StateFlow│◄─┤               │ │
│  │          │  │          │  │               │ │
│  │ Generate │  │ UIState  │  │  ┌─────────┐  │ │
│  │ Models   │  │          │  │  │ ApiClient│  │ │
│  │ Channels │  │          │  │  │ (Ktor)  │  │ │
│  │ Constrai │  │          │  │  └────┬────┘  │ │
│  │ Gallery  │  │          │  │       │       │ │
│  │ Settings │  │          │  │  ┌────┴────┐  │ │
│  └──────────┘  └──────────┘  │  │  MNN    │  │ │
│                               │  │ Engine  │  │ │
│                               │  └─────────┘  │ │
│                               └───────────────┘ │
└─────────────────────────────────────────────────┘
           │                              │
           ▼                              ▼
   ┌──────────────┐              ┌──────────────┐
   │  PC Backend  │              │  MNN Local   │
   │  (sd-vulkan) │              │  Inference   │
   │              │              │  (arm64-v8a) │
   │  HTTP API    │              │  .so libs    │
   │  :8080       │              │              │
   └──────────────┘              └──────────────┘
```

### 2.2 双模式架构

```kotlin
sealed class InferenceMode {
    // 远程模式：连接 PC 后端的 sd-vulkan/sd-cuda 服务器
    data class Remote(val host: String, val port: Int = 8080) : InferenceMode()
    
    // 本地模式：使用 MNN 引擎在手机端推理
    data class Local(val modelPath: String) : InferenceMode()
}
```

**远程模式优先**：检测到 PC 后端在线时自动切换为远程模式，本地模式作为离线降级方案。

### 2.3 依赖清单

```kotlin
// build.gradle.kts 核心依赖
dependencies {
    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.0")
    
    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")
    
    // Network (Ktor)
    implementation("io.ktor:ktor-client-android:2.3.12")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.12")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.12")
    
    // Image Loading
    implementation("io.coil-kt:coil-compose:2.6.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    
    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    
    // Parcelize
    implementation("kotlinx.parcelize:parcelize-runtime:1.9.22")
}
```

---

## 3. 后端 API 接口规范

> 源自 `app/frontend/src/services/api.js`，PC 后端为 `sd-vulkan.exe` / `sd-cuda.exe`

### 3.1 基础地址

```
http://{PC_IP}:8080
```

端口可通过 `/api/backend-status` 动态获取。

### 3.2 接口列表

#### 健康与状态

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/health` | 健康检查，返回 `{ ok, build, issues[], checks[], ports }` |
| GET | `/api/backend-status` | 后端状态，返回 `{ ready, running, loading?, settings?, error? }` |
| GET | `/api/hardware-specs` | 硬件规格，返回 `{ os_name, cpu_name, cpu_cores_physical, cpu_cores_logical, ram_total_gb, gpu_name }` |
| GET | `/api/telemetry` | 实时遥测，返回 `{ cpu_usage, ram_used_gb, ram_total_gb, gpu_name, vram_used_gb, vram_total_gb }` |
| GET | `/api/backend-options` | 可用后端，返回 `{ options[], cudaAvailable, vulkanAvailable, defaultBackendType }` |
| GET | `/api/diagnostics` | 诊断信息 |

#### 图像生成

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/v1/images/generations` | **文生图 (txt2img)** |
| POST | `/sdapi/v1/img2img` | **图生图 (img2img)** |
| GET | `/api/generation-progress` | 生成进度，返回 `{ active, step, steps, speed, decoding }` |

**txt2img 请求体：**
```json
{
  "prompt": "A futuristic city in cyberpunk aesthetic",
  "negative_prompt": "blurry, low quality",
  "n": 1,
  "size": "1024x1024",
  "response_format": "b64_json",
  "steps": 4,
  "cfg_scale": 1.0,
  "seed": 42,
  "sample_method": "euler_a"
}
```

**txt2img 响应体：**
```json
{
  "data": [{
    "b64_json": "base64_encoded_png...",
    "seed": 42
  }]
}
```

**img2img 请求体：**
```json
{
  "init_images": ["base64_encoded_source_image"],
  "prompt": "transform into watercolor style",
  "negative_prompt": "blurry",
  "denoising_strength": 0.7,
  "steps": 20,
  "cfg_scale": 7.0,
  "seed": -1,
  "width": 512,
  "height": 512,
  "sampler_name": "euler_a",
  "sample_method": "euler_a",
  "batch_size": 1,
  "n_iter": 1,
  "send_images": true,
  "save_images": false
}
```

#### 模型管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/models` | 列出本地模型，返回 `{ models: [{ filename, sizeBytes, size }] }` |
| POST | `/api/restart-backend` | 启动/重启后端，请求体：`{ model, steps, cfgScale, sampler, threads, use_gpu, backend_type, vae_tiling, vae_on_cpu }` |
| POST | `/api/stop-backend` | 停止后端 |
| POST | `/api/delete-model` | 删除模型，请求体：`{ filename }` |
| POST | `/api/download-model` | 从 URL 下载模型，请求体：`{ url }` |
| POST | `/api/cancel-download` | 取消下载 |
| GET | `/api/download-progress` | 下载进度，返回 `{ active, filename, progress, speed, eta, error? }` |
| POST | `/api/import-model?filename=xxx` | 上传模型文件（octet-stream body） |

#### 输出管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/outputs` | 列出生成的输出，返回 `{ outputs: [{ url, prompt, seed, ... }] }` |
| POST | `/api/save-output` | 保存生成结果，请求体：`{ image, metadata }` |
| POST | `/api/delete-outputs` | 删除输出，请求体：`{ outputs: [...] }` |

#### 系统维护

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/cleanup-candidates` | 可清理文件列表 |
| POST | `/api/cleanup` | 执行清理 |
| GET | `/v1/models` | 服务器模型列表（兼容 OpenAI 格式） |

### 3.3 后端启动参数

sd-vulkan.exe 的关键 CLI 参数（通过 `/api/restart-backend` 传递）：

```bash
sd-vulkan.exe \
  --listen-port 8080 \
  --model models/Juggernaut-XL-Lightning.safetensors \
  --threads 8 \
  --steps 4 \
  --cfg-scale 1.0 \
  --sampling-method euler_a \
  --use-gpu \
  --backend-type auto \
  --vae-tiling \
  --vae-on-cpu
```

---

## 4. UI 功能规格（对照 WebUI）

### 4.1 WebUI 组件映射

| WebUI 组件 | Android 对应 | 导航位置 |
|------------|-------------|---------|
| `Sidebar.jsx` | `Navigation.kt` (侧边栏) | 左侧固定 |
| `TopStatusBar.jsx` | 顶栏 (内嵌 Navigation) | 主内容区顶部 |
| `Generator.jsx` | `GenerateScreen.kt` | "生成" 页 |
| `ModelManager.jsx` | `ModelsScreen.kt` | "模型" 页 |
| `ImageConstraints.jsx` | `ConstraintsScreen.kt` | "参数" 页 |
| (RN ChannelsScreen) | `ChannelsScreen.kt` | "频道" 页 |
| (Gallery 内嵌 Generator) | `GalleryScreen.kt` | "图库" 页 |
| (Settings) | `SettingsScreen.kt` | "设置" 页 |

### 4.2 侧边栏 (Sidebar)

**WebUI 功能：**
- 3 个导航项：Image Generator / Model Manager / Image Constraints
- 底部显示主机硬件规格 (CPU/GPU/RAM/OS)

**Android 实现：**
- 6 个导航项：生成 / 模型 / 频道 / 参数 / 图库 / 设置
- Logo 区域（"AI" 图标）
- 底部显示连接状态指示器（绿点 + "已连接"/"离线"）
- 72dp 宽度，竖排图标 + 文字

### 4.3 顶栏 (TopStatusBar)

**WebUI 功能：**
- 服务器运行状态指示器（运行/停止）
- 当前活跃模型名称
- CPU/RAM/VRAM 实时遥测芯片
- 停止服务器按钮
- 主题切换（深色/浅色）
- 语言切换

**Android 实现：**
- 状态指示器（绿点=运行，灰点=停止）
- 活跃模型名称
- 连接地址显示
- 停止/断开按钮

### 4.4 生成页面 (Generator)

**WebUI 完整功能列表：**

1. **提示词输入区**
   - 正向提示词 textarea（多行，placeholder）
   - 负向提示词 input（单行，默认值 "blurry, low quality, deformed hands, texts"）
   - 禁用状态（生成中不可编辑）

2. **基础图（img2img）**
   - 文件选择器（PNG/JPG/WEBP）
   - 预览缩略图 + "Remove" 按钮
   - 去噪强度滑条（0.1-1.0，步长 0.05，默认 0.7）
   - 说明文字：低值=接近原图，高值=更多创意自由

3. **活跃参数芯片**
   - 分辨率芯片（可点击跳转参数页）
   - 步数芯片
   - 采样器芯片
   - 种子芯片（-1 显示为 "Random"）

4. **生成按钮**
   - 文生图："Generate" / 无模型时 "Select a model to generate"
   - 图生图："Generate from Base Image"
   - 禁用条件：正在生成 / 无提示词 / 无活跃模型

5. **输出预览区**
   - 空状态：✨ 图标 + 提示文字
   - 生成中：进度覆盖层
   - 完成后：生成的图片

6. **进度覆盖层（3 种状态）**
   - **重启后端中**：旋转指示器 + "Syncing Settings & Initializing..." + 模型加载进度条（张数/百分比/速度/设备信息）
   - **生成中**：
     - 旋转指示器
     - "Generating Locally..." / "Decoding Latents..."
     - CPU 降级警告（⚠️ CPU Fallback Detected）
     - 实时统计：当前步/总步、已用时间、生成速度 (it/s 或 s/it)
     - 进度条（0-99%）
     - 预估剩余时间
     - "Stop Generating" 按钮
   - **取消中**：旋转指示器 + "Cancelling Generation..." + 说明文字

7. **错误状态**
   - 红色警告图标 + "Generation Failed"
   - 错误信息
   - 提示：降低分辨率到 512x512

8. **生成结果信息**
   - Seed: {seed}
   - Inference Time: {duration}s

9. **图片操作按钮**
   - Copy（复制到剪贴板）
   - Save to USB（下载保存）

10. **历史图库**
    - 标题："Recent Gallery"
    - 选择模式（Ctrl/Cmd + 点击多选）
    - 选中后显示操作：Use / Download / Delete / Clear
    - 空状态提示
    - 缩略图网格（懒加载，tooltip 显示 prompt/seed/time）

---

## 5. 数据模型定义

### 5.1 UIState（统一状态）

```kotlin
data class UIState(
    // 连接
    val isConnected: Boolean = false,
    val serverUrl: String = "http://192.168.1.100:8080",
    val connectionError: String? = null,
    
    // 生成参数
    val prompt: String = "",
    val negativePrompt: String = "blurry, low quality, deformed hands, texts",
    val isGenerating: Boolean = false,
    val generationProgress: Int = 0,
    val currentStep: Int = 0,
    val generationSpeed: String = "",
    val isDecoding: Boolean = false,
    val isCpuFallback: Boolean = false,
    val elapsedTime: Int = 0,
    val estimatedLeftTime: Int = 0,
    val outputImage: String? = null,
    val outputSeed: Int? = null,
    val genDuration: Float? = null,
    val errorMsg: String? = null,
    
    // 模型
    val activeModel: String? = null,
    val models: List<ModelInfo> = emptyList(),
    val isModelLoading: Boolean = false,
    val modelLoadProgress: ModelLoadProgress? = null,
    
    // 约束参数
    val constraints: ImageConstraints = ImageConstraints(),
    
    // 频道
    val channels: List<Channel> = defaultChannels,
    val activeChannelId: String = "default",
    
    // 图库
    val galleryImages: List<GeneratedImage> = emptyList(),
    
    // 设置
    val autoSaveImages: Boolean = true,
    val saveHistory: Boolean = true,
    val buildTime: String = "2026-06-10"
)
```

### 5.2 ImageConstraints（生成约束）

```kotlin
data class ImageConstraints(
    val width: Int = 1024,
    val height: Int = 1024,
    val steps: Int = 4,               // Flux/Lightning 推荐 4
    val cfgScale: Float = 1.0f,       // Flux 推荐 1.0
    val sampler: String = "euler_a",
    val seed: Int = -1,               // -1 = 随机
    val denoisingStrength: Float = 0.7f, // img2img 专用
    val useGpu: Boolean = true,
    val useTaesd: Boolean = true,
    val useFlashAttn: Boolean = true,
    val useTiling: Boolean = false,
    val vaeTiling: Boolean = true,
    val vaeOnCpu: Boolean = false,
    val threads: Int = 4,
    val backendType: String = "auto"   // auto/cpu/vulkan/cuda
)
```

**模型默认参数自动同步规则（源自 WebUI）：**

| 模型关键字 | 步数 | CFG | 分辨率 |
|-----------|------|-----|--------|
| flux / schnell | 4 | 1.0 | 1024×1024 |
| lightning / turbo | 4 | 1.5 | 1024×1024 |
| sd15 | 25 | 7.0 | 512×512 |
| sd35 | 20 | 4.5 | 1024×1024 |
| 其他/自定义 | 20 | 7.0 | 512×512 |

### 5.3 ModelInfo（模型信息）

```kotlin
data class ModelInfo(
    val filename: String,
    val sizeBytes: Long = 0,
    val size: String = "Unknown",
    val format: String = "",       // GGUF / Safetensors / CKPT
    val isActive: Boolean = false
)
```

### 5.4 Channel（频道）

```kotlin
data class Channel(
    val id: String,
    val name: String,
    val icon: String = "🎨",
    val isDefault: Boolean = false,
    val constraints: ImageConstraints = ImageConstraints()
)

val defaultChannels = listOf(
    Channel("default", "默认", "🎨", true, ImageConstraints()),
    Channel("hd", "高清模式", "✨", false, ImageConstraints(width = 1024, height = 1024, steps = 20, cfgScale = 7.0f)),
    Channel("fast", "快速生成", "⚡", false, ImageConstraints(width = 512, height = 512, steps = 4, cfgScale = 1.0f)),
    Channel("portrait", "人像模式", "👤", false, ImageConstraints(width = 512, height = 768, steps = 25, cfgScale = 7.0f))
)
```

### 5.5 GeneratedImage（生成结果）

```kotlin
data class GeneratedImage(
    val url: String,
    val prompt: String,
    val negativePrompt: String,
    val seed: Int,
    val steps: Int,
    val cfgScale: Float,
    val width: Int,
    val height: Int,
    val sampler: String,
    val model: String?,
    val mode: String,                 // "txt2img" / "img2img"
    val denoisingStrength: Float? = null,
    val durationSec: Float? = null,
    val timestamp: String
)
```

### 5.6 ModelLoadProgress（模型加载进度）

```kotlin
data class ModelLoadProgress(
    val progress: Float = 0f,
    val phase: String = "Loading model...",
    val speed: String = "",
    val current: Int = 0,
    val total: Int = 0,
    val model: String = "",
    val backendMode: String = "",
    val backendBinary: String = "",
    val device: String = ""
)
```

---

## 6. 状态管理

### 6.1 ViewModel 架构

```kotlin
class MainViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(UIState())
    val uiState: StateFlow<UIState> = _uiState.asStateFlow()
    
    // ---- 连接管理 ----
    fun connectToServer(url: String)
    fun disconnect()
    fun checkConnection()
    
    // ---- 提示词 ----
    fun updatePrompt(prompt: String)
    fun updateNegativePrompt(prompt: String)
    
    // ---- 约束参数 ----
    fun updateConstraints(update: (ImageConstraints) -> ImageConstraints)
    fun syncConstraintsForModel(modelName: String)  // 自动调整步数/CFG/分辨率
    
    // ---- 生成 ----
    fun generateImage()
    fun cancelGeneration()
    
    // ---- 模型 ----
    fun loadModel(filename: String)
    fun unloadModel()
    fun deleteModel(filename: String)
    fun downloadModel(url: String)
    fun cancelDownload()
    fun refreshModels()
    
    // ---- 频道 ----
    fun switchChannel(channelId: String)
    fun createChannel(channel: Channel)
    fun deleteChannel(channelId: String)
    
    // ---- 图库 ----
    fun loadGallery()
    fun deleteGalleryItems(indices: List<Int>)
    fun saveImageToGallery(image: GeneratedImage)
    
    // ---- 设置 ----
    fun updateAutoSave(enabled: Boolean)
    fun updateSaveHistory(enabled: Boolean)
}
```

### 6.2 状态更新模式

所有状态更新通过 `_uiState.update { }` 原子操作：

```kotlin
fun updatePrompt(prompt: String) {
    _uiState.update { it.copy(prompt = prompt) }
}

fun updateConstraints(update: (ImageConstraints) -> ImageConstraints) {
    _uiState.update { it.copy(constraints = update(it.constraints)) }
}
```

---

## 7. 网络层实现

### 7.1 ApiClient (Ktor)

```kotlin
class ApiClient {
    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 300_000  // 5 分钟（大模型加载）
            connectTimeoutMillis = 10_000
        }
    }
    
    private var baseUrl = "http://192.168.1.100:8080"
    
    // ---- 健康与状态 ----
    suspend fun getHealth(): HealthResponse
    suspend fun getBackendStatus(): BackendStatusResponse
    suspend fun getHardwareSpecs(): HardwareSpecsResponse
    suspend fun getTelemetry(): TelemetryResponse
    suspend fun getBackendOptions(): BackendOptionsResponse
    
    // ---- 图像生成 ----
    suspend fun generateImage(request: Txt2ImgRequest): GenerationResult
    suspend fun generateImageImg2Img(request: Img2ImgRequest): GenerationResult
    suspend fun getGenerationProgress(): GenerationProgressResponse
    
    // ---- 模型管理 ----
    suspend fun listModels(): List<ModelInfo>
    suspend fun startServer(request: StartServerRequest): String
    suspend fun stopServer()
    suspend fun deleteModel(filename: String)
    suspend fun downloadModel(url: String): DownloadResponse
    suspend fun cancelDownload()
    suspend fun getDownloadProgress(): DownloadProgressResponse
    
    // ---- 输出管理 ----
    suspend fun listOutputs(): List<GeneratedImage>
    suspend fun saveOutput(image: String, metadata: Map<String, Any?>): SaveOutputResponse
    suspend fun deleteOutputs(outputs: List<GeneratedImage>)
}
```

### 7.2 关键请求/响应类型

```kotlin
// txt2img 请求
@Serializable
data class Txt2ImgRequest(
    val prompt: String,
    val negative_prompt: String = "",
    val n: Int = 1,
    val size: String = "1024x1024",      // "{width}x{height}"
    val response_format: String = "b64_json",
    val steps: Int = 4,
    val cfg_scale: Float = 1.0f,
    val seed: Int = -1,
    val sample_method: String = "euler_a"
)

// img2img 请求
@Serializable
data class Img2ImgRequest(
    val init_images: List<String>,         // base64 encoded
    val prompt: String,
    val negative_prompt: String = "",
    val denoising_strength: Float = 0.7f,
    val steps: Int = 20,
    val cfg_scale: Float = 7.0f,
    val seed: Int = -1,
    val width: Int = 512,
    val height: Int = 512,
    val sampler_name: String = "euler_a",
    val sample_method: String = "euler_a",
    val batch_size: Int = 1,
    val n_iter: Int = 1,
    val send_images: Boolean = true,
    val save_images: Boolean = false
)

// 生成结果
data class GenerationResult(
    val image: String,       // base64 data URI 或 URL
    val seed: Int,
    val durationSec: Float
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
    val backendMode: String = "",
    val device: String = ""
)

// 启动后端请求
@Serializable
data class StartServerRequest(
    val model: String,
    val steps: Int = 20,
    val cfgScale: Float = 7.0f,
    val sampler: String = "euler_a",
    val threads: Int = 8,
    val use_gpu: Boolean = true,
    val backend_type: String = "auto",
    val vae_tiling: Boolean = true,
    val vae_on_cpu: Boolean = false
)
```

### 7.3 生成流程（完整时序）

```
用户点击 Generate
       │
       ▼
  1. 检查后端设置是否同步
     - 比对 activeModel, steps, cfgScale, sampler, threads, useGpu, backendType
     ┌──────────────┐
     │ 需要重启？   │
     └──────┬───────┘
       是 │         │ 否
         ▼         │
  2. 调用 POST /api/restart-backend
     │              │
     ▼              │
  3. 轮询 GET /api/backend-status (500ms 间隔, 最多 120s)
     - 更新 modelLoadProgress (phase/speed/current/total/device)
     - ready=true → 继续
     - error → 抛出异常
     │              │
     ▼              ▼
  4. 发起生成请求
     - txt2img: POST /v1/images/generations
     - img2img: POST /sdapi/v1/img2img
     │
     ▼
  5. 同时启动进度轮询 (1s 间隔)
     - GET /api/generation-progress
     - 更新: step, steps, speed, decoding, cpuFallback
     - 计算预估剩余时间
     │
     ▼
  6. 收到生成结果
     - 解析 b64_json → Bitmap
     - 调用 POST /api/save-output 保存到 PC
     - 更新 outputImage, outputSeed, genDuration
     - 添加到 galleryImages
```

### 7.4 取消生成流程

```
用户点击 Stop Generating
       │
       ▼
  1. AbortController.abort() 取消 HTTP 请求
  2. 调用 POST /api/stop-server (终止推理线程)
  3. 重新启动后端: POST /api/restart-backend (使用当前模型+参数)
  4. 轮询等待 ready
  5. 重置所有进度状态
```

---

## 8. 本地推理（MNN）

### 8.1 MNN 引擎集成

```kotlin
class MnnInferenceEngine {
    companion object {
        init {
            System.loadLibrary("MNN")        // 核心库
        }
    }
    
    // 已集成的 .so 文件 (arm64-v8a):
    // libMNN.so, libMNN_CL.so, libMNN_Vulkan.so,
    // libMNNGraph.so, libMNN_Express.so,
    // libMNN_JNI.so, libMNN_Vulkan_JNI.so,
    // libllama.so, libllamamodel-MNN.so
    
    fun createSession(modelPath: String): Long
    fun runInference(sessionId: Long, input: FloatArray): FloatArray
    fun destroySession(sessionId: Long)
}
```

### 8.2 本地推理流程（待实现）

```kotlin
// MNNEngine.kt - 本地推理封装
class MNNEngine(private val context: Context) {
    private var sessionId: Long = 0
    
    suspend fun loadModel(modelPath: String): Result<Unit>
    suspend fun generate(
        prompt: String,
        negativePrompt: String,
        width: Int,
        height: Int,
        steps: Int,
        cfgScale: Float,
        seed: Int,
        sampler: String
    ): Result<Bitmap>
    fun unloadModel()
}
```

> **注意**：MNN 本地推理当前为架构预留，实际 Stable Diffusion 推理需 MNN 团队提供 SD 模型转换支持。目前优先使用 PC 远程后端模式。

---

## 9. 各页面详细设计

### 9.1 GenerateScreen

**布局：** 上下分区

```
┌──────────────────────────────────┐
│  提示词区域 (上半)                │
│  ┌──────────────────────────────┐│
│  │ 正向提示词 (多行输入)         ││
│  │                              ││
│  │ 负向提示词 (单行输入)         ││
│  │                              ││
│  │ [基础图预览] [Remove]         ││
│  │ 去噪强度: ────●──── 0.70     ││
│  │                              ││
│  │ 活跃参数芯片:                 ││
│  │ [1024×1024] [Steps:4] [euler]││
│  │                              ││
│  │ [✨ Generate]                 ││
│  └──────────────────────────────┘│
│                                  │
│  输出预览 (下半)                  │
│  ┌──────────────────────────────┐│
│  │                              ││
│  │      生成图片 / 进度 / 空态   ││
│  │                              ││
│  │  Seed: 42  |  Time: 3.2s     ││
│  │  [Copy] [Save]               ││
│  └──────────────────────────────┘│
│                                  │
│  历史图库                         │
│  ┌─┐ ┌─┐ ┌─┐ ┌─┐               │
│  │ │ │ │ │ │ │ │                │
│  └─┘ └─┘ └─┘ └─┘               │
└──────────────────────────────────┘
```

**关键交互：**
- 参数芯片点击 → 导航到 ConstraintsScreen
- 基础图选择 → 打开系统图片选择器
- 生成中 → 显示进度覆盖层（步数/速度/剩余时间/取消按钮）
- 生成完成 → 自动保存到图库
- 历史项点击 → 加载其 prompt 和参数

### 9.2 ModelsScreen

**布局：** 垂直列表

```
┌──────────────────────────────────┐
│  活跃模型卡片 (高亮)              │
│  ┌──────────────────────────────┐│
│  │ 🟢 Juggernaut-XL-Lightning   ││
│  │ SDXL • 6.6 GB • Vulkan GPU   ││
│  │                    [Unload]   ││
│  └──────────────────────────────┘│
│                                  │
│  模型加载进度 (加载中时显示)       │
│  ┌──────────────────────────────┐│
│  │ Loading: dreamshaper-xl...   ││
│  │ ████████░░░░ 65%             ││
│  │ Loaded 42/64 tensors (Vulkan)││
│  └──────────────────────────────┘│
│                                  │
│  本地模型列表                     │
│  ┌──────────────────────────────┐│
│  │ model1.gguf  GGUF • 3.8 GB  ││
│  │ [Load] [Delete]              ││
│  ├──────────────────────────────┤│
│  │ model2.safetensors  6.6 GB   ││
│  │ [Load] [Delete]              ││
│  └──────────────────────────────┘│
│                                  │
│  模型库推荐                       │
│  ┌──────────────────────────────┐│
│  │ 📦 Juggernaut XL v9 Lightning││
│  │ SDXL • 6.6GB • 1024x1024    ││
│  │ 最佳通用模型，快速生成        ││
│  │ [Download]                   ││
│  └──────────────────────────────┘│
│                                  │
│  从 URL 下载                      │
│  ┌──────────────────────────────┐│
│  │ [输入 URL............] [Go]   ││
│  └──────────────────────────────┘│
│                                  │
│  导入本地文件                     │
│  ┌──────────────────────────────┐│
│  │ 📁 选择 .gguf/.safetensors   ││
│  └──────────────────────────────┘│
└──────────────────────────────────┘
```

**WebUI 模型库数据：**

```kotlin
val MODEL_LIBRARY = listOf(
    ModelLibrarySection("SDXL 最佳", listOf(
        ModelLibraryItem(
            "Juggernaut XL v9 Lightning",
            "Juggernaut-XL-Lightning.safetensors",
            "Safetensors", "6.6 GB", "1024x1024",
            "最佳通用 SDXL 模型，4步即可出图",
            "https://huggingface.co/RunDiffusion/Juggernaut-XL-Lightning/resolve/main/Juggernaut-XL-Lightning.safetensors"
        ),
        ModelLibraryItem(
            "DreamShaper XL Lightning",
            "DreamShaperXL_Lightning.safetensors",
            "Safetensors", "6.6 GB", "1024x1024",
            "高质量创意模型，细节丰富",
            "https://huggingface.co/Lykon/dreamshaper-xl-lightning/resolve/main/DreamShaperXL_Lightning.safetensors"
        ),
        ModelLibraryItem(
            "SDXL Base 1.0 Q4_0 GGUF",
            "stable-diffusion-xl-base-1.0-Q4_0.gguf",
            "GGUF", "3.8 GB", "1024x1024",
            "量化版 SDXL，体积更小",
            "https://huggingface.co/hum-ma/SDXL-models-GGUF/resolve/7e15380138e7069ca3aaef5bf0401c3406e3a593/stable-diffusion-xl-base-1.0-Q4_0.gguf"
        ),
    )),
    ModelLibrarySection("SD 1.5 超快速", listOf(
        ModelLibraryItem(
            "DreamShaper 8",
            "DreamShaper_8_pruned.safetensors",
            "Safetensors", "2.1 GB", "512x512",
            "经典 SD1.5 模型，通用性强",
            "https://huggingface.co/Lykon/DreamShaper/resolve/main/DreamShaper_8_pruned.safetensors"
        ),
        ModelLibraryItem(
            "CyberRealistic v80 Inpainting",
            "cyberrealistic_v80Inpainting.safetensors",
            "Safetensors", "2.0 GB", "512x512",
            "写实风格，支持 inpainting",
            "https://civitai.com/api/download/models/1464918"
        ),
        ModelLibraryItem(
            "ReV Animated v2",
            "rev-animated-v2.safetensors",
            "Safetensors", "2.1 GB", "512x512",
            "动漫风格，色彩鲜艳",
            "https://civitai.com/api/download/models/474453"
        ),
    ))
)
```

### 9.3 ConstraintsScreen

**布局：** 3 个参数卡片

```
┌──────────────────────────────────┐
│  1. 图片尺寸                     │
│  ┌──────────────────────────────┐│
│  │ 质量模式: [高清1024] [标准512] ││
│  │                              ││
│  │ 宽高比:                       ││
│  │ ┌──┐ ┌──┐ ┌────┐ ┌┐        ││
│  │ │1:1│ │4:3│ │16:9│ │9:16│   ││
│  │ └──┘ └──┘ └────┘ └┘        ││
│  │                              ││
│  │ 自定义: 宽 [1024] 高 [1024]   ││
│  └──────────────────────────────┘│
│                                  │
│  2. 生成质量                     │
│  ┌──────────────────────────────┐│
│  │ 步数: ────●──── 4            ││
│  │ CFG Scale: ──●────── 1.0     ││
│  │                              ││
│  │ 采样方法:                     ││
│  │ [euler_a] [euler] [heun] ... ││
│  │                              ││
│  │ 种子: [  -1  ] [随机]         ││
│  └──────────────────────────────┘│
│                                  │
│  3. 系统设置                     │
│  ┌──────────────────────────────┐│
│  │ 后端: [Auto] [CPU] [Vulkan]  ││
│  │ 线程数: ──●──── 4            ││
│  │                              ││
│  │ ☑ VAE 平铺 (降低显存)        ││
│  │ ☐ VAE 转CPU (极限省显存)     ││
│  │ ☑ TAESD 解码器               ││
│  │ ☑ Flash Attention            ││
│  └──────────────────────────────┘│
└──────────────────────────────────┘
```

**WebUI 采样方法完整列表（17种）：**

| 值 | 显示名 |
|----|--------|
| `euler_a` | Euler A |
| `euler` | Euler |
| `heun` | Heun |
| `dpm2` | DPM2 |
| `dpm++2s_a` | DPM++ 2S A |
| `dpm++2m` | DPM++ 2M |
| `dpm++2mv2` | DPM++ 2MV2 |
| `ipndm` | iPNDM |
| `ipndm_v` | iPNDM V |
| `lcm` | LCM |
| `ddim_trailing` | DDIM Trailing |
| `tcd` | TCD |
| `res_multistep` | Res Multistep |
| `res_2s` | Res 2S |
| `er_sde` | ER SDE |
| `euler_cfg_pp` | Euler CFG++ |
| `euler_a_cfg_pp` | Euler A CFG++ |

**宽高比预设（WebUI 数据）：**

| ID | 标签 | SD1.5 | SDXL |
|----|------|-------|------|
| 1:1 | 方形 | 512×512 | 1024×1024 |
| 4:3 | 照片 | 512×384 | 1152×864 |
| 16:9 | 风景 | 512×288 | 1216×684 |
| 9:16 | 竖屏 | 288×512 | 684×1216 |

### 9.4 ChannelsScreen

4 个预设频道 + 自定义创建：

| 频道 | 分辨率 | 步数 | CFG | 说明 |
|------|--------|------|-----|------|
| 默认 🎨 | 1024×1024 | 4 | 1.0 | Flux/Lightning 默认参数 |
| 高清 ✨ | 1024×1024 | 20 | 7.0 | 更多细节，生成较慢 |
| 快速 ⚡ | 512×512 | 4 | 1.0 | 快速出图，质量稍低 |
| 人像 👤 | 512×768 | 25 | 7.0 | 适合人物生成 |

### 9.5 GalleryScreen

- 网格布局展示历史图片
- 点击查看详情（全屏预览 + 元数据）
- 长按多选 → 批量操作（下载/删除）
- 空状态友好提示

### 9.6 SettingsScreen

- 服务器地址配置
- 自动保存图片开关
- 保存历史记录开关
- 推理模式选择（远程/本地）
- 缓存管理
- 关于/版本信息

---

## 10. 主题与设计系统

### 10.1 颜色系统

```kotlin
// 深色主题 (默认，匹配 WebUI)
val BackgroundDark = Color(0xFF090A0F)      // 主背景
val SurfaceDark = Color(0xFF12141D)          // 卡片/面板
val SurfaceVariantDark = Color(0xFF1A1D29)   // 二级表面

// 主色 (Indigo)
val PrimaryDark = Color(0xFF6366F1)          // 主色调
val PrimaryContainerDark = Color(0xFF1E1B4B) // 主色容器

// 辅色
val SecondaryDark = Color(0xFF38BDF8)        // 天蓝
val TertiaryDark = Color(0xFFF472B6)         // 粉色

// 语义色
val ErrorDark = Color(0xFFF87171)            // 错误红
val SuccessDark = Color(0xFF34D399)          // 成功绿
val WarningDark = Color(0xFFFBBF24)          // 警告黄

// 文字
val TextPrimary = Color(0xFFF4F4F5)          // 主文字
val TextSecondary = Color(0xFFA1A1AA)        // 次要文字
val TextTertiary = Color(0xFF71717A)         // 辅助文字

// 分割线/边框
val DividerDark = Color(0xFF2A2D3A)
val BorderDark = Color(0xFF2A2D3A)

// 扩展
val PrimaryIndigo = Color(0xFF6366F1)
val AccentPurple = Color(0xFF8B5CF6)
val SurfaceLight = Color(0xFF1E2233)
val SurfaceDarker = Color(0xFF0A0B0F)
```

### 10.2 圆角规范

| 用途 | 圆角 |
|------|------|
| 卡片 | 16dp |
| 按钮 | 12dp |
| 输入框 | 12dp |
| 芯片/标签 | 8dp |
| 对话框 | 20dp |

### 10.3 间距规范

| 用途 | 间距 |
|------|------|
| 页面边距 | 16dp |
| 卡片间距 | 12dp |
| 元素内间距 | 16dp |
| 小元素间距 | 8dp |
| 极小间距 | 4dp |

---

## 11. 持久化方案

### 11.1 DataStore（设置）

```kotlin
// 用户偏好设置
data class UserPreferences(
    val serverUrl: String = "http://192.168.1.100:8080",
    val autoSaveImages: Boolean = true,
    val saveHistory: Boolean = true,
    val inferenceMode: String = "remote",  // remote / local
    val theme: String = "dark"
)
```

### 11.2 Room 数据库（历史记录）— 待实现

```kotlin
@Entity(tableName = "generation_history")
data class GenerationHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val prompt: String,
    val negativePrompt: String,
    val seed: Int,
    val steps: Int,
    val cfgScale: Float,
    val width: Int,
    val height: Int,
    val sampler: String,
    val model: String?,
    val mode: String,
    val denoisingStrength: Float?,
    val durationSec: Float?,
    val imagePath: String,
    val createdAt: Long = System.currentTimeMillis()
)
```

### 11.3 MediaStore（图片保存到相册）— 待实现

```kotlin
// 保存到系统相册
suspend fun saveImageToMediaStore(
    context: Context,
    bitmap: Bitmap,
    filename: String,
    mimeType: String = "image/png"
): Uri
```

---

## 12. 构建与部署

### 12.1 环境要求

| 项目 | 要求 |
|------|------|
| JDK | 21+ (已定位: `D:\jdk-21.0.11+10`) |
| Android SDK | compileSdk 36, targetSdk 36 |
| Android SDK 路径 | `C:\Users\Administrator\AppData\Local\Android\Sdk` |
| Gradle | 8.5 |
| AGP | 8.3.0 |
| NDK | 不指定 (使用默认) |
| 设备 | 红米 K50, Android 14, USB 调试已启用 |

### 12.2 构建命令

```powershell
# 设置 JAVA_HOME
$env:JAVA_HOME = "D:\jdk-21.0.11+10"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

# 构建 Debug APK
cd D:\GitHub\Local-AI-Image-Generator\android-app
.\gradlew.bat assembleDebug --no-daemon

# APK 输出路径
# app\build\outputs\apk\debug\app-debug.apk
```

### 12.3 安装到设备

```powershell
# 检查设备连接
adb devices

# 安装 APK
adb install -r app\build\outputs\apk\debug\app-debug.apk

# 启动应用
adb shell am start -n com.sdaiapp/.ui.MainActivity

# 查看日志
adb logcat -s "SDAI" "Compose"
```

### 12.4 已知构建问题

| 问题 | 解决方案 |
|------|---------|
| `compileSdk=36` 与 AGP 8.3.0 不兼容警告 | 在 `gradle.properties` 添加 `android.suppressUnsupportedCompileSdk=36` |
| `gradle-wrapper.jar` 损坏 | 从其他项目复制，或 Android Studio 自动下载 |
| MNN .so 仅 arm64-v8a | 其他架构需单独编译或排除 |

### 12.5 APK 信息

| 项目 | 值 |
|------|-----|
| Debug APK 大小 | ~33 MB |
| 包含 MNN .so | 9 个 arm64-v8a 库 |
| 签名 | Debug 签名 |
| 最低安装设备 | arm64-v8a, Android 8.0+ |

---

## 13. 功能对照检查表

### 13.1 生成功能

| 功能 | WebUI | Android | 状态 |
|------|-------|---------|------|
| 文生图 (txt2img) | ✅ | ✅ UI | 代码完成，待 API 联调 |
| 图生图 (img2img) | ✅ | ✅ UI | 代码完成，待 API 联调 |
| 正向提示词 | ✅ | ✅ | 完成 |
| 负向提示词 | ✅ | ✅ | 完成 |
| 基础图选择 | ✅ | ✅ | 完成 |
| 去噪强度 | ✅ | ✅ | 完成 |
| 实时进度显示 | ✅ | ⚠️ | UI 完成，轮询逻辑待接 |
| 步数/速度/剩余时间 | ✅ | ⚠️ | UI 完成，数据待接 |
| CPU 降级检测 | ✅ | ❌ | 待实现 |
| 取消生成 | ✅ | ⚠️ | UI 完成，停止+重启逻辑待接 |
| 复制图片到剪贴板 | ✅ | ❌ | 待实现 |
| 保存图片到相册 | ✅ | ❌ | 待实现 |
| 后端重启同步 | ✅ | ❌ | 待实现 |
| 模型加载进度 | ✅ | ⚠️ | UI 完成，API 待接 |
| 错误显示 | ✅ | ⚠️ | 基本完成 |

### 13.2 模型管理

| 功能 | WebUI | Android | 状态 |
|------|-------|---------|------|
| 列出本地模型 | ✅ | ✅ UI | 完成 |
| 激活/加载模型 | ✅ | ✅ UI | 完成 |
| 卸载模型 | ✅ | ✅ UI | 完成 |
| 删除模型文件 | ✅ | ✅ UI | 完成 |
| 从 URL 下载 | ✅ | ✅ UI | 完成 |
| 下载进度轮询 | ✅ | ⚠️ | UI 完成，API 待接 |
| 取消下载 | ✅ | ❌ | 待实现 |
| 导入本地文件 | ✅ | ⚠️ | UI 完成，文件选择待接 |
| 模型库推荐 | ✅ | ✅ | 完成 |
| 活跃模型状态 | ✅ | ✅ | 完成 |
| 后端模式/设备信息 | ✅ | ❌ | 待实现 |

### 13.3 参数控制

| 功能 | WebUI | Android | 状态 |
|------|-------|---------|------|
| 分辨率预设 | ✅ | ✅ | 完成 |
| 宽高比选择 | ✅ | ✅ | 完成 |
| 自定义宽高 | ✅ | ✅ | 完成 |
| 步数滑条 | ✅ | ✅ | 完成 |
| CFG Scale 滑条 | ✅ | ✅ | 完成 |
| 采样方法选择 | ✅ | ✅ | 完成 (17种) |
| 种子输入 | ✅ | ✅ | 完成 |
| 后端类型选择 | ✅ | ✅ | 完成 |
| CPU 线程数 | ✅ | ✅ | 完成 |
| VAE 平铺 | ✅ | ✅ | 完成 |
| VAE 转 CPU | ✅ | ✅ | 完成 |
| TAESD | ✅ | ✅ | 完成 |
| Flash Attention | ✅ | ✅ | 完成 |
| 模型参数自动同步 | ✅ | ❌ | 待实现 |

### 13.4 其他功能

| 功能 | WebUI/RN | Android | 状态 |
|------|----------|---------|------|
| 频道系统 | ✅ (RN) | ✅ | 完成 |
| 频道创建/编辑 | ✅ (RN) | ✅ | 完成 |
| 图库浏览 | ✅ (RN) | ✅ | 完成 |
| 历史记录持久化 | ✅ (RN) | ❌ | Room 待实现 |
| 服务器地址配置 | ✅ | ✅ | 完成 |
| 深色主题 | ✅ | ✅ | 完成 |
| 遥测数据显示 | ✅ | ❌ | 待实现 |
| 系统就绪检查 | ✅ | ❌ | 待实现 |
| 本地 MNN 推理 | — | ❌ | 架构预留 |
| i18n 国际化 | ✅ | ❌ | 待实现 |

---

## 14. 开发路线图

### Phase 1：核心功能联调（优先）

1. **API 客户端联调** — ApiClient.kt 与 PC 后端实际通信
2. **生成流程打通** — 点击 Generate → 后端重启 → 生成 → 进度 → 结果
3. **模型管理打通** — 列表 → 加载 → 卸载 → 删除
4. **图片保存** — Base64 → Bitmap → MediaStore 保存到相册
5. **设备连接测试** — Redmi K50 安装验证

### Phase 2：体验完善

6. **进度轮询** — 1s 间隔 getGenerationProgress，实时更新
7. **取消生成** — stopServer + restartServer 逻辑
8. **模型下载** — URL 输入 → download-model → 进度轮询
9. **后端重启同步** — 检测参数变化自动重启
10. **模型参数自动同步** — 切换模型时自动调整 steps/CFG/分辨率

### Phase 3：数据持久化

11. **Room 数据库** — GenerationHistory 表
12. **图库持久化** — 重启后保留历史记录
13. **DataStore 设置** — 服务器地址、用户偏好
14. **频道持久化** — 自定义频道保存

### Phase 4：高级功能

15. **本地 MNN 推理** — 完整的端侧推理流程
16. **遥测面板** — CPU/RAM/VRAM 实时监控
17. **提示词历史** — 快速复用历史 prompt
18. **i18n** — 中英文切换
19. **系统就绪检查** — health/diagnostics/cleanup
20. **批量生成** — 多图并行

---

## 附录 A：WebUI 与 Android 文件对应关系

| WebUI 文件 | Android 文件 | 说明 |
|------------|-------------|------|
| `App.jsx` | `MainViewModel.kt` + `Navigation.kt` | 状态管理+路由 |
| `Generator.jsx` | `GenerateScreen.kt` | 生成页面 |
| `ModelManager.jsx` | `ModelsScreen.kt` | 模型管理 |
| `ImageConstraints.jsx` | `ConstraintsScreen.kt` | 参数控制 |
| `Sidebar.jsx` | `Navigation.kt` (侧边栏部分) | 导航 |
| `TopStatusBar.jsx` | `Navigation.kt` (顶栏部分) | 状态栏 |
| `api.js` | `ApiClient.kt` | API 客户端 |
| `App.css` | `theme/Color.kt` + `Theme.kt` | 样式/主题 |
| `i18n.js` | — | 待实现 |
| `en-US.json` / `zh-CN.json` | — | 待实现 |

## 附录 B：React Native 参考源码

| RN 文件 | 说明 |
|---------|------|
| `mobile/src/screens/GeneratorScreen.tsx` | 生成页面（含进度/取消/保存） |
| `mobile/src/screens/ModelsScreen.tsx` | 模型管理（含下载进度） |
| `mobile/src/screens/ConstraintsScreen.tsx` | 参数控制 |
| `mobile/src/screens/ChannelsScreen.tsx` | 频道管理 |
| `mobile/src/screens/GalleryScreen.tsx` | 图库 |
| `mobile/src/screens/SettingsScreen.tsx` | 设置 |
| `mobile/src/store/appStore.ts` | Zustand 状态管理 |
| `mobile/src/services/api.ts` | API 服务 |

## 附录 C：PC 后端二进制文件

| 文件 | 路径 | 说明 |
|------|------|------|
| sd-vulkan.exe | `app/backend/win/vulkan/sd-vulkan.exe` | Vulkan GPU 后端 (1.4MB) |
| stable-diffusion.dll | `app/backend/win/vulkan/stable-diffusion.dll` | SD 核心库 (105MB) |
| sd-cuda (目录) | `app/backend/win/cuda/` | CUDA GPU 后端 |

---

*文档生成时间：2026-06-10 21:20*
*基于 WebUI 源码版本：polish-setup-v1 (EXPECTED_SERVER_BUILD)*
