@echo off
setlocal

rem %~dp0 ends with a backslash, so use pushd instead of cd /d "%~dp0"
pushd "%~dp0"
if errorlevel 1 (
    echo [ERROR] Cannot enter project directory: %~dp0
    pause
    exit /b 1
)

echo ================================================
echo  Classroom AI backend (firestDemo)
echo  Working dir: %CD%
echo  URL: http://localhost:8080/health
echo  Press Ctrl+C to stop.
echo ================================================
echo.

rem Pass --server.port explicitly so that any SERVER_PORT / SERVER__PORT
rem environment variable on this machine cannot override the port.
java -jar target\firestDemo-0.0.1-SNAPSHOT.jar --server.port=8080
set CODE=%ERRORLEVEL%

echo.
echo Server exited. ErrorLevel = %CODE%
if not "%CODE%"=="0" (
    echo Start failed. Check that port 8080 is free and Java 17 is installed.
)

popd
pause
