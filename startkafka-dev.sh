#!/usr/bin/env bash
set -e

KAFKA_HOME="D:/lap/java/kafka_2"
ZK_CONFIG="$KAFKA_HOME/config/zookeeper.properties"
KAFKA_CONFIG="$KAFKA_HOME/config/server.properties"
LOG_DIR="$KAFKA_HOME/logs"

ZK_LOG="$LOG_DIR/zookeeper.log"
KAFKA_LOG="$LOG_DIR/kafka.log"

TIMEOUT=60

mkdir -p "$LOG_DIR"
rm -f "$ZK_LOG" "$KAFKA_LOG"

cleanup() {
  echo ""
  echo "🛑 Stopping Kafka & Zookeeper..."
  taskkill //IM java.exe //F > /dev/null 2>&1 || true
  echo "✅ Shutdown complete"
  exit 0
}

trap cleanup SIGINT SIGTERM

echo "🚀 Starting Zookeeper..."
"$KAFKA_HOME/bin/windows/zookeeper-server-start.bat" "$ZK_CONFIG" \
  > "$ZK_LOG" 2>&1 &

echo "⏳ Waiting for Zookeeper to be ready..."
for ((i=1;i<=TIMEOUT;i++)); do
  if grep -Eq "PrepRequestProcessor|Snapshotting|Started AdminServer" "$ZK_LOG"; then
    echo "✅ Zookeeper is UP"
    break
  fi
  sleep 1
  if [[ $i -eq $TIMEOUT ]]; then
    echo "❌ Zookeeper failed to start"
    exit 1
  fi
done

echo "🚀 Starting Kafka Broker..."
"$KAFKA_HOME/bin/windows/kafka-server-start.bat" "$KAFKA_CONFIG" \
  > "$KAFKA_LOG" 2>&1 &

echo "⏳ Waiting for Kafka to be ready..."
for ((i=1;i<=TIMEOUT;i++)); do
  if grep -Eq "KafkaServer.*started" "$KAFKA_LOG"; then
    echo "✅ Kafka is UP"
    break
  fi
  sleep 1
  if [[ $i -eq $TIMEOUT ]]; then
    echo "❌ Kafka failed to start"
    exit 1
  fi
done

echo ""
echo "📡 Live logs (CTRL+C to stop everything)"
echo "--------------------------------------"

tail -n 50 -f "$ZK_LOG" "$KAFKA_LOG"
