@echo off
echo Shutting down existing containers and volumes...
docker-compose down -v --remove-orphans

if errorlevel 1 (
    echo Failed to shut down containers.
    exit /b 1
)

echo.
echo Rebuilding Docker images...
docker-compose build postgres pgadmin 

if errorlevel 1 (
    echo Build failed for postgres or pgadmin.
    exit /b 1
)

echo.
echo Starting containers...
docker-compose up -d postgres pgadmin

if errorlevel 1 (
    echo Failed to start postgres or pgadmin.
    exit /b 1
)
