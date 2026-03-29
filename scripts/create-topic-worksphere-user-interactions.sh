#!/bin/bash
# Kafka topic creation script for UserInteraction
# Auto-generated - do not edit manually

KAFKA_CONTAINER="kafka"
BOOTSTRAP_SERVER="localhost:9092"

echo "Creating Kafka topic for UserInteraction..."

docker exec $KAFKA_CONTAINER kafka-topics \
  --bootstrap-server $BOOTSTRAP_SERVER \
  --create \
  --if-not-exists \
  --topic worksphere-user-interactions \
  --partitions 4 \
  --replication-factor 1 \
  --config retention.ms=2592000000 \
  || echo "  ✗ Failed (topic may already exist)"

echo "  ✓ Topic worksphere-user-interactions configured (4 partitions, replication: 1)"

echo ""
echo "Verify topic:"
docker exec $KAFKA_CONTAINER kafka-topics --bootstrap-server $BOOTSTRAP_SERVER --describe --topic worksphere-user-interactions
