#!/bin/bash
set -e

NAMESPACE="job-scheduler"
BASE_DIR="."

echo "🚀 Creating Namespace..."
kubectl apply -f "${BASE_DIR}/namespace.yaml"

# Label the worker nodes as infra,monitoring,seriveapp nodes
kubectl label node desktop-worker node-type=infra
kubectl label node desktop-worker3 node-type=serviceapp
kubectl label node desktop-worker2 node-type=monitoring
kubectl get nodes --show-labels


# -------------------------------
# Step 0: Apply StorageClass
# -------------------------------
if [ -f "${BASE_DIR}/StorageClass.yaml" ]; then
    echo "💾 Applying StorageClass..."
    kubectl apply -f "${BASE_DIR}/StorageClass.yaml"
fi

# -------------------------------
# Step 1: Apply ConfigMaps
# -------------------------------
echo "📦 Applying ConfigMaps..."
kubectl apply -f "${BASE_DIR}/Infra/kafka/kafka-ConfigMap.yaml"
# kubectl apply -f "${BASE_DIR}/Infra/mongodb/mongo-ConfigMap.yaml"
kubectl apply -f "${BASE_DIR}/Infra/postgres/postgres-ConfigMap.yaml"
kubectl apply -f "${BASE_DIR}/Infra/redis/redis-ConfigMap.yaml"

# -------------------------------
# Step 2: Deploy StatefulSets + Services
# -------------------------------
echo "🗄️ Deploying Infra StatefulSets..."

# Postgres
kubectl apply -f "${BASE_DIR}/Infra/postgres/postgres-StatefulSet.yaml"
kubectl apply -f "${BASE_DIR}/Infra/postgres/postgres-ClusterIPService.yaml"
kubectl apply -f "${BASE_DIR}/Infra/postgres/postgres-HeadlessService.yaml"
kubectl apply -f "${BASE_DIR}/Infra/postgres/postgres-ConfigMap.yaml"

# MongoDB
kubectl apply -f "${BASE_DIR}/Infra/mongodb/mongo-StatefulSet.yaml"
kubectl apply -f "${BASE_DIR}/Infra/mongodb/mongo-ClusterIPService.yaml"
kubectl apply -f "${BASE_DIR}/Infra/mongodb/mongo-HeadlessService.yaml"

# Redis
kubectl apply -f "${BASE_DIR}/Infra/redis/redis-StatefulSet.yaml"
kubectl apply -f "${BASE_DIR}/Infra/redis/redis-Service.yaml"

# Kafka
kubectl apply -f "${BASE_DIR}/Infra/kafka/kafka-StatefulSet.yaml"
kubectl apply -f "${BASE_DIR}/Infra/kafka/kafka-ClusterIPService.yaml"
kubectl apply -f "${BASE_DIR}/Infra/kafka/Kafka-HeadlessService.yaml"
kubectl apply -f "${BASE_DIR}/Infra/kafka/zookeeper.yaml"


# -------------------------------
# Step 3: Wait for readiness
# -------------------------------
echo "⏳ Waiting for pods to be Ready..."
kubectl wait --for=condition=Ready pod -l app=postgres -n ${NAMESPACE} --timeout=300s
kubectl wait --for=condition=Ready pod -l app=mongodb -n ${NAMESPACE} --timeout=300s
kubectl wait --for=condition=Ready pod -l app=redis -n ${NAMESPACE} --timeout=300s
kubectl wait --for=condition=Ready pod -l app=kafka -n ${NAMESPACE} --timeout=300s

# -------------------------------
# Step 4: Status
# -------------------------------
echo "📋 Infra Deployment Status:"
kubectl get pods,pvc,svc -n ${NAMESPACE} -o wide

echo "🎉 Infra deployed successfully!"
