#!/usr/bin/env bash

echo "Building pm-backend builder image..."

# Build the builder image first
docker build -f Dockerfile.builder -t plusmin/pm-backend-builder:latest .

if [ $? -eq 0 ]; then
    echo "Builder image created successfully!"
else
    echo "Failed to build builder image!"
    exit 1
fi