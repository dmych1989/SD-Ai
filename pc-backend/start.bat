@echo off
chcp 65001 > nul
setlocal EnableDelayedExpansion

set "SCRIPT_DIR=%~dp0"
set "SETUP_SCRIPT=%SCRIPT_DIR%scripts\setup.ps1"
set "SERVE_SCRIPT=%SCRIPT_DIR%scripts\serve.cjs"
set "NODE_EXE=%SCRIPT_DIR%app\tools\node-win\node.exe"

echo ============================================================
echo  LOCAL AI IMAGE GENERATOR
echo ============================================================
echo.

REM Check if setup is needed
if not exist "%NODE_EXE%" (
    echo [setup] First-time setup required...
    echo [setup] Running setup.ps1...
    echo.
    powershell -ExecutionPolicy Bypass -NoProfile -File "%SETUP_SCRIPT%"
    if !ERRORLEVEL! NEQ 0 (
        echo.
        echo [error] Setup failed. Please check the error messages above.
        pause
        exit /b 1
    )
    echo.
)

echo [start] Starting Local AI Image Generator...
echo.

REM Start the server
"%NODE_EXE%" "%SERVE_SCRIPT%"

endlocal
