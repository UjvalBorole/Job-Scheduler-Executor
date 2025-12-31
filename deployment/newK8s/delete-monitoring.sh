#!/bin/bash
set -e

NAMESPACE="job-scheduler"
BASE_DIR="."

echo "🧹 Deleting Monitoring Stack from namespace: ${NAMESPACE}"

# -------------------------------
# Step 1: Grafana (UI)
# -------------------------------
echo "📊 Removing Grafana..."
kubectl delete -f "${BASE_DIR}/Monitoring/grafana/grafana-Deployment.yaml" -n ${NAMESPACE} --ignore-not-found
kubectl delete -f "${BASE_DIR}/Monitoring/grafana/grafana-ClusterIPService.yaml" -n ${NAMESPACE} --ignore-not-found
kubectl delete -f "${BASE_DIR}/Monitoring/grafana/grafana-ConfigMap.yaml" -n ${NAMESPACE} --ignore-not-found
kubectl delete -f "${BASE_DIR}/Monitoring/grafana/grafana-PVC.yaml" -n ${NAMESPACE} --ignore-not-found

# -------------------------------
# Step 2: Tempo
# -------------------------------
echo "⏱️ Removing Tempo..."
kubectl delete -f "${BASE_DIR}/Monitoring/tempo/tempo-StatefulSet.yaml" -n ${NAMESPACE} --ignore-not-found
kubectl delete -f "${BASE_DIR}/Monitoring/tempo/tempo-ClusterIPService.yaml" -n ${NAMESPACE} --ignore-not-found
kubectl delete -f "${BASE_DIR}/Monitoring/tempo/tempo-ConfigMap.yaml" -n ${NAMESPACE} --ignore-not-found

# -------------------------------
# Step 3: Promtail
# -------------------------------
echo "🧲 Removing Promtail..."
kubectl delete -f "${BASE_DIR}/Monitoring/promtail/promtail-DaemonSet.yaml" -n ${NAMESPACE} --ignore-not-found
kubectl delete -f "${BASE_DIR}/Monitoring/promtail/promtail-ConfigMap.yaml" -n ${NAMESPACE} --ignore-not-found
kubectl delete -f "${BASE_DIR}/Monitoring/promtail/promtail-RBAC.yaml" -n ${NAMESPACE} --ignore-not-found

# -------------------------------
# Step 4: Loki
# -------------------------------
echo "📜 Removing Loki..."
kubectl delete -f "${BASE_DIR}/Monitoring/loki/loki-StatefulSet.yaml" -n ${NAMESPACE} --ignore-not-found
kubectl delete -f "${BASE_DIR}/Monitoring/loki/loki-ClusterIPService.yaml" -n ${NAMESPACE} --ignore-not-found
kubectl delete -f "${BASE_DIR}/Monitoring/loki/loki-HeadlessService.yaml" -n ${NAMESPACE} --ignore-not-found
kubectl delete -f "${BASE_DIR}/Monitoring/loki/loki-ConfigMap.yaml" -n ${NAMESPACE} --ignore-not-found

# -------------------------------
# Step 5: Prometheus
# -------------------------------
echo "📈 Removing Prometheus..."
kubectl delete -f "${BASE_DIR}/Monitoring/prometheus/pro-Deployment.yaml" -n ${NAMESPACE} --ignore-not-found
kubectl delete -f "${BASE_DIR}/Monitoring/prometheus/pro-ClusterIPService.yaml" -n ${NAMESPACE} --ignore-not-found
kubectl delete -f "${BASE_DIR}/Monitoring/prometheus/pro-ConfigMap.yaml" -n ${NAMESPACE} --ignore-not-found
kubectl delete -f "${BASE_DIR}/Monitoring/prometheus/pro-PVC.yaml" -n ${NAMESPACE} --ignore-not-found

# -------------------------------
# Step 6: Final Status
# -------------------------------
echo "📋 Remaining Monitoring Resources:"
kubectl get pods,svc,pvc -n ${NAMESPACE}

echo "✅ Monitoring stack deletion completed!"
