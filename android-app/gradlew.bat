@echo off
rem Gradle Wrapper for Windows
set DIR=%~dp0
set APP_BASE_NAME=%~n0
set APP_HOME=%DIR%

set DEFAULT_JVM_OPTS="-Xmx2048m" "-XX:MaxMetaspaceSize=512m"

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>1
if "%ERRORLEVEL%" == "0" goto execute

echo.
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.
exit /b 1

:execute
@rem Setup the command line
set CLASSPATH=%APP_HOME%gradle\wrapper\gradle-wrapper.jar

@rem Execute Gradle
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*

if "%ERRORLEVEL%"=="0" goto mainEnd

:fail
rem Set variable GRADLE_EXIT_CONSOLE if you need the _script_ return code instead of
rem the _cmd.exe /c_ return code!
set EXITION_CODE=%ERRORLEVEL%
goto mainEnd

:mainEnd
if "%OS%"=="Windows_NT" endlocal
exit /b %EXITION_CODE%
