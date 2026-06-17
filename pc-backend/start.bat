@echo off
chcp 65001 > nul
title SdAiBackend - SD-Ai PC Backend

REM ====== 配置区（按需改）======
set SD_MODEL_DIR=D:\GitHub\Local-AI-Image-Generator\app\models
set SD_OUTPUT_DIR=D:\GitHub\Local-AI-Image-Generator\app\outputs
REM ==============================

if not exist "%SD_MODEL_DIR%" (
    echo [WARN] 模型目录不存在: %SD_MODEL_DIR%
    echo        请修改本脚本里的 SD_MODEL_DIR 后重试
    pause
    exit /b 1
)

if not exist "%SD_OUTPUT_DIR%" mkdir "%SD_OUTPUT_DIR%"

echo ========================================
echo   SdAiBackend - C# AOT 出图后端
echo   监听: http://0.0.0.0:1420
echo   模型: %SD_MODEL_DIR%
echo   输出: %SD_OUTPUT_DIR%
echo   关闭: 直接关这个窗口，或 Ctrl+C
echo ========================================
echo.

SdAiBackend.exe

pause
