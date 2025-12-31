#!/bin/bash
set -e

NAMESPACE="job-scheduler"

echo "🔁 Restarting all Service Applications in namespace: ${NAMESPACE}"

# -------------------------------
# Helper function
# -------------------------------
restart_deployment() {
  local DEPLOYMENT=$1
  echo "🔄 Restarting ${DEPLOYMENT}..."
  kubectl rollout restart deployment ${DEPLOYMENT} -n ${NAMESPACE}
  kubectl rollout status deployment ${DEPLOYMENT} -n ${NAMESPACE} --timeout=300s
}

# -------------------------------
# Step 1: Core Infra Services
# -------------------------------
restart_deployment discovery-service
restart_deployment config-server

# -------------------------------
# Step 2: Gateway
# -------------------------------
# restart_deployment api-gateway

# -------------------------------
# Step 3: Job Services
# -------------------------------
restart_deployment job-service
restart_deployment job-consumer
restart_deployment job-schedulersvc

# -------------------------------
# Step 4: Job Executors
# -------------------------------
# restart_deployment job-cron-executor
# restart_deployment job-manual-executor

# -------------------------------
# Step 5: Final Status Check
# -------------------------------
echo "⏳ Waiting for all pods to become Ready..."
kubectl wait --for=condition=Ready pod \
  -n ${NAMESPACE} \
  --all \
  --timeout=600s

echo "📋 Current status:"
kubectl get deploy,svc,pods -n ${NAMESPACE} -o wide

echo "🎉 All Service Applications restarted successfully!"
