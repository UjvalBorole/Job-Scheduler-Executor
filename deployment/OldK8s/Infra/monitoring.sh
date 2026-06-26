#!/bin/bash

LOGFILE="cluster_check_$(date +%Y%m%d_%H%M%S).log"
NAMESPACE="job-scheduler"

echo "===== Kubernetes Cluster Check Script =====" | tee -a $LOGFILE
echo "Namespace: $NAMESPACE" | tee -a $LOGFILE
echo "Check started at $(date)" | tee -a $LOGFILE
echo "----------------------------------------" | tee -a $LOGFILE

# 1️⃣ Namespaces
echo -e "\n=== NAMESPACES ===" | tee -a $LOGFILE
kubectl get namespaces | tee -a $LOGFILE

# 2️⃣ All resources in the namespace
echo -e "\n=== ALL RESOURCES IN $NAMESPACE ===" | tee -a $LOGFILE
kubectl get all -n $NAMESPACE | tee -a $LOGFILE

# 3️⃣ Pods, describe, logs
echo -e "\n=== POD STATUS AND LOGS ===" | tee -a $LOGFILE
kubectl get pods -n $NAMESPACE -o wide | tee -a $LOGFILE

for pod in $(kubectl get pods -n $NAMESPACE -o name); do
  echo -e "\n=== DESCRIBE $pod ===" | tee -a $LOGFILE
  kubectl describe $pod -n $NAMESPACE | tee -a $LOGFILE

  echo -e "\n=== LAST 50 LOG LINES OF $pod ===" | tee -a $LOGFILE
  kubectl logs $pod -n $NAMESPACE --tail=50 | tee -a $LOGFILE
done

# 4️⃣ StatefulSets
echo -e "\n=== STATEFULSETS ===" | tee -a $LOGFILE
kubectl get statefulsets -n $NAMESPACE | tee -a $LOGFILE
for sts in $(kubectl get statefulsets -n $NAMESPACE -o name); do
  echo -e "\n=== DESCRIBE $sts ===" | tee -a $LOGFILE
  kubectl describe $sts -n $NAMESPACE | tee -a $LOGFILE
done

# 5️⃣ Deployments
echo -e "\n=== DEPLOYMENTS ===" | tee -a $LOGFILE
kubectl get deployments -n $NAMESPACE | tee -a $LOGFILE
for deploy in $(kubectl get deployments -n $NAMESPACE -o name); do
  echo -e "\n=== DESCRIBE $deploy ===" | tee -a $LOGFILE
  kubectl describe $deploy -n $NAMESPACE | tee -a $LOGFILE
done

# 6️⃣ Services
echo -e "\n=== SERVICES ===" | tee -a $LOGFILE
kubectl get svc -n $NAMESPACE | tee -a $LOGFILE
for svc in $(kubectl get svc -n $NAMESPACE -o name); do
  echo -e "\n=== DESCRIBE $svc ===" | tee -a $LOGFILE
  kubectl describe $svc -n $NAMESPACE | tee -a $LOGFILE
done

# 7️⃣ PersistentVolumeClaims
echo -e "\n=== PVCs ===" | tee -a $LOGFILE
kubectl get pvc -n $NAMESPACE | tee -a $LOGFILE
for pvc in $(kubectl get pvc -n $NAMESPACE -o name); do
  echo -e "\n=== DESCRIBE $pvc ===" | tee -a $LOGFILE
  kubectl describe $pvc -n $NAMESPACE | tee -a $LOGFILE
done

# 8️⃣ ConfigMaps
echo -e "\n=== CONFIGMAPS ===" | tee -a $LOGFILE
kubectl get configmap -n $NAMESPACE | tee -a $LOGFILE
for cm in $(kubectl get configmap -n $NAMESPACE -o name); do
  echo -e "\n=== DESCRIBE $cm ===" | tee -a $LOGFILE
  kubectl describe $cm -n $NAMESPACE | tee -a $LOGFILE
done

# 9️⃣ Secrets
echo -e "\n=== SECRETS ===" | tee -a $LOGFILE
kubectl get secret -n $NAMESPACE | tee -a $LOGFILE
for secret in $(kubectl get secret -n $NAMESPACE -o name); do
  echo -e "\n=== DESCRIBE $secret ===" | tee -a $LOGFILE
  kubectl describe $secret -n $NAMESPACE | tee -a $LOGFILE
done

# 🔟 Events
echo -e "\n=== RECENT EVENTS ===" | tee -a $LOGFILE
kubectl get events -n $NAMESPACE --sort-by='.metadata.creationTimestamp' | tee -a $LOGFILE

# 1️⃣1️⃣ Kafka health
echo -e "\n=== KAFKA TOPICS AND CONSUMER GROUPS ===" | tee -a $LOGFILE
for pod in $(kubectl get pods -n $NAMESPACE -l app=kafka -o name); do
  echo -e "\n--- $pod ---" | tee -a $LOGFILE
  kubectl exec -it $pod -n $NAMESPACE -- kafka-topics --bootstrap-server localhost:9092 --list 2>/dev/null | tee -a $LOGFILE
  kubectl exec -it $pod -n $NAMESPACE -- kafka-consumer-groups --bootstrap-server localhost:9092 --list 2>/dev/null | tee -a $LOGFILE
done

# 1️⃣2️⃣ Zookeeper health
echo -e "\n=== ZOOKEEPER NODES ===" | tee -a $LOGFILE
for pod in $(kubectl get pods -n $NAMESPACE -l app=zookeeper -o name); do
  echo -e "\n--- $pod ---" | tee -a $LOGFILE
  kubectl exec -it $pod -n $NAMESPACE -- zkCli.sh -server localhost:2181 ls / 2>/dev/null | tee -a $LOGFILE
done

echo -e "\n=== CHECK COMPLETE ===" | tee -a $LOGFILE
echo "Output saved to $LOGFILE"
echo "Check ended at $(date)" | tee -a $LOGFILE
echo "========================================" | tee -a $LOGFILE