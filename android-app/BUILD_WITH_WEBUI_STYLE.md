# Android 应用重构 - WebUI 风格界面

## 完成状态

### ✅ 已完成的改造

1. **主题系统** (`ui/theme/`)
   - `Color.kt` - Material 3 暗色主题配色（紫色主色调 + 玻璃态效果）
   - `Theme.kt` - 完整的暗色主题配置
   - `Type.kt` - Inter 字体风格的排版系统

2. **数据模型** (`data/model/`)
   - `Models.kt` - 完整的数据类：ServerStatus, GenerateParams, ModelInfo, HistoryItem, GenerationState, AppSettings 等

3. **网络层** (`network/`)
   - `ApiClient.kt` - Ktor HTTP 客户端，支持系统状态、模型管理、图像生成、历史记录 API
   - `MNNEngine.kt` - MNN 本地推理引擎封装

4. **状态管理** (`ui/viewmodel/`)
   - `MainViewModel.kt` - MVVM 架构的 ViewModel，管理所有应用状态

5. **界面组件** (`ui/components/`)
   - `Navigation.kt` - 侧边导航栏 + 顶栏组件（与 WebUI 风格一致）

6. **主界面** (`ui/screens/`)
   - `GenerateScreen.kt` - 生成界面（提示词、比例、参数滑块、预览、生成按钮）
   - `ModelsScreen.kt` - 模型管理界面（搜索、过滤、选择）
   - `GalleryScreen.kt` - 图库界面（网格/列表视图、收藏、排序）
   - `SettingsScreen.kt` - 设置界面（连接、推理、历史、通知）

7. **主活动** (`ui/MainActivity.kt`)
   - 整合所有组件的 MainScreen Composable

## 设计亮点

### 🎨 视觉风格
- **暗色主题**：深蓝黑色背景 (#090A0F)
- **玻璃态效果**：半透明卡片 + 微光边框
- **紫色主色调**：#6366F1 (Indigo)
- **渐变按钮**：紫到蓝渐变

### 📱 界面布局
- **左侧导航**：图标式导航栏（72dp 宽）
- **顶部状态栏**：服务器状态、GPU 内存指示器
- **响应式内容区**：自适应不同屏幕

### 🔧 技术栈
- Jetpack Compose + Material 3
- MVVM 架构 + StateFlow
- Ktor 网络通信
- MNN 本地推理

## 构建说明

### 使用 Android Studio 构建

1. **打开项目**
   ```
   File → Open → 选择 android-app 目录
   ```

2. **等待 Gradle 同步完成**
   - 右下角会显示 "Syncing..."
   - 完成后显示 "Gradle sync finished"

3. **选择运行设备**
   - Redmi K50 (4XCA9PZDQSPJIZG6) - 已连接

4. **构建 Debug APK**
   ```
   Build → Build Bundle(s) / APK(s) → Build APK
   ```

5. **安装到设备**
   ```
   Run → Run 'app'
   ```

### 命令行构建（可选）

如果需要命令行构建，请确保配置：
```bash
# 设置 JAVA_HOME
export JAVA_HOME="D:\Program Files\Android\Android Studio\jbr"

# 运行 Gradle
cd android-app
./gradlew assembleDebug
```

## 文件结构

```
android-app/
├── app/src/main/java/com/sdaiapp/
│   ├── data/model/
│   │   └── Models.kt          # 数据模型
│   ├── network/
│   │   ├── ApiClient.kt       # API 客户端
│   │   └── MNNEngine.kt      # MNN 推理引擎
│   ├── ui/
│   │   ├── MainActivity.kt    # 主活动
│   │   ├── components/
│   │   │   └── Navigation.kt  # 导航组件
│   │   ├── screens/
│   │   │   ├── GenerateScreen.kt
│   │   │   ├── ModelsScreen.kt
│   │   │   ├── GalleryScreen.kt
│   │   │   └── SettingsScreen.kt
│   │   ├── theme/
│   │   │   ├── Color.kt
│   │   │   ├── Theme.kt
│   │   │   └── Type.kt
│   │   └── viewmodel/
│   │       └── MainViewModel.kt
│   ├── service/
│   │   └── LogcatService.kt
│   └── utils/
│       └── CrashLogger.kt
└── build.gradle
```

## 待完成功能

### 高优先级
1. **MNN 模型加载** - 集成实际的 MNN 模型推理
2. **图片保存** - 将生成结果保存到相册
3. **历史持久化** - 使用 Room 数据库保存历史记录

### 中优先级
4. **图片预览** - 点击图库项查看大图
5. **图片操作** - 缩放、分享、删除
6. **离线模式** - 完全脱离服务器工作

### 低优先级
7. **多语言支持** - 中文/英文切换
8. **主题切换** - 亮色/暗色主题
9. **快捷键** - 常用操作的快捷键支持

## API 端点

应用期望后端提供以下 API：

```
GET  /api/system/status     - 获取系统状态
GET  /api/models           - 获取模型列表
POST /api/models/select    - 选择模型
POST /api/generate         - 开始生成
POST /api/generate/cancel  - 取消生成
GET  /api/history          - 获取历史记录
POST /api/history/clear   - 清除历史
GET  /api/images/{id}      - 获取图片
POST /api/images/upscale   - 放大图片
```

## 注意事项

1. **服务器 URL**：默认 `http://192.168.1.100:7860`，可在设置中修改
2. **MNN 模型**：需要 .mnn 格式的模型文件，放置在应用可访问的目录
3. **权限**：首次使用需要授予存储和网络权限
4. **性能**：MNN GPU 加速需要设备支持 Vulkan

## 技术债务

- [ ] 移除未使用的旧文件 (GeneratorScreen_old.kt, ModelInfo.kt, etc.)
- [ ] 添加单元测试覆盖
- [ ] 优化大量图片加载的内存占用
- [ ] 添加错误重试机制
- [ ] 实现离线模式的状态同步

---
生成时间: 2026-06-10
