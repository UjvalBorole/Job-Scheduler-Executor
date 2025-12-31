#!/bin/bash
set -e

# ===============================
# CONFIG
# ===============================
DOCKERHUB_USERNAME="boroleujval4"
VERSION="1.0.0"
PLATFORM="linux/amd64"

ROOT_DIR="$(pwd)"

# ===============================
# DOCKER CHECK
# ===============================
docker info > /dev/null 2>&1 || {
  echo "❌ Docker is not running"
  exit 1
}

docker login

# ===============================
# BUILD FUNCTION
# ===============================
build_and_push () {
  IMAGE=$1
  CONTEXT=$2

  FULL_IMAGE="${DOCKERHUB_USERNAME}/${IMAGE}:${VERSION}"

  echo "🔨 Building ${FULL_IMAGE}"
  docker build -t ${FULL_IMAGE} "${CONTEXT}"
  echo "✅ ${FULL_IMAGE} built"

  echo "📤 Pushing ${FULL_IMAGE}"
  docker push ${FULL_IMAGE}

  echo "✅ ${FULL_IMAGE} pushed"
}

# ===============================
# CORE SERVICES
# ===============================

build_and_push "job-apigateway" \
  "${ROOT_DIR}/Api Gateway/ApiGateway"

build_and_push "job-configserver" \
  "${ROOT_DIR}/Config Server/ConfigServer"

build_and_push "job-servicediscovery" \
  "${ROOT_DIR}/Service Discovery/ServiceDiscovery"

# ===============================
# JOB MANAGEMENT
# ===============================

build_and_push "job-service" \
  "${ROOT_DIR}/Job Management/JobService"

build_and_push "job-consumersvc" \
  "${ROOT_DIR}/Job Management/JobConsumerSvc"

# ===============================
# JOB SCHEDULING
# ===============================

build_and_push "job-schedulersvc" \
  "${ROOT_DIR}/Job Scheduling/watcher"



# ===============================
# JOB EXECUTION
# ===============================

build_and_push "job-cronexecutor" \
  "${ROOT_DIR}/Job Execution/executor1"

build_and_push "job-manualexecutor" \
  "${ROOT_DIR}/Job Execution/executor1 - Manual"

echo "🎉 ALL IMAGES BUILT & PUSHED SUCCESSFULLY"
