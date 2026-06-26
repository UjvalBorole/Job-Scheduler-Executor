#!/bin/bash
set -e

NAMESPACE="job-scheduler"
BASE_DIR="."
# kubectl rollout restart deployment job-servicediscovery -n job-scheduler

echo "🚀 Deploying Service Applications into namespace: ${NAMESPACE}"

# -------------------------------
# Step 0: Shared PVC (if needed first)
# -------------------------------
# if [ -f "${BASE_DIR}/ServiceApp/share-pvc.yaml" ]; then
#   echo "📦 Applying shared PVC..."
#   kubectl apply -f "${BASE_DIR}/ServiceApp/share-pvc.yaml" -n ${NAMESPACE}
# fi

# -------------------------------
# Step 1: Service Discovery
# -------------------------------
echo "🔍 Deploying Service Discovery..."
kubectl apply -f "${BASE_DIR}/ServiceApp/service-discovery/sd-Deployment.yaml" -n ${NAMESPACE}

# -------------------------------
# Step 2: Config Server
# -------------------------------
echo "⚙️ Deploying Config Server..."
kubectl apply -f "${BASE_DIR}/ServiceApp/config-server/cs-Deployment.yaml" -n ${NAMESPACE}


# -------------------------------
# Step 3: API Gateway
# -------------------------------
# echo "🌐 Deploying API Gateway..."
# kubectl apply -f "${BASE_DIR}/ServiceApp/api-gateway/apigateway-Deployment.yaml" -n ${NAMESPACE}
# kubectl apply -f "${BASE_DIR}/ServiceApp/api-gateway/apigateway-Service.yaml" -n ${NAMESPACE}



# -------------------------------
# Step 4: Job Service
# -------------------------------
echo "🛠️ Deploying Job Service..."
kubectl apply -f "${BASE_DIR}/ServiceApp/job-service/jobservice-ConfigMap.yaml" -n ${NAMESPACE}
kubectl apply -f "${BASE_DIR}/ServiceApp/job-service/jobservice-Deployment.yaml" -n ${NAMESPACE}
kubectl apply -f "${BASE_DIR}/ServiceApp/job-service/jobservice-Service.yaml" -n ${NAMESPACE}
kubectl apply -f "${BASE_DIR}/ServiceApp/job-service/jobservice-HPA.yaml" -n ${NAMESPACE}

# -------------------------------
# Step 5: Job Consumer
# -------------------------------
echo "📥 Deploying Job Consumer..."
kubectl apply -f "${BASE_DIR}/ServiceApp/job-consumer/jobconsumer-Secret.yaml" -n ${NAMESPACE}
kubectl apply -f "${BASE_DIR}/ServiceApp/job-consumer/jobconsumer-ConfigMap.yaml" -n ${NAMESPACE}
kubectl apply -f "${BASE_DIR}/ServiceApp/job-consumer/jobconsumer-Deployment.yaml" -n ${NAMESPACE}
kubectl apply -f "${BASE_DIR}/ServiceApp/job-consumer/jobconsumer-Service.yaml" -n ${NAMESPACE}
kubectl apply -f "${BASE_DIR}/ServiceApp/job-consumer/jobconsumer-HPA.yaml" -n ${NAMESPACE}

# -------------------------------
# Step 6: Job Scheduler
# -------------------------------
echo "🗓️ Deploying Job Scheduler..."
kubectl apply -f "${BASE_DIR}/ServiceApp/job-scheduler/jobscheduler-ConfigMap.yaml" -n ${NAMESPACE}
kubectl apply -f "${BASE_DIR}/ServiceApp/job-scheduler/jobscheduler-Deployment.yaml" -n ${NAMESPACE}
kubectl apply -f "${BASE_DIR}/ServiceApp/job-scheduler/jobscheduler-Service.yaml" -n ${NAMESPACE}

# -------------------------------
# Step 7: Job Executor (Cron)
# -------------------------------
echo "⏱️ Deploying Job Executor (Cron)..."
kubectl apply -f "${BASE_DIR}/ServiceApp/job-executor/jobexecutor-ConfigMap.yaml" -n ${NAMESPACE}
kubectl apply -f "${BASE_DIR}/ServiceApp/job-executor/jobexecutor-cron-Deployment.yaml" -n ${NAMESPACE}
kubectl apply -f "${BASE_DIR}/ServiceApp/job-executor/jobexecutor-cron-Service.yaml" -n ${NAMESPACE}
kubectl apply -f "${BASE_DIR}/ServiceApp/job-executor/jobexecutor-cron-HPA.yaml" -n ${NAMESPACE}

# -------------------------------
# Step 8: Job Executor (Manual)
# -------------------------------
# echo "🖐️ Deploying Job Executor (Manual)..."
# kubectl apply -f "${BASE_DIR}/ServiceApp/job-executor/jobexecutor-manual-Deployment.yaml" -n ${NAMESPACE}
# kubectl apply -f "${BASE_DIR}/ServiceApp/job-executor/jobexecutor-manual-Service.yaml" -n ${NAMESPACE}
# kubectl apply -f "${BASE_DIR}/ServiceApp/job-executor/jobexecutor-manual-HPA.yaml" -n ${NAMESPACE}

# -------------------------------
# Step 9: Wait for Readiness
# -------------------------------
echo "⏳ Waiting for all service pods to be Ready..."
kubectl wait --for=condition=Ready pod \
  -n ${NAMESPACE} \
  --all \
  --timeout=600s

# -------------------------------
# Step 10: Status Check
# -------------------------------
echo "📋 ServiceApp Deployment Status:"
kubectl get deploy,svc,hpa,pods -n ${NAMESPACE} -o wide

echo "🎉 All Service Applications deployed successfully!"
