# CUDA 后端手动下载指南

## 下载信息

**文件**: sd-master-2d40a8b-bin-win-cuda12-x64.zip  
**大小**: 334.7 MB  
**用途**: NVIDIA GPU 加速后端

---

## 方法 1：使用镜像加速（推荐）

### 镜像地址（选择一个速度快的）

**镜像 1**: ghproxy.net（已验证可用）
```
https://ghproxy.net/https://github.com/leejet/stable-diffusion.cpp/releases/download/master-669-2d40a8b/sd-master-2d40a8b-bin-win-cuda12-x64.zip
```

**镜像 2**: ghproxy.cn
```
https://ghproxy.cn/https://github.com/leejet/stable-diffusion.cpp/releases/download/master-669-2d40a8b/sd-master-2d40a8b-bin-win-cuda12-x64.zip
```

**镜像 3**: gh.api.99988866.xyz
```
https://gh.api.99988866.xyz/https://github.com/leejet/stable-diffusion.cpp/releases/download/master-669-2d40a8b/sd-master-2d40a8b-bin-win-cuda12-x64.zip
```

---

## 方法 2：使用多线程下载工具（最快）

### Internet Download Manager (IDM)
1. 复制上面的镜像地址
2. IDM 会自动捕获下载，或手动 "任务 → 添加新任务"
3. IDM 会自动开启多线程加速（通常 8-16 线程）
4. 速度可达 1-5 MB/s（取决于你的带宽）

### Android Download Manager (ADM)
- 如果你有 Android 设备，可以用 ADM 下载后传到电脑
- 支持多线程，速度通常比电脑快

### aria2 GUI 工具
- **Motrix**: https://motrix.app/（支持 HTTP/FTP/BT，界面友好）
- **XDM (Xtreme Download Manager)**: 开源免费，支持浏览器集成

---

## 方法 3：使用命令行工具（已安装 aria2）

如果当前下载太慢，可以取消后重新运行（支持断点续传）：

```powershell
# 停止当前下载（如果正在运行）
# 然后在项目目录执行：
cd D:\GitHub\Local-AI-Image-Generator\app\tools
..\..\app\tools\aria2c.exe -x 16 -s 16 --continue=true --max-tries=0 https://ghproxy.net/https://github.com/leejet/stable-diffusion.cpp/releases/download/master-669-2d40a8b/sd-master-2d40a8b-bin-win-cuda12-x64.zip
```

---

## 下载后安装步骤

1. **下载完成后**，将 `sd-cuda.zip` 放到：
   ```
   D:\GitHub\Local-AI-Image-Generator\app\tools\sd-cuda.zip
   ```

2. **解压文件**：
   ```powershell
   cd D:\GitHub\Local-AI-Image-Generator\app\tools
   Expand-Archive -Path sd-cuda.zip -DestinationPath sd-cuda-temp -Force
   ```

3. **复制到后端目录**：
   ```powershell
   $backendDir = "D:\GitHub\Local-AI-Image-Generator\app\backend\win\cuda"
   Copy-Item "sd-cuda-temp\bin\sd-server.exe" "$backendDir\sd-cuda.exe" -Force
   Copy-Item "sd-cuda-temp\bin\stable-diffusion.dll" $backendDir -Force
   Get-ChildItem "sd-cuda-temp" -Filter "*.dll" -Recurse | Copy-Item -Destination $backendDir -Force
   ```

4. **清理临时文件**：
   ```powershell
   Remove-Item "sd-cuda.zip" -Force
   Remove-Item "sd-cuda-temp" -Recurse -Force
   ```

5. **验证安装**：
   ```powershell
   Get-ChildItem "D:\GitHub\Local-AI-Image-Generator\app\backend\win\cuda" | Select-Object Name, @{Name="Size(MB)";Expression={[math]::Round($_.Length/1MB,2)}}
   ```
   应该看到 `sd-cuda.exe` (约 14 MB) 和 `stable-diffusion.dll` (约 56 MB) 等文件。

6. **启动项目**：
   - 双击 `start.bat`，或
   - 访问 http://localhost:1420

---

## 备用：Vulkan 后端（AMD/Intel GPU 或 CPU 模式）

如果 NVIDIA GPU 不可用，可以下载 Vulkan 后端：

**下载地址**（使用相同镜像前缀）：
```
https://ghproxy.net/https://github.com/leejet/stable-diffusion.cpp/releases/download/master-669-2d40a8b/sd-master-2d40a8b-bin-win-vulkan-x64.zip
```

**安装位置**: `D:\GitHub\Local-AI-Image-Generator\app\backend\win\vulkan\`

---

## 模型下载

后端安装完成后，还需要下载模型文件（.safetensors 或 .gguf）：

**推荐模型**：
- SD 1.5: https://huggingface.co/runwayml/stable-diffusion-v1-5
- SDXL: https://huggingface.co/stabilityai/stable-diffusion-xl-base-1.0

**放置位置**: `D:\GitHub\Local-AI-Image-Generator\app\models\`

---

## 故障排除

### 下载速度慢
- 尝试不同的镜像
- 使用 IDM 等多线程下载工具
- 在网络空闲时（如凌晨）下载

### 文件损坏
- 检查文件大小是否接近 334.7 MB
- 如果文件大小异常，重新下载

### 后端无法启动
- 检查 `app\backend\win\cuda\` 是否有 `sd-cuda.exe` 和 `stable-diffusion.dll`
- 查看浏览器控制台（F12）的错误信息
