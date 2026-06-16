# 快速构建指南（避免网络问题）

## 方案 1：用 Android Studio 打开项目（推荐）

### 步骤
1. 打开 **Android Studio** (已在你的电脑安装)
2. `File → Open` → 选择 `D:\GitHub\Local-AI-Image-Generator\android-app`
3. Android Studio 会自动：
   - 下载 Gradle（通过国内镜像）
   - 同步依赖
   - 下载 Compose / Kotlin 编译器

### 构建 APK
1. 等待 Gradle 同步完成（底部状态栏）
2. `Build → Build Bundle(s) / APK(s) → Build APK(s)`
3. 输出路径：`app\build\outputs\apk\debug\app-debug.apk`

### 安装到手机
```powershell
# 手机已连接: 4XCA9PZDQSPJIZG6
$adb = "C:\Users\Administrator\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb install -r "D:\GitHub\Local-AI-Image-Generator\android-app\app\build\outputs\apk\debug\app-debug.apk"
```

---

## 方案 2：手动下载 Gradle Wrapper

### 2.1 下载 gradle-wrapper.jar
1. 访问：https://raw.githubusercontent.com/gradle/gradle/v8.5.0/gradle/wrapper/gradle-wrapper.jar
2. 右键保存为 `gradle-wrapper.jar`
3. 放入：`android-app\gradle\wrapper\gradle-wrapper.jar`

### 2.2 修改 gradle-wrapper.properties（使用国内镜像）
```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://mirrors.cloud.tencent.com/gradle/gradle-8.5-bin.zip
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

### 2.3 构建
```powershell
cd D:\GitHub\Local-AI-Image-Generator\android-app
.\gradlew.bat assembleDebug
```

---

## 方案 3：使用 Android Studio 自带 Gradle

### 3.1 查找 Gradle 路径
```
D:\Program Files\Android\Android Studio\gradle\gradle-8.5\bin\gradle.bat
```

### 3.2 直接构建
```powershell
$gradle = "D:\Program Files\Android\Android Studio\gradle\gradle-8.5\bin\gradle.bat"
cd D:\GitHub\Local-AI-Image-Generator\android-app
& $gradle assembleDebug
```

---

## MNN 库说明

当前 `app\libs\MNN.aar` 是**占位文件**（空实现），用于让项目编译通过。

### 获取真实 MNN AAR

#### 方法 1：从 MNN 官方下载
1. 访问：https://github.com/alibaba/MNN/releases
2. 下载 `MNN-2.9.10-Android.zip`
3. 解压后，将 `.so` 文件放入 `app\src\main\jniLibs\arm64-v8a\`
4. 删除占位 `MNN.aar`

#### 方法 2：使用预构建 AAR（推荐）
```powershell
# 下载预构建 MNN AAR（含 Vulkan 支持）
$mnnAar = "https://github.com/alibaba/MNN/releases/download/2.9.10/MNN-2.9.10.aar"
Invoke-WebRequest -Uri $mnnAar -OutFile "D:\GitHub\Local-AI-Image-Generator\android-app\app\libs\MNN.aar"
```

---

## 故障排查

### 问题 1：Gradle 同步失败
- 检查网络（需要访问 Maven 仓库）
- 在 `gradle.properties` 添加国内镜像：
```properties
systemProp.http.proxyHost=mirrors.cloud.tencent.com
systemProp.https.proxyHost=mirrors.cloud.tencent.com
```

### 问题 2：Java 版本错误
- 确保使用 JDK 17+（Android Studio 自带 JDK 21）
- 在 Android Studio 中设置：`File → Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK → 选择 "Embedded JDK 21"`

### 问题 3：MNN 库缺失
- 暂时注释掉 `app/build.gradle` 中的 MNN 依赖：
```groovy
// implementation fileTree(dir: 'libs', include: ['*.aar', '*.jar'])
```
- 先用**远程模式**测试 UI

---

## 下一步

1. **先用 Android Studio 打开项目**，让它自动处理 Gradle
2. **构建 Debug APK**
3. **安装到红米 K50**，测试远程模式
4. **确认 UI 没问题后**，再下载真实 MNN 库接入本地推理

---

## 项目状态

✅ 所有源码文件已创建
✅ Android Studio 项目结构完整
✅ Gradle 构建配置已写好
⚠️ 需要 Android Studio 打开并同步（自动下载 Gradle）
⚠️ MNN 库需要替换为真实版本（当前是占位文件）
