# Data Warehouse & ML Pipeline

## Architecture

```
App (Social Platform)
    │
    ├─→ FeedImpressionLogger ──→ Kafka: worksphere-feed-impressions
    │                                      │
    ├─→ UserInteractionLogger ─→ Kafka: worksphere-user-interactions
    │                                      │
    └─→ EventPublisher ────────→ Kafka: posts.created, messages.sent, reactions.added
                                           │
                                    ┌──────┴──────┐
                                    │ Spark        │
                                    │ Consumer     │
                                    │ (30s batch)  │
                                    └──────┬──────┘
                                           │
                                    ┌──────┴──────┐
                                    │ Apache       │
                                    │ Iceberg      │
                                    │ (MinIO/S3)   │
                                    └──────┬──────┘
                                           │
                              ┌────────────┼────────────┐
                              │            │            │
                              ▼            ▼            ▼
                         ┌────────┐  ┌─────────┐  ┌─────────┐
                         │ Trino  │  │ ML      │  │ Admin   │
                         │ (SQL)  │  │ Training│  │ UI      │
                         └────────┘  └─────────┘  └─────────┘
```

## Prerequisites

- **Kafka** running on host with Docker listener (port 29092)
- **Docker network** `structured-logging_logging-network` created
- **Social app** running with analytics loggers enabled

### Kafka Dual-Listener Setup

Kafka must expose a Docker-accessible listener. The host's `/opt/homebrew/etc/kafka/server.properties` should include:

```properties
listeners=PLAINTEXT://:9092,DOCKER://:29092,CONTROLLER://:9093
advertised.listeners=PLAINTEXT://localhost:9092,DOCKER://host.docker.internal:29092,CONTROLLER://localhost:9093
listener.security.protocol.map=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT,DOCKER:PLAINTEXT,...
```

| Listener | Port | Advertised As | Used By |
|---|---|---|---|
| `PLAINTEXT` | 9092 | `localhost:9092` | Social app, CLI tools |
| `DOCKER` | 29092 | `host.docker.internal:29092` | Spark consumer, other containers |

Without this, containers get `localhost:9092` from Kafka's metadata response, which resolves to themselves — not the host.

## Starting the Pipeline

```bash
# 1. Ensure Docker network exists
docker network create structured-logging_logging-network 2>/dev/null || true

# 2. Start all data warehouse services
docker compose -f docker-compose.data-warehouse.yml up -d

# 3. Verify all 5 services are running
docker ps --format "table {{.Names}}\t{{.Status}}" | grep ws-

# Expected output:
# ws-spark-consumer    Up 2 minutes
# ws-trino             Up 5 minutes (healthy)
# ws-hive-metastore    Up 5 minutes (healthy)
# ws-hive-postgres     Up 5 minutes (healthy)
# ws-minio             Up 5 minutes (healthy)

# 4. Verify Spark is streaming
docker logs -f ws-spark-consumer
# Look for: "✓ 2 streams running. Waiting for data..."

# 5. Generate some traffic, then check data landed
curl -s "http://localhost:8080/api/feed?userId=1&page=0&size=20" -H "X-User-Id: 1" > /dev/null
sleep 35  # Wait for one Spark micro-batch
docker exec ws-trino trino --execute "SELECT count(*) FROM iceberg.worksphere.feedimpression"
```

## Services

All services are defined in `docker-compose.data-warehouse.yml`:

| Service | Container | Port | Image | Purpose |
|---|---|---|---|---|
| MinIO | `ws-minio` | 9000, 9001 | `minio/minio` | S3-compatible object storage |
| Hive Postgres | `ws-hive-postgres` | 5433 | `postgres:15` | Hive Metastore metadata DB |
| Hive Metastore | `ws-hive-metastore` | 9083 | Custom build | Iceberg catalog (Thrift) |
| Trino | `ws-trino` | 8081 | `trinodb/trino` | SQL query engine |
| Spark Consumer | `ws-spark-consumer` | — | Custom build | Kafka → Iceberg streaming |

**MinIO Console:** http://localhost:9001 (admin / password123)
**Trino:** http://localhost:8081

## Connecting to Trino

### CLI

```bash
brew install trino
trino --server localhost:8081 --catalog iceberg --schema worksphere
```

### JDBC

```
URL:    jdbc:trino://localhost:8081/iceberg/worksphere
Driver: io.trino.jdbc.TrinoDriver
User:   admin (no password)
```

### DBeaver / DataGrip

1. New Connection → Trino
2. Host: `localhost`, Port: `8081`
3. Database: `iceberg`
4. Schema: `worksphere`
5. User: `admin`, no password

### Kubernetes

```bash
kubectl -n worksphere port-forward svc/trino 8081:8080
# Then connect to localhost:8081 as above
```

## Iceberg Tables

### `iceberg.worksphere.feedimpression`

Each row represents one post shown to a user in their feed. Includes all 10 ML ranking features.

