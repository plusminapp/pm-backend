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

if docker ps --filter "name=pm-backend-lcl" --filter "status=running" --format '{{.Names}}' | grep -q '^pm-backend-lcl$'; then
  docker run --rm \
    -v ${PROJECT_FOLDER}/pm-backend:/app \
    -v maven-cache:/root/.m2/repository \
    -w /app \
    --name pm-backend-builder \
    --network npm_default \
    plusmin/pm-backend-builder:latest \
    mvn -DapiDocsUrl=http://pm-backend-lcl:3045/api/v1/v3/api-docs \
      -DoutputDir=src/main/resources/static \
      springdoc-openapi:generate
else
  echo "pm-backend-lcl container is not running; skipping openapi generation"
fi

popd