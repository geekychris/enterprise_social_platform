#!/usr/bin/env bash
set -euo pipefail

# WorkSphere Full Stack Startup Script
# Starts all services in the correct order

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

echo "═══════════════════════════════════════════════════"
echo "  WorkSphere Full Stack Startup"
echo "═══════════════════════════════════════════════════"

# ── 1. Infrastructure services (brew) ──
echo ""
echo "── Step 1: Infrastructure (Redis, Kafka) ──"
brew services start redis 2>/dev/null || true
brew services start kafka 2>/dev/null || true
sleep 3
redis-cli ping 2>/dev/null | grep -q PONG && echo "  ✓ Redis" || echo "  ✗ Redis failed"
lsof -i :9092 2>/dev/null | grep -q LISTEN && echo "  ✓ Kafka" || echo "  ✗ Kafka (may need a moment)"

# ── 2. Docker services (social platform core) ──
echo ""
echo "── Step 2: Core Docker services ──"
cd "$PROJECT_DIR"
if [ -f social-platform/docker-compose.yml ]; then
  docker compose -f social-platform/docker-compose.yml up -d 2>&1 | grep -v "^$" | tail -5
fi

# Check core services
sleep 5
docker ps --format "{{.Names}} {{.Status}}" | grep "social-postgres" | head -1 | sed 's/^/  /'
docker ps --format "{{.Names}} {{.Status}}" | grep "social-opensearch" | head -1 | sed 's/^/  /'
docker ps --format "{{.Names}} {{.Status}}" | grep "social-aoee" | head -1 | sed 's/^/  /'

# ── 3. Ensure Docker network exists ──
echo ""
echo "── Step 3: Docker network ──"
docker network create structured-logging_logging-network 2>/dev/null && echo "  Created logging-network" || echo "  ✓ logging-network exists"

# ── 4. Data warehouse services ──
echo ""
echo "── Step 4: Data Warehouse (MinIO, Hive Metastore) ──"
if [ -f docker-compose.data-warehouse.yml ]; then
  docker compose -f docker-compose.data-warehouse.yml up -d 2>&1 | tail -5
  echo "  Waiting for Hive Metastore to initialize (can take 60-90 seconds)..."
  for i in $(seq 1 30); do
    if docker ps --format "{{.Names}} {{.Status}}" | grep "ws-hive-metastore" | grep -q "healthy"; then
      echo "  ✓ Hive Metastore healthy"
      break
    fi
    sleep 5
    printf "."
  done
  echo ""
fi

# ── 5. Trino + Spark Consumer (part of data warehouse compose) ──
echo ""
echo "── Step 5: Trino + Spark Consumer ──"
docker ps --format "{{.Names}} {{.Status}}" | grep "ws-trino" | head -1 | sed 's/^/  /'
docker ps --format "{{.Names}} {{.Status}}" | grep "ws-spark-consumer" | head -1 | sed 's/^/  /'

# ── 6. Kafka topics ──
echo ""
echo "── Step 6: Kafka Topics ──"
KAFKA_TOPICS="/opt/homebrew/bin/kafka-topics"
for topic in worksphere-feed-impressions worksphere-user-interactions posts.created messages.sent reactions.added; do
  $KAFKA_TOPICS --create --topic "$topic" --bootstrap-server localhost:9092 --partitions 4 --replication-factor 1 2>/dev/null && echo "  Created: $topic" || echo "  ✓ $topic exists"
done

# ── 7. Social App ──
echo ""
echo "── Step 7: Social App ──"
if pgrep -f 'social-app-1.0.0' > /dev/null; then
  echo "  ✓ Social App already running"
else
  echo "  Starting Social App..."
  java -jar "$PROJECT_DIR/social-platform/social-app/target/social-app-1.0.0-SNAPSHOT.jar" \
    --spring.datasource.url=jdbc:postgresql://localhost:5432/social_enterprise \
    --spring.datasource.username=social \
    --spring.datasource.password=social_dev_password \
    --social.media.upload-dir="$PROJECT_DIR/uploads" \
    > /tmp/social-app.log 2>&1 &
  echo "  Started (PID: $!). Log: /tmp/social-app.log"
  echo "  Waiting for startup..."
  for i in $(seq 1 30); do
    if grep -q "Started SocialApplication" /tmp/social-app.log 2>/dev/null; then
      echo "  ✓ Social App ready"
      break
    fi
    sleep 2
    printf "."
  done
  echo ""
fi

# ── Status ──
echo ""
echo "═══════════════════════════════════════════════════"
echo "  Status"
echo "═══════════════════════════════════════════════════"
echo ""
echo "Services:"
pgrep -f 'social-app-1.0.0' > /dev/null && echo "  ✓ Social App         http://localhost:8080" || echo "  ✗ Social App"
redis-cli ping 2>/dev/null | grep -q PONG && echo "  ✓ Redis              localhost:6379" || echo "  ✗ Redis"
lsof -i :9092 2>/dev/null | grep -q LISTEN && echo "  ✓ Kafka              localhost:9092" || echo "  ✗ Kafka"
curl -s http://localhost:11434/api/tags > /dev/null 2>&1 && echo "  ✓ Ollama             http://localhost:11434" || echo "  ✗ Ollama"
docker ps --format "{{.Names}}" | grep -q "social-postgres" && echo "  ✓ PostgreSQL         localhost:5432" || echo "  ✗ PostgreSQL"
docker ps --format "{{.Names}}" | grep -q "social-opensearch" && echo "  ✓ OpenSearch         http://localhost:9200" || echo "  ✗ OpenSearch"
docker ps --format "{{.Names}}" | grep -q "ws-minio" && echo "  ✓ MinIO              http://localhost:9000 (console: 9001)" || echo "  ✗ MinIO"
docker ps --format "{{.Names}}" | grep -q "ws-hive-metastore" && echo "  ✓ Hive Metastore     localhost:9083" || echo "  ✗ Hive Metastore"
docker ps --format "{{.Names}}" | grep -q "ws-trino" && echo "  ✓ Trino              http://localhost:8081" || echo "  ✗ Trino"
docker ps --format "{{.Names}}" | grep -q "ws-spark-consumer" && echo "  ✓ Spark Consumer     Kafka → Iceberg streaming" || echo "  ✗ Spark Consumer"

echo ""
echo "Access:"
echo "  Web UI:           http://localhost:8080"
echo "  Admin:            http://localhost:8080/admin"
echo "  MinIO Console:    http://localhost:9001 (admin/password123)"
echo "  Trino:            trino --server localhost:8081 --catalog iceberg"
echo "  Kafka Topics:     kafka-topics --list --bootstrap-server localhost:9092"
echo ""