| Column | Type | Description |
|---|---|---|
| `timestamp` | string | ISO 8601 event time |
| `event_date` | string | Partition key (YYYY-MM-DD) |
| `user_id` | long | Viewer user ID |
| `post_id` | long | Post shown |
| `author_id` | long | Post author |
| `position` | int | Position in feed (0-indexed) |
| `score` | double | Ranking score assigned |
| `source` | string | `organic` or `recommended` |
| `target_type` | string | Group/page context |
| `target_id` | long | Group/page ID |
| `feat_engagement` | double | reactions + comments×2 |
| `feat_recency_hours` | double | Hours since post creation |
| `feat_affinity` | double | Viewer→author affinity |
| `feat_reaction_count` | int | Total reactions |
| `feat_comment_count` | int | Total comments |
| `feat_author_follower_count` | int | Author's followers |
| `feat_is_recommended` | boolean | From recommendation engine |
| `feat_has_attachment` | boolean | Has image/file |
| `feat_has_poll` | boolean | Has poll |
| `feat_social_distance` | int | 1=following, 2=FoF, 3=stranger |

### `iceberg.worksphere.userinteraction`

Each row represents a user action. Partitioned by `event_date` and `interaction_type`.

| Column | Type | Description |
|---|---|---|
| `timestamp` | string | ISO 8601 event time |
| `event_date` | string | Partition key |
| `user_id` | long | Acting user |
| `interaction_type` | string | REACTION, COMMENT, SEARCH, etc. |
| `target_id` | long | Target entity ID |
| `target_type` | string | post, user, conversation, etc. |
| `content_author_id` | long | Author of content acted on |
| `reaction_type` | string | LIKE, LOVE, HAHA, etc. |
| `search_query` | string | Search text |
| `search_result_count` | int | Number of results |
| `message_has_attachment` | boolean | For MESSAGE_SENT |
| `bot_context` | string | For BOT_INTERACTION |
| `bot_tools_used` | string | Comma-separated tools |
| `bot_response_time_ms` | long | Bot latency |
| `group_id` | long | Group context |
| `page_id` | long | Page context |
| `device_type` | string | web, ios, android |
| `properties` | map&lt;string,string&gt; | Extra properties |

## Example Queries

### Recent feed impressions

```sql
SELECT timestamp, user_id, post_id, score, source,
       feat_engagement, feat_recency_hours, feat_social_distance
FROM iceberg.worksphere.feedimpression
WHERE event_date = CURRENT_DATE
ORDER BY timestamp DESC
LIMIT 100;
```

### Engagement by feed source

```sql
SELECT source,
       COUNT(*) AS impressions,
       COUNT(DISTINCT user_id) AS unique_users,
       ROUND(AVG(score), 3) AS avg_score,
       ROUND(AVG(feat_recency_hours), 1) AS avg_recency_hrs
FROM iceberg.worksphere.feedimpression
WHERE event_date >= CURRENT_DATE - INTERVAL '7' DAY
GROUP BY source
ORDER BY impressions DESC;
```

### Interaction breakdown

```sql
SELECT interaction_type, COUNT(*) AS cnt
FROM iceberg.worksphere.userinteraction
WHERE event_date >= CURRENT_DATE - INTERVAL '7' DAY
GROUP BY interaction_type
ORDER BY cnt DESC;
```

### Top content by reactions

```sql
SELECT target_id AS post_id, content_author_id,
       COUNT(*) AS reactions,
       COUNT(DISTINCT user_id) AS unique_reactors
FROM iceberg.worksphere.userinteraction
WHERE interaction_type = 'reaction'
  AND event_date >= CURRENT_DATE - INTERVAL '7' DAY
GROUP BY target_id, content_author_id
ORDER BY reactions DESC
LIMIT 20;
```

### Feature distribution for model analysis

```sql
SELECT ROUND(feat_engagement, 0) AS engagement_bucket,
       COUNT(*) AS count,
       ROUND(AVG(CASE WHEN feat_recency_hours < 12 THEN 1.0 ELSE 0.0 END), 2) AS pct_recent,
       ROUND(AVG(CASE WHEN feat_social_distance = 1 THEN 1.0 ELSE 0.0 END), 2) AS pct_following
FROM iceberg.worksphere.feedimpression
WHERE event_date >= CURRENT_DATE - INTERVAL '30' DAY
GROUP BY ROUND(feat_engagement, 0)
ORDER BY engagement_bucket;
```

### Build training dataset (impressions joined with interactions)

This is the key query that produces labeled data for model training:

```sql
SELECT fi.user_id, fi.post_id,
       fi.feat_engagement, fi.feat_recency_hours, fi.feat_affinity,
       fi.feat_reaction_count, fi.feat_comment_count,
       fi.feat_author_follower_count, fi.feat_is_recommended,
       fi.feat_has_attachment, fi.feat_has_poll, fi.feat_social_distance,
       CASE WHEN ui.user_id IS NOT NULL THEN 1 ELSE 0 END AS engaged
FROM iceberg.worksphere.feedimpression fi
LEFT JOIN iceberg.worksphere.userinteraction ui
    ON fi.user_id = ui.user_id
    AND fi.post_id = ui.target_id
    AND ui.interaction_type IN ('reaction', 'comment', 'FEED_CLICK')
    AND ui.timestamp BETWEEN fi.timestamp AND fi.timestamp + INTERVAL '5' MINUTE
WHERE fi.event_date >= CURRENT_DATE - INTERVAL '30' DAY;
```

