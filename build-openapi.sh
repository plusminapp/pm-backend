#!/usr/bin/env bash

echo pm-backend version: ${VERSION}
echo lcl_platform: ${LCL_PLATFORM}
echo platform: ${PLATFORM}

PROJECT_ROOT=${PROJECT_FOLDER}/pm-backend
API_URL="http://pm-backend-lcl:3045/api/v1/v3/api-docs"
OUT_DIR="src/main/resources/static"
JSON_FILE="${OUT_DIR}/PlusMin-api-docs.json"
YAML_FILE="${OUT_DIR}/PlusMin-api-docs.yaml"

pushd "${PROJECT_ROOT}" || exit 1

# ensure output dir exists
mkdir -p "${OUT_DIR}"

# ensure builder image exists
if ! docker image inspect plusmin/pm-backend-builder:latest > /dev/null 2>&1; then
    echo "Builder image not found, creating it..."
    ./build-builder.sh
fi

if docker ps --filter "name=pm-backend-lcl" --filter "status=running" --format '{{.Names}}' | grep -q '^pm-backend-lcl$'; then

  # 1) fetch api-docs JSON from the running lcl container (run inside builder container so hostname resolves)
  docker run --rm \
    -v "${PROJECT_ROOT}:/app" \
    -w /app \
    --name pm-backend-openapi-fetch \
    --network npm_default \
    plusmin/pm-backend-builder:latest \
    sh -c "curl -fsS '${API_URL}' -o '${JSON_FILE}'"

  if [ ! -s "${JSON_FILE}" ]; then
    echo "Fout: kon JSON niet ophalen of bestand leeg: ${JSON_FILE}"
    popd
    exit 2
  fi

  # 2) convert JSON -> YAML
    docker run --rm -v "${PROJECT_ROOT}:/workdir" --workdir /workdir mikefarah/yq eval -P "${JSON_FILE}" > "${YAML_FILE}"
    echo "Gegenereerd ${YAML_FILE} (via yq)"

else
  echo "pm-backend-lcl container is not running; skipping openapi generation"
fi

popd