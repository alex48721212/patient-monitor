@echo off
rem Start the app. Uses JAVA_HOME if set, otherwise the JDK 21 that IntelliJ downloaded to %USERPROFILE%\.jdks
setlocal
if not defined JAVA_HOME (
    for /d %%D in ("%USERPROFILE%\.jdks\*21*") do set "JAVA_HOME=%%D"
)
if not defined JAVA_HOME (
    echo JDK 21 not found. Install one via IntelliJ: File ^> Project Structure ^> SDK ^> Download JDK
    exit /b 1
)
echo Using JAVA_HOME=%JAVA_HOME%
echo Dashboard: http://localhost:8080
call "%~dp0gradlew.bat" bootRun %*
