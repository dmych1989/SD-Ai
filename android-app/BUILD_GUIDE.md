# SD-AI Android App 构建指南

## 项目概述
- **项目名称**: SD-AI 本地生图
- **包名**: com.sdaiapp
- **最低 SDK**: 26 (Android 8.0, Vulkan 1.1+)
- **目标 SDK**: 34 (Android 14)
- **CPU 架构**: arm64-v8a (天玑 8100)

## 已实现功能
✅ **远程模式**: 通过 WiFi 连接 PC 端 Stable Diffusion 后端
✅ **本地模式框架**: MNN 推理引擎封装（待接入真实 MNN API）
✅ **错误日志自动抓取**: 
  - 自动捕获 Java Crash → 写入 `files/logs/fatal_*.log`
  - 手动抓取 Logcat（设置页面按钮）
  - 后台实时日志抓取服务
✅ **Material Design 3 UI**: Compose 实现
✅ **双模式切换**: 本地离线 / 远程 PC API

## 项目结构
```
android-app/
├── app/
│   ├── build.gradle              # 构建配置
│   ├── proguard-rules.pro        # 混淆规则
│   └── src/main/
│       ├── AndroidManifest.xml   # 权限 + Application 声明
│       ├── java/com/sdaiapp/
│       │   ├── SDApp.kt                  # Application 初始化
│       │   ├── ui/                       # Compose UI
│       │   ├── inference/                # MNN 推理引擎
│       │   ├── network/                  # PC 远程 API 客户端
│       │   ├── service/                  # 日志抓取服务
│       │   ├── utils/                    # 日志工具
│       │   └── data/                     # 数据模型
│       └── res/                          # 资源文件
├── build.gradle
├── gradlew.bat
└── gradle/wrapper/
```

## 构建步骤

### 1. 安装依赖
```powershell
# 1.1 安装 JDK 17+
# 下载: https://adoptium.net/temurin/releases/?version=17
# 安装后设置 JAVA_HOME 环境变量

# 1.2 安装 Android SDK Command-line Tools
# 已自动安装到: C:\Users\Administrator\AppData\Local\Android\Sdk\cmdline-tools\latest\

# 1.3 安装 Gradle Wrapper
cd D:\GitHub\Local-AI-Image-Generator\android-app
# 手动下载 gradle-wrapper.jar 到 gradle\wrapper\
# URL: https://raw.githubusercontent.com/gradle/gradle/v8.8.0/gradle/wrapper/gradle-wrapper.jar
```

### 2. 构建 Debug APK
```powershell
cd D:\GitHub\Local-AI-Image-Generator\android-app
.\gradlew.bat assembleDebug
```

### 3. 安装到红米 K50
```powershell
# 手机已连接: 4XCA9PZDQSPJIZG6
$adb = "C:\Users\Administrator\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb install -r "app\build\outputs\apk\debug\app-debug.apk"
```

## 下一步开发计划

### 阶段 1: 远程模式测试（当前）
- [x] UI 框架完成
- [x] PC API 客户端完成
- [ ] 构建 APK 并测试远程连接

### 阶段 2: 本地推理接入
- [ ] 下载 MNN Android AAR (Vulkan 支持)
- [ ] 接入 MNN 推理 API
- [ ] 转换 SD 模型为 .mnn 格式
- [ ] 优化天玑 8100 推理性能

### 阶段 3: 功能完善
- [ ] 图片保存至相册
- [ ] 生成历史记录
- [ ] 模型管理界面
- [ ] Img2Img 功能

## 故障排查

### 日志抓取
- **自动**: 崩溃日志在 `Android/data/com.sdaiapp/files/logs/`
- **手动**: 设置页面 →「抓取并分享 Logcat」
- **实时**: 设置页面 →「启动实时日志抓取」

### 连接问题
1. 确保 PC 端运行 `start.bat`
2. 手机与 PC 在同一 WiFi
3. 检查 PC 防火墙是否允许端口 1420

## 性能预期（天玑 8100 + Mali-G610）
- **SD 1.5 (512×512, 20 steps)**: ~3-8 分钟/张
- **SD-Turbo (512×512, 2 steps)**: ~30 秒/张
- **建议**: 使用 SD-Turbo 或 LCM 加速模型

## 资源链接
- MNN GitHub: https://github.com/alibaba/MNN
- MNN Releases: https://github.com/alibaba/MNN/releases
- Stable Diffusion: https://github.com/CompVis/stable-diffusion
- 模型转换指南: 见 `SD-AI_ARCHITECTURE.md`
