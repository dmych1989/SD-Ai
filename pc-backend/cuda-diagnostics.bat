@echo off
title Local AI Image Generator - Advanced CUDA Diagnostics
cd /d "%~dp0"

echo.
echo ============================================================
 LOCAL AI Image Generator - Advanced CUDA Diagnostics
============================================================
echo.
echo Checking detailed CUDA compatibility information...
echo.

set APP=%~dp0app
set CUDA_BACKEND=%APP%\backend\win\cuda\sd-cuda.exe
set CUDA_DLL=%APP%\backend\win\cuda\stable-diffusion.dll

echo [INFO] Checking system CUDA environment...
echo.

:: Check Windows version
for /f "tokens=4-5 delims=. " %%i in ('ver') do (
    set win_version=%%i.%%j
)
echo Windows Version: %win_version%

:: Check GPU information
echo.
echo [INFO] Checking GPU hardware...
powershell -Command "Get-CimInstance Win32_VideoController | Select-Object Name, AdapterRAM, DriverVersion" | findstr /v "Caption" | findstr /v "Name" | findstr /v "DriverVersion"

:: Check NVIDIA tools
echo.
echo [INFO] Checking NVIDIA tools availability...
if exist "C:\Program Files\NVIDIA Corporation\NVSMI\nvidia-smi.exe" (
    echo [OK] nvidia-smi found at: C:\Program Files\NVIDIA Corporation\NVSMI\nvidia-smi.exe
    echo [INFO] Running nvidia-smi to get GPU details...
    "C:\Program Files\NVIDIA Corporation\NVSMI\nvidia-smi.exe" --query-gpu=driver_version,name,memory.total --format=csv,noheader,nounits
) else (
    echo [WARN] nvidia-smi not found
)

:: Check CUDA runtime DLLs
echo.
echo [INFO] Checking CUDA runtime DLLs in system...
if exist "C:\Windows\System32\nvcuda.dll" (
    echo [OK] CUDA runtime DLL found in system32
    for /f "tokens=*" %%i in ('powershell -Command "(Get-Item 'C:\Windows\System32\nvcuda.dll').VersionInfo.FileVersion"') do set cuda_system_version=%%i
    echo System CUDA Version: %cuda_system_version%
) else (
    echo [ERROR] CUDA runtime DLL not found in system32
    echo [ERROR] This means CUDA is not properly installed
    goto :final_analysis
)

:: Check project-specific CUDA files
echo.
echo [INFO] Checking project CUDA installation...
if exist "%CUDA_BACKEND%" (
    echo [OK] Project CUDA backend executable: %CUDA_BACKEND%
    echo [INFO] Checking backend executable details...
    dir "%CUDA_BACKEND%" | findstr /i "exe"
) else (
    echo [WARN] Project CUDA backend not found
)

if exist "%CUDA_DLL%" (
    echo [OK] Project CUDA runtime library: %CUDA_DLL%
    echo [INFO] Checking library details...
    dir "%CUDA_DLL%" | findstr /i "dll"
) else (
    echo [WARN] Project CUDA runtime library not found
)

:: Check required CUDA runtime DLLs in project
echo.
echo [INFO] Checking required CUDA runtime DLLs in project backend...
set CUBLAS_DLL=%APP%\backend\win\cuda\cublas64_12.dll
set CUBLASLT_DLL=%APP%\backend\win\cuda\cublasLt64_12.dll
set CUDART_DLL=%APP%\backend\win\cuda\cudart64_12.dll

set project_dll_status=0

if exist "%CUBLAS_DLL%" (
    echo [OK] cuBLAS 12.0 library present
    for /f "tokens=*" %%i in ('powershell -Command "(Get-Item '%CUBLAS_DLL%').VersionInfo.FileVersion"') do set cublas_version=%%i
    echo cuBLAS Version: %cublas_version%
) else (
    echo [ERROR] cuBLAS 12.0 library missing
    set /a project_dll_status+=1
)

if exist "%CUBLASLT_DLL%" (
    echo [OK] cuBLASLt 12.0 library present
    for /f "tokens=*" %%i in ('powershell -Command "(Get-Item '%CUBLASLT_DLL%').VersionInfo.FileVersion"') do set cublaslt_version=%%i
    echo cuBLASLt Version: %cublaslt_version%
) else (
    echo [ERROR] cuBLASLt 12.0 library missing
    set /a project_dll_status+=1
)

if exist "%CUDART_DLL%" (
    echo [OK] CUDA Runtime 12.0 library present
    for /f "tokens=*" %%i in ('powershell -Command "(Get-Item '%CUDART_DLL%').VersionInfo.FileVersion"') do set cudart_version=%%i
    echo CUDA Runtime Version: %cudart_version%
) else (
    echo [ERROR] CUDA Runtime 12.0 library missing
    set /a project_dll_status+=1
)

echo.
echo [INFO] Project CUDA DLL check complete. %project_dll_status% files missing.

:: Check for potential conflicts
echo.
echo [INFO] Checking for potential conflicts...
if exist "%APP%\backend\win\vulkan\sd-vulkan.exe" (
    echo [OK] Vulkan backend available as fallback
) else (
    echo [WARN] No Vulkan backend available for fallback
)

:final_analysis
echo.
echo ============================================================
 DETAILED ANALYSIS COMPLETE
============================================================
echo.
echo If you have CUDA 12.x but still experience crashes, the issues might be:

echo 1. [POSSIBLE] Driver incompatibility
echo    - Try updating NVIDIA drivers to latest version
echo    - Use DDU to clean old drivers and reinstall

echo 2. [POSSIBLE] System instability
echo    - Check system memory (RAM) for errors
echo    - Verify GPU memory is not overheating

echo 3. [POSSIBLE] Project installation issue
echo    - Run setup.ps1 to reinstall CUDA backend
echo    - Try safe-start.bat to test without GPU acceleration

echo.
echo Recommended next steps:
echo 1. Try safe-start.bat first to confirm CUDA is the issue
echo 2. If safe-start works, update NVIDIA drivers
echo 3. If still crashing, run full setup again with latest drivers

echo.
echo Press any key to open the recommended safe-start tool...
pause >nul

call safe-start.bat