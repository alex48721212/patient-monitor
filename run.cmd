@echo off
rem Build the app and start it. Stop with Ctrl+C (or stop.cmd from another terminal).
rem Extra arguments are passed to the app, e.g.  run.cmd --spring.profiles.active=mysql
setlocal

rem Use JAVA_HOME if set, otherwise the JDK 21 that IntelliJ downloaded to %USERPROFILE%\.jdks
if not defined JAVA_HOME (
    for /d %%D in ("%USERPROFILE%\.jdks\*21*") do set "JAVA_HOME=%%D"
)
if not defined JAVA_HOME (
    echo JDK 21 not found. Install one via IntelliJ: File ^> Project Structure ^> SDK ^> Download JDK
    exit /b 1
)

echo Building...
call "%~dp0gradlew.bat" -q bootJar
if errorlevel 1 (
    echo Build failed.
    exit /b 1
)

rem Logs are UTF-8: switch this console to UTF-8 while the app runs, then restore it
for /f "tokens=2 delims=:" %%C in ('chcp') do set "OLD_CP=%%C"
chcp 65001 >nul

echo.
echo  Dashboard : http://localhost:8080
echo  Stop      : Ctrl+C
echo.
"%JAVA_HOME%\bin\java" -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -jar "%~dp0build\libs\patient-monitor-0.1.0.jar" %*

chcp %OLD_CP: =% >nul
