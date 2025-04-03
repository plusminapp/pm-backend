#!/usr/bin/env bash

echo pm-backend version: ${VERSION}
echo pm-backend platform: ${PLATFORM}

pushd ${PROJECT_FOLDER}/pm-backend
mvn clean

if mvn package; then
  docker build \
    --platform=$PLATFORM \
    --build-arg JAR_FILE=./target/pm-backend-${VERSION}.jar \
    -t plusmin/pm-backend:${VERSION} .
else
  echo mvn clean package FAILED!!!
fi

popd