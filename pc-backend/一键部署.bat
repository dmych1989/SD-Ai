@echo off
title Local AI Image Generator - 一键部署工具
cd /d "%~dp0"

echo.
echo ============================================================
 Local AI Image Generator - 一键部署工具
============================================================
echo.
echo 此工具将自动下载所有必需的文件
echo.

:: 设置变量
set APP=%~dp0app
set TOOLS_DIR=%APP%\tools
set BACKEND_DIR=%APP%\backend\win
set MODELS_DIR=%APP%\models

:: 创建必要目录
mkdir "%TOOLS_DIR%" 2>nul
mkdir "%BACKEND_DIR%\cuda" 2>nul
mkdir "%BACKEND_DIR%\vulkan" 2>nul
mkdir "%MODELS_DIR%" 2>nul

echo [1/4] 下载 CUDA 后端...
if not exist "%BACKEND_DIR%\cuda\sd-cuda.exe" (
    echo 正在下载 CUDA 后端 (~335MB)...
    curl -L -o "%TEMP%\sd-cuda.zip" "https://github.com/leejet/stable-diffusion.cpp/releases/download/master-669-2d40a8b/sd-master-2d40a8b-bin-win-cuda12-x64.zip"
    if exist "%TEMP%\sd-cuda.zip" (
        tar -xf "%TEMP%\sd-cuda.zip" -C "%TEMP%\"
        if exist "%TEMP%\bin\sd-server.exe" copy "%TEMP%\bin\sd-server.exe" "%BACKEND_DIR%\cuda\sd-cuda.exe"
        if exist "%TEMP%\sd-server.exe" copy "%TEMP%\sd-server.exe" "%BACKEND_DIR%\cuda\sd-cuda.exe"
        if exist "%TEMP%\bin\stable-diffusion.dll" copy "%TEMP%\bin\stable-diffusion.dll" "%BACKEND_DIR%\cuda\"
        del "%TEMP%\sd-cuda.zip"
        echo CUDA 后端下载完成！
    ) else (
        echo CUDA 后端下载失败，请手动下载
    )
) else (
    echo CUDA 后端已存在，跳过下载
)

echo.
echo [2/4] 下载 Vulkan 后端...
if not exist "%BACKEND_DIR%\vulkan\sd-vulkan.exe" (
    echo 正在下载 Vulkan 后端 (~335MB)...
    curl -L -o "%TEMP%\sd-vulkan.zip" "https://github.com/leejet/stable-diffusion.cpp/releases/download/master-669-2d40a8b/sd-master-2d40a8b-bin-win-vulkan-x64.zip"
    if exist "%TEMP%\sd-vulkan.zip" (
        tar -xf "%TEMP%\sd-vulkan.zip" -C "%TEMP%\"
        if exist "%TEMP%\bin\sd-server.exe" copy "%TEMP%\bin\sd-server.exe" "%BACKEND_DIR%\vulkan\sd-vulkan.exe"
        if exist "%TEMP%\sd-server.exe" copy "%TEMP%\sd-server.exe" "%BACKEND_DIR%\vulkan\sd-vulkan.exe"
        if exist "%TEMP%\bin\stable-diffusion.dll" copy "%TEMP%\bin\stable-diffusion.dll" "%BACKEND_DIR%\vulkan\"
        del "%TEMP%\sd-vulkan.zip"
        echo Vulkan 后端下载完成！
    ) else (
        echo Vulkan 后端下载失败，请手动下载
    )
) else (
    echo Vulkan 后端已存在，跳过下载
)

echo.
echo [3/4] 下载推荐模型...
echo 正在下载 DreamShaper 8 轻量模型 (2.1GB)...
if not exist "%MODELS_DIR%\DreamShaper_8_pruned.safetensors" (
    curl -L -o "%MODELS_DIR%\DreamShaper_8_pruned.safetensors" "https://huggingface.co/Lykon/DreamShaper/resolve/main/DreamShaper_8_pruned.safetensors"
    echo 轻量模型下载完成！
) else (
    echo 轻量模型已存在，跳过下载
)

echo.
echo [4/4] 验证安装...
set ALL_READY=1

if not exist "%BACKEND_DIR%\cuda\sd-cuda.exe" (
    echo [错误] CUDA 后端缺失
    set ALL_READY=0
)

if not exist "%BACKEND_DIR%\vulkan\sd-vulkan.exe" (
    echo [错误] Vulkan 后端缺失
    set ALL_READY=0
)

if not exist "%MODELS_DIR%\DreamShaper_8_pruned.safetensors" (
    echo [警告] 建议至少下载一个模型
)

echo.
echo ============================================================
 部署完成检查
============================================================
if %ALL_READY%==1 (
    echo [✓] 所有核心组件已就绪
    echo [✓] CUDA 后端: 已安装
    echo [✓] Vulkan 后端: 已安装  
    echo [✓] 轻量模型: 已安装
    echo.
    echo 现在可以运行 start.bat 启动应用！
) else (
    echo [⚠] 部分组件缺失，请检查下载
    echo 手动下载地址已保存在下方...
)

echo.
echo ============================================================
 手动下载备用地址
============================================================
echo.
echo CUDA 后端: https://github.com/leejet/stable-diffusion.cpp/releases/download/master-669-2d40a8b/sd-master-2d40a8b-bin-win-cuda12-x64.zip
echo.
echo Vulkan 后端: https://github.com/leejet/stable-diffusion.cpp/releases/download/master-669-2d40a8b/sd-master-2d40a8b-bin-win-vulkan-x64.zip
echo.
echo 轻量模型 DreamShaper 8: https://huggingface.co/Lykon/DreamShaper/resolve/main/DreamShaper_8_pruned.safetensors
echo.
echo 高质量模型 Juggernaut XL: https://huggingface.co/RunDiffusion/Juggernaut-XL-Lightning/resolve/main/Juggernaut_RunDiffusionPhoto2_Lightning_4Steps.safetensors
echo.
echo 国内镜像站: https://hf-mirror.co/
echo.
echo Press any key to exit...
pause >nul