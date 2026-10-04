@echo off
if not exist grasscutter.jar (
    echo grasscutter.jar was not found. Run gradlew-jar.bat first.
    exit /b 1
)
java -jar grasscutter.jar
pause
