@echo off
title AetherMind - Build
cd /d "%~dp0"

if defined JAVA_HOME if not exist "%JAVA_HOME%\bin\javac.exe" set "JAVA_HOME="
if not defined JAVA_HOME set "VSCODE_JDK=%USERPROFILE%\.vscode\extensions\redhat.java-1.56.0-win32-x64\jre\21.0.12.1-win32-x86_64\bin"
if not defined JAVA_HOME if exist "%VSCODE_JDK%\javac.exe" for %%I in ("%VSCODE_JDK%\..") do set "JAVA_HOME=%%~fI"
if defined JAVA_HOME set "PATH=%JAVA_HOME%\bin;%PATH%"

where mvn >nul 2>&1
if errorlevel 1 (
    echo  Maven is required to build the Java Playwright dependency.
    echo  Install Maven and a JDK 11 or newer, then run build.bat again.
    exit /b 1
)

where javac >nul 2>&1
if errorlevel 1 (
    echo  A JDK with javac is required to build. Set JAVA_HOME or install a JDK.
    exit /b 1
)

echo.
echo  [1/3] Syncing knowledge.json...
copy /Y shared\knowledge.json web\knowledge.json >nul

echo  [2/3] Building the desktop application and dependencies...
call mvn -q -DskipTests package
if errorlevel 1 (
    echo  ERROR: Maven build failed.
    pause
    exit /b 1
)

echo  [3/3] Copying the runnable JAR...
copy /Y target\AetherMind.jar AetherMind.jar >nul
if errorlevel 1 (
    echo  ERROR: Could not copy the runnable JAR.
    pause
    exit /b 1
)

echo  Done. Run: java -jar AetherMind.jar
pause
