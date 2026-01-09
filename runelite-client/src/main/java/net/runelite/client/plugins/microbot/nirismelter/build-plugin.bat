@echo off
REM Build nirismelter plugin as standalone JAR

echo Building nirismelter plugin JAR...

REM Set paths
set SCRIPT_DIR=%~dp0
set PROJECT_ROOT=%SCRIPT_DIR%..\..\..\..\..\..\..\..\..\..
set PLUGIN_DIR=%SCRIPT_DIR%
set OUTPUT_DIR=%SCRIPT_DIR%build
set CLASSES_DIR=%OUTPUT_DIR%\classes
set JAR_NAME=nirismelter-plugin-1.0.0.jar

REM Clean and create directories
if exist "%OUTPUT_DIR%" rmdir /s /q "%OUTPUT_DIR%"
mkdir "%CLASSES_DIR%"

REM Compile Java files (using parent project's compiled dependencies)
echo Compiling Java sources...
cd "%PLUGIN_DIR%"

REM Use the parent build output for classpath
set CLASSPATH=%PROJECT_ROOT%\runelite-client\build\classes\java\main;%PROJECT_ROOT%\runelite-api\build\classes\java\main;%PROJECT_ROOT%\cache\build\classes\java\main

javac -cp "%CLASSPATH%" -d "%CLASSES_DIR%" *.java

if %ERRORLEVEL% NEQ 0 (
    echo Compilation failed!
    exit /b 1
)

REM Create JAR
echo Creating JAR file...
cd "%CLASSES_DIR%"
jar cvf "%OUTPUT_DIR%\%JAR_NAME%" net/runelite/client/plugins/microbot/nirismelter/*.class

echo.
echo Build complete! JAR created at:
echo %OUTPUT_DIR%\%JAR_NAME%
echo.
pause
