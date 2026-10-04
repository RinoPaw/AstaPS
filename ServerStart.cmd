@echo off
setlocal

call gradlew.bat jar
if errorlevel 1 exit /b %errorlevel%

java -jar grasscutter.jar