## ML Pipeline

### Phase 1: Heuristic Scoring (Current)

```java
// FeedFeatureExtractor.computeScore()
double recencyDecay = Math.pow(0.5, recencyHours / 24.0);
score = engagement * recencyDecay * affinity;
```

### Phase 2: Data Collection (Active)

Feed impressions and user interactions are streamed to Kafka and landed in Iceberg every 30 seconds. This creates the raw data needed for labeled training examples.

### Phase 3: Model Training

```bash
cd ml/feed-ranking

# Synthetic data (development / testing)
python3 train.py --samples 100000 --trees 200

# Real data (exported from Trino)
python3 train.py --data training_data.parquet --trees 500 --depth 8

# Run test suite (32 tests)
python3 test_model.py
```

**Model:** XGBoost binary classifier (200 trees, depth 6, lr 0.1)
**Validation:** AUC-ROC >= 0.65, AUC-PR >= 0.25, 5-fold cross-validation

Outputs:
| File | Format | Purpose |
|---|---|---|
| `output/feed_ranker.ubj` | XGBoost binary | Input to GBDT kernel codegen |
| `output/feed_ranker.json` | JSON | Human-readable model |
| `output/feed_ranker.joblib` | Joblib | Python inference |
| `output/feed_ranker_result.json` | JSON | Metrics, feature importance |
| `output/feature_config.json` | JSON | Feature schema for ranker framework |

### Phase 4: GBDT Kernel Generation

Uses `gbdt_accelerated_ranker_framework` to convert XGBoost trees into optimized C scoring code:

```bash
python3 -m cuda_codegen generate \
  --model output/feed_ranker.ubj \
  --output output/generated \
  --user-features 2 \
  --library --cpu
```

The `--user-features 2` flag indicates that features at indices 2 (affinity) and 9 (social_distance) vary per user and must be passed at request time. The remaining 8 item features can be pre-computed and cached.

### Phase 5: Deployment

1. Deploy `scorched` gRPC server with the generated C kernel
2. Add gRPC client to `FeedService`
3. Replace `FeedFeatureExtractor.computeScore()` with gRPC inference call
4. A/B test: 50% heuristic vs 50% GBDT, measure engagement lift

## Admin ML Playground

Access via Admin panel → ML Playground tab:

- **Feature Explorer:** Top 30 recent posts with all 10 extracted features and scores
- **Score Simulator:** Adjust feature sliders to see how ranking score changes
- **Analytics Stats:** Platform-wide counts (posts, messages, reactions, users, feed entries, unread counts)
- **Data Explorer:** Browse Kafka topics for recent analytics events
- **Model Info:** Current model status, feature schema, scoring formula

## Spark Consumer Details

The consumer (`docker/spark-consumer/consumer.py`) is schema-driven:

1. Reads all `*.json` files from `log-configs/` (mounted as a volume)
2. For each config: creates an Iceberg table matching the field definitions
3. Starts a Spark Structured Streaming query per topic
4. Parses the JSON envelope (`{_log_type, _log_class, _version, data}`) and extracts `data.*`
5. Writes to Iceberg using `toTable()` with 30-second micro-batches

To add a new log type:
1. Create a JSON schema in `log-configs/`
2. Create a corresponding code-generated logger (via structured-logger)
3. Restart the Spark consumer: `docker compose -f docker-compose.data-warehouse.yml restart spark-consumer`

## Troubleshooting

**Spark consumer exits immediately:**
```bash
docker logs ws-spark-consumer 2>&1 | grep -E "(✗|Error|Exception)" | head -5
```
Common causes: Kafka unreachable (check dual-listener setup), S3/MinIO not healthy, missing `AWS_REGION`.

**Tables empty after generating traffic:**
- Verify Kafka has data: `kafka-console-consumer --topic worksphere-feed-impressions --from-beginning --max-messages 1 --timeout-ms 5000`
- Check Spark is streaming: look for "Committed offsets for batch" in logs
- Wait 30+ seconds for the next micro-batch

**Trino query returns 0 rows:**
- Iceberg metadata may be cached. Run: `CALL iceberg.system.invalidate_metadata('worksphere', 'feedimpression')`
- Check table exists: `SHOW TABLES IN iceberg.worksphere`

**"Invalid S3 URI" errors in Spark:**
- The Iceberg namespace was created with a local file path. Fix:
  ```sql
  -- Via Trino
  DROP TABLE IF EXISTS iceberg.worksphere.feedimpression;
  DROP TABLE IF EXISTS iceberg.worksphere.userinteraction;
  ```
  Then restart the Spark consumer (it will recreate with S3 paths).
