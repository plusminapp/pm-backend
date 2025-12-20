#!/usr/bin/env bash

echo pm-backend version: ${VERSION}
echo lcl_platform: ${LCL_PLATFORM}
echo platform: ${PLATFORM}

pushd ${PROJECT_FOLDER}/pm-backend

# Check if builder image exists, if not create it
if ! docker image inspect plusmin/pm-backend-builder:latest > /dev/null 2>&1; then
    echo "Builder image not found, creating it..."
    ./build-builder.sh
fi

# Build the application using the multi-stage Dockerfile
docker build \
  --platform=$PLATFORM \
  --build-arg LCL_PLATFORM=${LCL_PLATFORM} \
  -t plusmin/pm-backend:${VERSION} .

if [ $? -eq 0 ]; then
    echo "Application built successfully!"
else
    echo "Application build FAILED!!!"
fi

popd