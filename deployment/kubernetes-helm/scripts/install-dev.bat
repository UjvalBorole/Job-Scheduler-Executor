@echo off
setlocal
set RELEASE_NAME=job-platform
set CHART_DIR=%~dp0..\chart

echo Installing or upgrading %RELEASE_NAME% from %CHART_DIR%
helm upgrade --install %RELEASE_NAME% "%CHART_DIR%" -f "%CHART_DIR%\values-dev.yaml" --wait --timeout 15m
if errorlevel 1 exit /b %errorlevel%

echo.
echo Deployment status:
kubectl get pods -n job-scheduler
kubectl get pods -n data
kubectl get pods -n observability
endlocal
