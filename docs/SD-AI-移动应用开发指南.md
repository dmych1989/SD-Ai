# SD-AI 移动应用开发指南

## 📱 项目概述

### 基本信息
- **应用名称**: SD-AI ✅
- **显示名**: SD-AI
- **Android 包名**: com.sdai.app
- **开发环境**: Android Studio (C. Native + ML Kit)
- **目标设备**: 红米K50 (天玑8100 CPU, Mali-G GPU)
- **平台要求**: Android 14

### 核心功能需求
1. **🚀 完全本地运行**: 利用WebGPU和ML Kit在手机本地执行AI推理
2. **🔗 PC后端连接**: 通过WiFi连接PC后端API (`:1420`)
3. **📦 模型转换**: 支持转换和使用桌面端模型
4. **🎯 多种尺寸**: 256、512、16:9、4:3、9:16
5. **💾 离线优先**: 支持离线运行，无需网络连接

---

## 🛠️ 技术架构设计

### 方案选择：Android Native + ML Kit

#### 核心技术栈
```
📱 Android (Native)
├── 🎯 Android Studio (Java/Kotlin)
├── 🧠 ML Kit (本地AI推理)
├── 🌐 WebGPU (GPU加速)
├── 📡 Network (WiFi连接PC)
└── 🗂️ SQLite (数据存储)
```

#### 双模式架构
```
SD-AI App
├── 📱 本地模式 (Offline)
│   ├── ML Kit + WebGPU推理
│   ├── 本地模型文件
│   └── SQLite缓存
│
└── 🌐 远程模式 (PC Backend)
    ├── HTTP客户端 (:1420)
    ├── 实时进度监控
    └── PC模型管理
```

---

## 🎨 UI/UX 设计

### 主界面布局
- **5-tab 底部导航**: 生成、图库、模型、设置、连接
- **Material Design 3** 风格
- **深色/浅色主题** 自动适配

### 关键功能页面
1. **GeneratorScreen** - AI图像生成
2. **GalleryScreen** - 图库管理
3. **ModelsScreen** - 模型管理
4. **SettingsScreen** - 应用设置
5. **ConnectionScreen** - PC连接状态

---

## 🚀 实现步骤

### 第一阶段：项目初始化

#### 1. 创建Android项目
```bash
# 在Android Studio中创建新项目
# Package Name: com.sdai.app
# Minimum SDK: 24 (Android 7.0)
# Target SDK: 34 (Android 14)
```

#### 2. 添加依赖
```gradle
// build.gradle (Module: app)
dependencies {
    // ML Kit
    implementation 'com.google.mlkit:image-labeling:17.0.7'
    implementation 'com.google.mlkit:text-recognition:16.0.0'
    
    // WebGPU
    implementation 'androidx.webkit:webkit:1.10.0'
    
    // Network
    implementation 'com.squareup.okhttp3:okhttp:4.12.0'
    
    // SQLite
    implementation 'androidx.sqlite:sqlite:2.4.0'
    
    // Material Design
    implementation 'com.google.android.material:material:1.11.0'
    
    // Coroutines
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
}
```

#### 3. 权限配置
```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.CAMERA" />
```

### 第二阶段：WebGPU集成

#### 1. WebGPU初始化
```kotlin
// WebGPUManager.kt
class WebGPUManager {
    private var device: GPUDevice? = null
    private var context: GPUContext? = null
    
    suspend fun initialize(): Boolean {
        return try {
            context = GPUAdapter.requestAdapter()
            device = context?.requestDevice()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    fun executeWebGLPipeline(shaderCode: String): GPUBuffer? {
        // 执行WebGL着色器pipeline
        return device?.createBuffer(shaderCode)
    }
}
```

#### 2. WebGL着色器转换
```kotlin
// ShaderConverter.kt
object ShaderConverter {
    fun convertStableDiffusionShader(): String {
        // 将desktop端WebGL着色器转换为mobile端格式
        return """
            // Mobile-optimized Stable Diffusion WebGL shader
            precision mediump float;
            varying vec2 vUv;
            uniform sampler2D uInput;
            uniform sampler2D uLatent;
            
            void main() {
                vec4 color = texture2D(uInput, vUv);
                // Mobile-optimized operations
                gl_FragColor = color;
            }
        """.trimIndent()
    }
}
```

### 第三阶段：ML Kit集成

