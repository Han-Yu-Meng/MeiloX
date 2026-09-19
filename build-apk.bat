@echo off
set JAVA_HOME=C:\Program Files\Java\jdk-17
set PATH=%JAVA_HOME%\bin;%PATH%
cd /d D:\Workspace\MeiloX
call gradlew.bat assembleDebug
echo BUILD_EXIT_CODE=%ERRORLEVEL%
