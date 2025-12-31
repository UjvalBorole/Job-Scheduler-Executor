#!/bin/bash
# -----------------------------------------------------------
# Script: reset-job-scheduler.sh
# Purpose: Delete all Kubernetes resources in the
#          job-scheduler namespace and optionally recreate it
# Usage: ./reset-job-scheduler.sh
# -----------------------------------------------------------

NAMESPACE="job-scheduler"

echo "🚨 WARNING: This will delete ALL resources in namespace '$NAMESPACE'!"

read -p "Do you want to continue? (yes/no): " CONFIRM
if [[ "$CONFIRM" != "yes" ]]; then
  echo "Aborted."
  exit 0
fi

# 1️⃣ Delete namespace (this deletes all resources inside it)
echo "🗑️ Deleting namespace '$NAMESPACE'..."
kubectl delete namespace $NAMESPACE --wait

# 2️⃣ Optionally recreate namespace
echo "📦 Recreating namespace '$NAMESPACE'..."
kubectl create namespace $NAMESPACE

# 3️⃣ Delete dangling PersistentVolumes bound to this namespace
echo "🔍 Cleaning up dangling PersistentVolumes..."
PVS=$(kubectl get pv --no-headers | awk '{print $1}')
for pv in $PVS; do
  STATUS=$(kubectl get pv $pv -o jsonpath='{.status.phase}')
  if [[ "$STATUS" == "Released" || "$STATUS" == "Failed" ]]; then
    echo "Deleting PV: $pv"
    kubectl delete pv $pv
  fi
done

# 4️⃣ Verify cleanup
echo "✅ Resources after cleanup:"
kubectl get all -n $NAMESPACE
kubectl get pvc -n $NAMESPACE
kubectl get pv

echo "🎉 Namespace '$NAMESPACE' is clean and ready for re-deployment!"