#### 1. 本地AI推理
```kotlin
// LocalInferenceEngine.kt
class LocalInferenceEngine(private val context: Context) {
    private val webGPUManager = WebGPUManager()
    private val modelInterpreter = MLModelInterpreter()
    
    suspend fun inferLocal(
        prompt: String,
        modelPath: String,
        width: Int,
        height: Int
    ): Result<Bitmap> {
        return try {
            // 1. 加载模型
            val model = loadLocalModel(modelPath)
            
            // 2. WebGPU推理
            val input = preprocessInput(prompt, width, height)
            val output = webGPUManager.executeInference(model, input)
            
            // 3. 后处理
            val result = postProcessOutput(output)
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

#### 2. 模型文件处理
```kotlin
// ModelManager.kt
class ModelManager(private val context: Context) {
    private val modelsDir = File(context.filesDir, "models")
    
    fun convertDesktopModel(desktopPath: String): Boolean {
        return try {
            val desktopModel = File(desktopPath)
            val mobileModel = File(modelsDir, "${desktopModel.name}.mobile")
            
            // 转换模型格式
            val converter = ModelConverter()
            converter.convertForMobile(desktopModel, mobileModel)
            
            true
        } catch (e: Exception) {
            false
        }
    }
    
    fun getAvailableModels(): List<ModelInfo> {
        return modelsDir.listFiles()?.map { file ->
            ModelInfo(
                name = file.name,
                size = file.length(),
                path = file.absolutePath
            )
        } ?: emptyList()
    }
}
```

### 第四阶段：PC后端连接

#### 1. HTTP客户端
```kotlin
// PCBackendClient.kt
class PCBackendClient {
    private val okHttpClient = OkHttpClient()
    private val baseUrl = "http://192.168.1.100:1420" // 自动发现PC
    
