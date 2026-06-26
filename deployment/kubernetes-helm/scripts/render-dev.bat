@echo off
setlocal
set RELEASE_NAME=job-platform
set CHART_DIR=%~dp0..\chart
set OUTPUT_FILE=%~dp0..\rendered-dev.yaml

echo Rendering %RELEASE_NAME% to %OUTPUT_FILE%
helm template %RELEASE_NAME% "%CHART_DIR%" -f "%CHART_DIR%\values-dev.yaml" > "%OUTPUT_FILE%"
if errorlevel 1 exit /b %errorlevel%

echo Rendered file created: %OUTPUT_FILE%
endlocal
