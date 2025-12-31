#!/bin/bash
set -e

NAMESPACE="job-scheduler"
BASE_DIR="."

echo "📊 Deploying Monitoring Stack into namespace: ${NAMESPACE}"
kubectl get nodes -L node-type -o wide

# -------------------------------
# Step 1: Prometheus (Storage + Config + Deployment)
# -------------------------------
echo "📈 Deploying Prometheus..."
kubectl apply -f "${BASE_DIR}/Monitoring/prometheus/pro-PVC.yaml" -n ${NAMESPACE}
kubectl apply -f "${BASE_DIR}/Monitoring/prometheus/pro-ConfigMap.yaml" -n ${NAMESPACE}
kubectl apply -f "${BASE_DIR}/Monitoring/prometheus/pro-Deployment.yaml" -n ${NAMESPACE}
kubectl apply -f "${BASE_DIR}/Monitoring/prometheus/pro-ClusterIPService.yaml" -n ${NAMESPACE}

# -------------------------------
# Step 2: Loki (Logs backend)
# -------------------------------
echo "📜 Deploying Loki..."
kubectl apply -f "${BASE_DIR}/Monitoring/loki/loki-ConfigMap.yaml" -n ${NAMESPACE}
kubectl apply -f "${BASE_DIR}/Monitoring/loki/loki-StatefulSet.yaml" -n ${NAMESPACE}
kubectl apply -f "${BASE_DIR}/Monitoring/loki/loki-ClusterIPService.yaml" -n ${NAMESPACE}
kubectl apply -f "${BASE_DIR}/Monitoring/loki/loki-HeadlessService.yaml" -n ${NAMESPACE}

# -------------------------------
# Step 3: Promtail (Log Collector)
# -------------------------------
echo "🧲 Deploying Promtail..."
kubectl apply -f "${BASE_DIR}/Monitoring/promtail/promtail-RBAC.yaml" -n ${NAMESPACE}
kubectl apply -f "${BASE_DIR}/Monitoring/promtail/promtail-ConfigMap.yaml" -n ${NAMESPACE}
kubectl apply -f "${BASE_DIR}/Monitoring/promtail/promtail-DaemonSet.yaml" -n ${NAMESPACE}

# -------------------------------
# Step 4: Tempo (Tracing backend)
# -------------------------------
echo "⏱️ Deploying Tempo..."
kubectl apply -f "${BASE_DIR}/Monitoring/tempo/tempo-ConfigMap.yaml" -n ${NAMESPACE}
kubectl apply -f "${BASE_DIR}/Monitoring/tempo/tempo-StatefulSet.yaml" -n ${NAMESPACE}
kubectl apply -f "${BASE_DIR}/Monitoring/tempo/tempo-ClusterIPService.yaml" -n ${NAMESPACE}

# -------------------------------
# Step 5: Grafana (UI)
# -------------------------------
echo "📊 Deploying Grafana..."
kubectl apply -f "${BASE_DIR}/Monitoring/grafana/grafana-PVC.yaml" -n ${NAMESPACE}
kubectl apply -f "${BASE_DIR}/Monitoring/grafana/grafana-ConfigMap.yaml" -n ${NAMESPACE}
kubectl apply -f "${BASE_DIR}/Monitoring/grafana/grafana-Deployment.yaml" -n ${NAMESPACE}
kubectl apply -f "${BASE_DIR}/Monitoring/grafana/grafana-ClusterIPService.yaml" -n ${NAMESPACE}

# -------------------------------
# Step 6: Wait for Readiness
# -------------------------------
echo "⏳ Waiting for monitoring pods to be Ready..."
kubectl wait --for=condition=Ready pod \
  -n ${NAMESPACE} \
  -l app.kubernetes.io/name \
  --timeout=600s || true

# -------------------------------
# Step 7: Status
# -------------------------------
echo "📋 Monitoring Stack Status:"
kubectl get pods,svc,pvc -n ${NAMESPACE} -o wide

echo "🎉 Monitoring stack deployed successfully!"
