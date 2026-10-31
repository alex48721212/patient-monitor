@echo off
rem Stop whatever is serving on port 8080 (use when the terminal running the app was closed or lost).
setlocal
set "FOUND="
for /f "tokens=5" %%P in ('netstat -ano ^| findstr /r /c:":8080 .*LISTENING"') do (
    if not defined FOUND (
        set "FOUND=1"
        echo Stopping process %%P on port 8080...
        taskkill /PID %%P /F >nul
    )
)
if defined FOUND (
    echo Stopped.
) else (
    echo Nothing is running on port 8080.
)
