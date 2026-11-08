@echo off
setlocal
cd /d "%~dp0"

echo =======================================================
echo   Erumi Native - Standalone Android APK Builder
echo =======================================================
echo.

set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo Building Erumi Native Debug APK...
echo.
call gradlew.bat assembleDebug --no-daemon

if %ERRORLEVEL% neq 0 (
    echo.
    echo [ERROR] Build failed! Check the error log above.
    exit /b %ERRORLEVEL%
)

echo.
echo =======================================================
echo   Build Successful!
echo   Debug APK: app\build\outputs\apk\debug\app-debug.apk
echo =======================================================
