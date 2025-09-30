@echo off
echo Shutting down existing containers and volumes...
docker-compose down -v --remove-orphans

if errorlevel 1 (
    echo Failed to shut down containers.
    exit /b 1
)

echo.
echo Rebuilding Docker images...
docker-compose build kafka zookeeper redis

if errorlevel 1 (
    echo Build failed.
    exit /b 1
)

echo.
echo Starting containers...
docker-compose up -d kafka zookeeper redis

if errorlevel 1 (
    echo Failed to start containers.
    exit /b 1
)
