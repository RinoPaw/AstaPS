@echo off
call .\gradlew jar -PskipHandbook=1 -PjarFilename=grasscutter
if errorlevel 1 exit /b %errorlevel%
pause
