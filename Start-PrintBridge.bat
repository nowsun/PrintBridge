@echo off
setlocal

chcp 65001 >nul

set "JAR_PATH=%~dp0printbridge-2.1.3.jar"
if not exist "%JAR_PATH%" set "JAR_PATH=%~dp0target\printbridge-2.1.3.jar"

if not exist "%JAR_PATH%" (
    echo PrintBridge JAR was not found.
    echo Place printbridge-2.1.3.jar beside this batch file or in the target folder.
    pause
    exit /b 1
)

where java.exe >nul 2>&1
if errorlevel 1 (
    echo Java was not found. Install Java 8 or later and add java.exe to PATH.
    pause
    exit /b 1
)

echo Starting PrintBridge...
java -Dfile.encoding=UTF-8 -jar "%JAR_PATH%"

echo.
echo PrintBridge has exited. Press any key to close this window.
pause >nul
endlocal
