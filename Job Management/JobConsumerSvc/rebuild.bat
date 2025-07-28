@echo off

echo.
echo Rebuilding Docker images...
docker-compose build job_consumer_svc

echo.
echo Starting containers...
docker-compose up -d job_consumer_svc

if errorlevel 1 (
    echo Failed to start job_consumer_svc
    exit /b 1
)
