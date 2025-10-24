@echo off
echo Starting clean build...
call gradlew.bat clean
echo.
echo Running build...
call gradlew.bat build
echo.
echo Build complete!
pause


