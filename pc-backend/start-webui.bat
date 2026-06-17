@echo off
chcp 65001 > nul
title SdAi WebUI

echo ========================================
echo   SdAi WebUI - 浏览器出图界面
echo   依赖: 后端必须在 1420 端口跑着
echo   访问: http://127.0.0.1:1421/webui.html
echo   关闭: 关掉这个窗口
echo ========================================
echo.

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-webui.ps1"

pause