    suspend fun generateImage(
        prompt: String,
        negativePrompt: String,
        model: String,
        constraints: ImageConstraints
    ): Result<GenerationResult> {
        return try {
            val request = Request.Builder()
                .url("$baseUrl/api/generate")
                .post(RequestBody.create(
                    MediaType.parse("application/json"),
                    Gson().toJson(mapOf(
                        "prompt" to prompt,
                        "negative_prompt" to negativePrompt,
                        "model" to model,
                        "constraints" to constraints
                    ))
                ))
                .build()
            
            val response = okHttpClient.newCall(request).execute()
            val result = Gson().fromJson(
                response.body()?.string(),
                GenerationResult::class.java
            )
            
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

#### 2. 自动发现PC
```kotlin
// PCDiscovery.kt
class PCDiscovery {
    suspend fun discoverPC(): String? {
        // 扫描局域网内运行SD-AI PC的设备
        val network = NetworkInterface.getNetworkInterfaces()
        while (network.hasMoreElements()) {
            val iface = network.nextElement()
            if (iface.name == "wlan0") {
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is Inet4Address) {
                        val baseUrl = "http://${addr.hostAddress}:1420/api/ping"
                        if (testConnection(baseUrl)) {
                            return baseUrl.replace("/api/ping", "")
                        }
                    }
                }
            }
        }
        return null
    }
}
```

### 第五阶段：主界面实现

#### 1. 生成界面
```kotlin
// GeneratorScreen.kt
@Composable
fun GeneratorScreen(
    viewModel: GeneratorViewModel = hiltViewModel()
) {
    val prompt by viewModel.prompt.collectAsState()
    val negativePrompt by viewModel.negativePrompt.collectAsState()
    val constraints by viewModel.constraints.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 提示词输入
        OutlinedTextField(
            value = prompt,
            onValueChange = { viewModel.updatePrompt(it) },
            label = { Text("提示词") },
            modifier = Modifier.fillMaxWidth()
        )
        
        // 负面提示词
        OutlinedTextField(
            value = negativePrompt,
            onValueChange = { viewModel.updateNegativePrompt(it) },
            label = { Text("负面提示词") },
            modifier = Modifier.fillMaxWidth()
        )
        
        // 参数设置
        ConstraintSettingsScreen(
            constraints = constraints,
            onConstraintsChange = { viewModel.updateConstraints(it) }
        )
        
        // 生成按钮
        Button(
            onClick = { viewModel.startGeneration() },
            enabled = !isGenerating,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isGenerating) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                Text("开始生成")
            }
        }
    }
}
```

#### 2. 模型管理界面
```kotlin
// ModelsScreen.kt
@Composable
fun ModelsScreen(
    viewModel: ModelsViewModel = hiltViewModel()
) {
    val models by viewModel.models.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    
    LazyColumn {
        items(models) { model ->
            ModelItem(
                model = model,
                isSelected = model.name == selectedModel,
                onClick = { viewModel.selectModel(model.name) },
                onDownload = { viewModel.downloadModel(model) },
                onDelete = { viewModel.deleteModel(model.name) }
            )
        }
    }
}
```

---

## 🎯 关键实现细节

### 1. WebGPU优化
```kotlin
// WebGPU优化配置
class WebGPUOptimizer {
    fun optimizeForMobileDevice(): Map<String, Any> {
        return mapOf(
            "workgroupSize" to 256, // 限制工作组大小
            "maxMemory" to 128 * 1024 * 1024, // 128MB
            "shaderPrecision" to "mediump", // 中等精度
            "batchSize" to 1, // 批处理大小
            "useComputeShaders" to false // 禁用计算着色器
        )
    }
}
```

### 2. 模型转换
```kotlin
// 模型转换服务
class ModelConverter {
    fun convertForDesktopToMobile(desktopModel: File): File {
        return try {
            // 1. 读取desktop模型
            val desktopData = desktopModel.readBytes()
            
            // 2. 转换格式
            val mobileData = convertTensorFormat(desktopData, MobileFormat())
            
            // 3. 优化大小
            val optimizedData = optimizeModelSize(mobileData)
            
            // 4. 保存
            val mobileModel = File.createTempFile("mobile_model", ".bin")
            mobileModel.writeBytes(optimizedData)
            mobileModel
        } catch (e: Exception) {
            throw ModelConversionException("转换失败", e)
        }
    }
}
```

### 3. 双模式切换
```kotlin
// 应用模式管理
enum class AppMode {
    LOCAL,     // 本地模式
    REMOTE     // 远程模式
}

class AppModeManager {
    var currentMode: AppMode = AppMode.LOCAL
    
    fun switchToLocalMode() {
        currentMode = AppMode.LOCAL
        stopPCConnection()
        startLocalInference()
    }
    
    fun switchToRemoteMode() {
        currentMode = AppMode.REMOTE
        stopLocalInference()
        startPCConnection()
    }
}
```

---

## 📋 构建和部署

### 本地构建
```bash
# 1. 构建APK
./gradlew assembleDebug

# 2. 生成签名密钥
keytool -genkey -v -keystore sdai-release.keystore -alias sdai -keyalg RSA -keysize 2048 -validity 10000

# 3. 签名APK
jarsigner -verbose -sigalg SHA1withRSA -digestalg SHA1 -keystore sdai-release.keystore app/build/outputs/apk/debug/app-debug.apk sdai

# 4. 安装到设备
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 优化配置
```gradle
// build.gradle (优化配置)
android {
    compileSdk 34
    
    defaultConfig {
        minSdk 24
        targetSdk 34
        ndk {
            abiFilters 'armeabi-v7a', 'arm64-v8a'
        }
    }
    
    buildTypes {
        release {
            minifyEnabled true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
    
    packaging {
        jniLibs {
            keepDebugSymbols.add("*.so")
            pickFirsts.add("lib/x86/libc++_shared.so")
        }
    }
}
```

---

## 🔄 开发流程

### 开发阶段
1. **阶段1**: 项目初始化 + WebGPU集成
2. **阶段2**: ML Kit + 本地推理
3. **阶段3**: 模型转换 + 文件管理
4. **阶段4**: PC后端连接 + 网络功能
5. **阶段5**: UI完善 + 性能优化
6. **阶段6**: 测试 + 部署

### 测试计划
- **本地模式测试**: WebGPU推理、内存使用、生成质量
- **远程模式测试**: PC连接、数据传输、实时状态
- **性能测试**: 生成时间、电池消耗、发热控制
- **兼容性测试**: 不同Android版本、屏幕尺寸

---

## 📦 最终交付物

### 应用包结构
```
SD-AI-App/
├── app/
│   ├── src/main/java/com/sdai/app/
│   │   ├── MainActivity.kt
│   │   ├── webgpu/
│   │   ├── mlkit/
│   │   ├── models/
│   │   ├── networking/
│   │   └── ui/
│   ├── res/drawable/
│   ├── res/values/
│   └── AndroidManifest.xml
├── gradle/
├── build.gradle
└── README.md
```

### 应用功能
- ✅ **本地AI生成**: 使用WebGPU+ML Kit
- ✅ **PC后端连接**: WiFi局域网连接
- ✅ **模型管理**: 转换和管理模型文件
- ✅ **图库管理**: 本地图像管理
- ✅ **参数设置**: 多种分辨率和比例
- ✅ **主题切换**: 深色/浅色模式

这个方案将为您的红米K50提供一个完整的SD-AI移动应用，支持本地和远程两种模式，充分利用设备的WebGPU和ML Kit能力。