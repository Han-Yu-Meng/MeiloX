@echo off
REM Local release APK (same path as GitHub Actions assembleRelease)
set JAVA_HOME=C:\Program Files\Java\jdk-17
if exist "C:\Program Files\Java\jdk-21\bin\java.exe" set JAVA_HOME=C:\Program Files\Java\jdk-21
set PATH=%JAVA_HOME%\bin;%PATH%
cd /d D:\Workspace\MeiloX
echo Using JAVA_HOME=%JAVA_HOME%
call gradlew.bat clean assembleRelease --no-daemon
echo EXIT=%ERRORLEVEL%
dir /b app\build\outputs\apk\release\*.apk 2>nul
