@echo off

echo.
echo Rebuilding Docker images...
docker-compose build job_service

echo.
echo Starting containers...
docker-compose up -d job_service

if errorlevel 1 (
    echo Failed to start job_service
    exit /b 1
)
