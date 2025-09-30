#!/bin/sh

# Wait for host:port to be available
host="$1"
port="$2"
shift 2  # Remove host and port from arguments
cmd="$@"

echo "Waiting for $host:$port..."

while ! nc -z "$host" "$port"; do
  sleep 1
done

echo "$host:$port is up!"

# Execute the command
exec $cmd