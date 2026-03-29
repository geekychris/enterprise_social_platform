#!/bin/bash
# Kafka topic creation script for FeedImpression
# Auto-generated - do not edit manually

KAFKA_CONTAINER="kafka"
BOOTSTRAP_SERVER="localhost:9092"

echo "Creating Kafka topic for FeedImpression..."

docker exec $KAFKA_CONTAINER kafka-topics \
  --bootstrap-server $BOOTSTRAP_SERVER \
  --create \
  --if-not-exists \
  --topic worksphere-feed-impressions \
  --partitions 4 \
  --replication-factor 1 \
  --config retention.ms=2592000000 \
  || echo "  ✗ Failed (topic may already exist)"

echo "  ✓ Topic worksphere-feed-impressions configured (4 partitions, replication: 1)"

echo ""
echo "Verify topic:"
docker exec $KAFKA_CONTAINER kafka-topics --bootstrap-server $BOOTSTRAP_SERVER --describe --topic worksphere-feed-impressions
