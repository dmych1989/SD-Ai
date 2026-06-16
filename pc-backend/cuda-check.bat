@echo off
title Local AI Image Generator - CUDA Compatibility Check
cd /d "%~dp0"

echo.
echo ============================================================
 LOCAL AI IMAGE GENERATOR - CUDA COMPATIBILITY CHECK
============================================================
echo.

:: Check for GPU and CUDA drivers
echo Step 1: Checking GPU hardware...
echo.

echo Checking for NVIDIA GPUs:
if exist "C:\Program Files\NVIDIA Corporation\NVSMI\nvidia-smi.exe" (
    echo [OK] NVIDIA GPU detected via NVSMI path
) else (
    echo [INFO] NVIDIA GPU not found via NVSMI path
)

echo.
echo Checking for NVIDIA drivers...
if exist "C:\Windows\System32\nvcuda.dll" (
    echo [OK] NVIDIA CUDA runtime DLL found
) else (
    echo [ERROR] NVIDIA CUDA runtime DLL not found - no GPU support possible
    goto :manual_install
)

echo.
echo Step 2: Checking CUDA version compatibility...
echo.

:: Check CUDA version from DLL files
if exist "C:\Windows\System32\nvcuda.dll" (
    for /f "tokens=*" %%i in ('powershell -Command "(Get-Item 'C:\Windows\System32\nvcuda.dll').VersionInfo.FileVersion"') do set cuda_version=%%i
    echo Detected CUDA Version: %cuda_version%
    echo.
    
    if "%cuda_version:~0,2%"=="12" (
        echo [OK] CUDA version 12.x detected - compatible with the project
    ) else if "%cuda_version:~0,2%"=="11" (
        echo [WARN] CUDA version 11.x detected - may have compatibility issues
        echo [INFO] The project expects CUDA 12.x, but 11.x might work
    ) else (
        echo [ERROR] Unsupported CUDA version: %cuda_version%
        echo [INFO] Expected CUDA 12.x for optimal compatibility
    )
)

echo.
echo Step 3: Checking existing installation...
set APP=%~dp0app
set CUDA_BACKEND=%APP%\backend\win\cuda\sd-cuda.exe
set CUDA_DLL=%APP%\backend\win\cuda\stable-diffusion.dll

if exist "%CUDA_BACKEND%" (
    echo [OK] CUDA backend executable exists
) else (
    echo [INFO] CUDA backend not found - will be installed
)

if exist "%CUDA_DLL%" (
    echo [OK] CUDA runtime library exists
) else (
    echo [INFO] CUDA runtime library not found - will be installed
)

echo.
echo Step 4: Checking CUDA runtime DLLs in app...
set CUBLAS_DLL=%APP%\backend\win\cuda\cublas64_12.dll
set CUBLASLT_DLL=%APP%\backend\win\cuda\cublasLt64_12.dll
set CUDART_DLL=%APP%\backend\win\cuda\cudart64_12.dll

set missing_dlls=0

if exist "%CUBLAS_DLL%" (
    echo [OK] cuBLAS 12.0 library present
) else (
    echo [ERROR] cuBLAS 12.0 library missing
    set /a missing_dlls+=1
)

if exist "%CUBLASLT_DLL%" (
    echo [OK] cuBLASLt 12.0 library present
) else (
    echo [ERROR] cuBLASLt 12.0 library missing
    set /a missing_dlls+=1
)

if exist "%CUDART_DLL%" (
    echo [OK] CUDA Runtime 12.0 library present
) else (
    echo [ERROR] CUDA Runtime 12.0 library missing
    set /a missing_dlls+=1
)

echo.
if %missing_dlls% gtr 0 (
    echo [WARN] %missing_dlls% CUDA runtime DLLs are missing
    echo This could cause startup issues or crashes
    goto :manual_install
) else (
    echo [OK] All required CUDA runtime DLLs are present
    echo.
    echo Project should start without CUDA compatibility issues
    goto :test_run
)

:manual_install
echo.
echo ============================================================
 MANUAL CUDA INSTALLATION GUIDE
============================================================
echo.
echo If the project crashes due to CUDA issues, try these solutions:
echo.
echo 1. Download CUDA 12.x runtime DLLs:
echo    https://github.com/ggml-org/llama.cpp/releases/download/b9509/cudart-llama-bin-win-cuda-12.4-x64.zip
echo.
echo 2. Extract DLLs to: app\backend\win\cuda\
echo    - cublas64_12.dll
echo    - cublasLt64_12.dll
echo    - cudart64_12.dll
echo.
echo 3. Or install full CUDA Toolkit 12.x:
echo    https://developer.nvidia.com/cuda-toolkit-archive
echo.
echo 4. Alternative: Use CPU mode by removing app/backend/win/cuda/
echo.
echo Press any key to check CPU mode availability...
pause >nul

:cpu_mode_check
set CPU_BACKEND=%APP%\backend\win\vulkan\sd-vulkan.exe
if exist "%CPU_BACKEND%" (
    echo [OK] Vulkan backend available for CPU fallback
    echo You can try removing CUDA backend and using Vulkan instead
) else (
    echo [INFO] Neither CUDA nor Vulkan backend is available
    echo Complete setup may be needed
)

:test_run
echo.
echo ============================================================
 RECOMMENDED NEXT STEPS
============================================================
echo.
echo 1. First, try running CPU/Vulkan mode (remove CUDA folder):
echo    rd /s /q "app\backend\win\cuda" 2>nul
echo    Then run start.bat
echo.
echo 2. If CPU mode works, CUDA driver issue is confirmed
echo 3. If CPU mode also crashes, check system requirements:
echo    - Windows 10/11 64-bit
echo    - 8GB+ RAM (16GB+ recommended)
echo    - 2GB+ available disk space
echo.
echo Press any key to return to main menu...
pause >nul

call start.bat