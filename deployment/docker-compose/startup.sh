#!/bin/bash

# ------------------------------------------
# Smart Docker Startup Script
# ------------------------------------------

# Function to get image digests from docker-compose
get_digests() {
    docker-compose -f "$1" config | \
    awk '/image:/ {print $2}' | \
    xargs -n1 docker inspect --format '{{.Id}}'
}

# 1. Pull and start infrastructure services
echo "Starting infrastructure..."
docker-compose -f infra.yml pull
docker-compose -f infra.yml up -d

# 2. Pull monitoring images and check for changes
echo "Checking monitoring services..."
old_digests=$(get_digests monitoring.yml)

docker-compose -f monitoring.yml pull
new_digests=$(get_digests monitoring.yml)

if [ "$old_digests" != "$new_digests" ]; then
    echo "Monitoring images updated. Restarting monitoring services..."
    docker-compose -f monitoring.yml up -d
else
    echo "No changes in monitoring images. Skipping restart."
fi

# 3. Start application services
echo "Starting application services..."
docker-compose -f apps.yml up -d

echo "All services started successfully!"
