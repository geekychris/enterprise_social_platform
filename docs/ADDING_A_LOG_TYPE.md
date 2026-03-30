# Adding a New Structured Log Type

This guide walks through adding a new analytics event type, from schema definition through to querying it in Trino.

## Overview

The pipeline:

```
JSON schema  →  Code generator  →  Java logger class  →  Kafka  →  Spark  →  Iceberg  →  Trino
(you write)     (generates)        (you call)            (auto)    (auto)     (auto)      (you query)
```

You need to do steps 1–4. The Spark consumer auto-discovers new schemas on restart.

---

## Step 1: Define the Schema

Create a JSON config in `log-configs/`. This is the single source of truth for the entire pipeline.

**Example:** `log-configs/page_view.json`

```json
{
  "name": "PageView",
  "log_type": "page_view",
  "version": "1.0.0",
  "description": "Tracks page/screen views across all clients",
  "kafka": {
    "topic": "worksphere-page-views",
    "partitions": 4,
    "replication_factor": 1,
    "retention_ms": 2592000000
  },
  "warehouse": {
    "table_name": "analytics.page_views",
    "partition_by": ["event_date"],
    "sort_by": ["timestamp", "user_id"],
    "retention_days": 90
  },
  "fields": [
    {"name": "timestamp",    "type": "timestamp", "required": true,  "description": "Event timestamp"},
    {"name": "event_date",   "type": "date",      "required": true,  "description": "Partition date"},
    {"name": "user_id",      "type": "long",      "required": true,  "description": "Viewing user"},
    {"name": "page_type",    "type": "string",    "required": true,  "description": "FEED, PROFILE, GROUP, PAGE, MESSAGES, SEARCH, ADMIN"},
    {"name": "page_id",      "type": "long",      "required": false, "description": "Entity ID (group/page/user ID)"},
    {"name": "referrer_type","type": "string",    "required": false, "description": "Where user came from"},
    {"name": "device_type",  "type": "string",    "required": false, "description": "web, ios, android"},
    {"name": "session_id",   "type": "string",    "required": false, "description": "Client session ID"},
    {"name": "load_time_ms", "type": "long",      "required": false, "description": "Page load time in ms"},
    {"name": "properties",   "type": "map<string,string>", "required": false, "description": "Extra properties"}
  ]
}
```

### Schema rules

- `name`: PascalCase. Becomes the Java class name (e.g., `PageViewLogger`).
- `log_type`: snake_case. Used in the Kafka envelope `_log_type` field.
- `kafka.topic`: Must be unique. Convention: `worksphere-{kebab-case-name}`.
- First two fields should always be `timestamp` (type `timestamp`) and `event_date` (type `date`).
- `warehouse.partition_by`: Usually `["event_date"]`. Can add a second column for high-cardinality types.

### Supported field types

| Type | Java | Spark | Iceberg |
|---|---|---|---|
| `string` | `String` | `StringType` | `string` |
| `int` / `integer` | `Integer` | `IntegerType` | `int` |
| `long` | `Long` | `LongType` | `long` |
| `float` | `Float` | `DoubleType` | `double` |
| `double` | `Double` | `DoubleType` | `double` |
| `boolean` | `Boolean` | `BooleanType` | `boolean` |
| `timestamp` | `Instant` | `StringType`* | `string` |
| `date` | `LocalDate` | `StringType`* | `string` |
| `map<string,string>` | `Map<String, String>` | `MapType(String, String)` | `map<string,string>` |

*Timestamps and dates are stored as ISO strings in Iceberg for portability.

---

## Step 2: Generate the Java Logger

Run the structured-logger code generator:

```bash
cd /Users/chris/code/claude_world/social_enterprise

# Generate Java logger class
python3 lib/structured-logger/generators/generate_loggers.py \
  log-configs/page_view.json \
  --lang java \
  --java-output lib/structured-logger/java-logger/src/main/java/com/logging/generated

# Rebuild the logger library
cd lib/structured-logger && make build-java
```

This generates `PageViewLogger.java` with:
- A type-safe `log(Instant timestamp, LocalDate eventDate, long userId, String pageType, ...)` method
- A builder pattern for optional fields
- Kafka envelope wrapping with `_log_type`, `_log_class`, `_version`
- Auto-partitioning by `user_id`

### What the generated logger looks like (simplified)

```java
package com.logging.generated;

public class PageViewLogger extends BaseStructuredLogger {

    public PageViewLogger(String kafkaServers) {
        super("worksphere-page-views", "page_view", "PageView", "1.0.0", kafkaServers);
    }

    public void log(Instant timestamp, LocalDate eventDate, long userId,
                    String pageType, Long pageId, String referrerType,
                    String deviceType, String sessionId, Long loadTimeMs,
                    Map<String, String> properties) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("timestamp", timestamp);
        record.put("event_date", eventDate);
        record.put("user_id", userId);
        record.put("page_type", pageType);
        if (pageId != null) record.put("page_id", pageId);
        // ... remaining fields ...
        publish(String.valueOf(userId), record);
    }
}
```

---

## Step 3: Create the Kafka Topic

```bash
/opt/homebrew/bin/kafka-topics --create \
  --topic worksphere-page-views \
  --bootstrap-server localhost:9092 \
  --partitions 4 \
  --replication-factor 1
```

Verify:
```bash
/opt/homebrew/bin/kafka-topics --describe \
  --topic worksphere-page-views \
  --bootstrap-server localhost:9092
```

---

## Step 4: Wire the Logger into the Application

### 4a. Add the logger to AnalyticsService

Edit `social-platform/social-app/src/main/java/com/social/app/service/AnalyticsService.java`:

```java
// Add field
private PageViewLogger pageViewLogger;

// In init():
pageViewLogger = new PageViewLogger(kafkaServers);

// In shutdown():
try { if (pageViewLogger != null) pageViewLogger.close(); } catch (Exception ignored) {}

// Add logging method:
public void logPageView(long userId, String pageType, Long pageId,
                        String referrerType, String deviceType,
                        String sessionId, Long loadTimeMs) {
    if (!enabled) return;
    try {
        pageViewLogger.log(
            Instant.now(), LocalDate.now(), userId,
            pageType, pageId, referrerType,
            deviceType, sessionId, loadTimeMs,
            null  // properties
        );
    } catch (Exception e) {
        log.debug("Failed to log page view: {}", e.getMessage());
    }
}
```

### 4b. Call it from your controllers/services

```java
// In any controller or service:
@Autowired
private AnalyticsService analyticsService;

// Log a page view
analyticsService.logPageView(userId, "GROUP", groupId, "FEED", "web", null, null);
```

### 4c. Add to the admin topic viewer (optional)

If you want the topic visible in the Admin → Analytics panel, edit `AnalyticsViewController.java`:

```java
// Add to the allowed topics set:
Set<String> allowed = Set.of(
    "worksphere-feed-impressions",
    "worksphere-user-interactions",
    "worksphere-page-views",   // ← add
    // ...
);

// Add to the topics list:
for (String name : List.of(
    "worksphere-feed-impressions",
    "worksphere-user-interactions",
    "worksphere-page-views",   // ← add
    // ...
)) {
```

---

## Step 5: Restart the Spark Consumer

The Spark consumer auto-discovers all `*.json` files in `log-configs/`. Just restart it:

```bash
docker compose -f docker-compose.data-warehouse.yml restart spark-consumer
```

Verify the new table was created:
```bash
docker logs ws-spark-consumer 2>&1 | grep -E "✓|✗" | tail -10
# Should show: ✓ Table iceberg.worksphere.pageview ready
```

---

## Step 6: Generate Some Data and Query

### Generate events

Browse the app or call the API to trigger page views. Then wait ~30 seconds for the Spark micro-batch.

### Query in Trino

```bash
docker exec ws-trino trino --execute "
  SELECT page_type, count(*) as views, count(DISTINCT user_id) as unique_users
  FROM iceberg.worksphere.pageview
  WHERE event_date = CURRENT_DATE
  GROUP BY page_type
  ORDER BY views DESC
"
```

---

## Complete Checklist

```
□ 1. Create log-configs/{name}.json
□ 2. Run code generator → generates {Name}Logger.java
□ 3. Rebuild logger library (make build-java)
□ 4. Create Kafka topic
□ 5. Add logger to AnalyticsService (init, shutdown, log method)
□ 6. Call from controllers/services
□ 7. Rebuild social-app (mvn package)
□ 8. Restart Spark consumer
□ 9. Verify in Trino: SELECT count(*) FROM iceberg.worksphere.{name}
□ 10. (Optional) Add topic to admin viewer whitelist
```

---

## Quick Copy-Paste Script

Save this as `scripts/add-log-type.sh` for convenience:

```bash
#!/usr/bin/env bash
set -euo pipefail

# Usage: ./scripts/add-log-type.sh log-configs/page_view.json

CONFIG="$1"
PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

if [ ! -f "$CONFIG" ]; then
  echo "Usage: $0 <log-config.json>"
  exit 1
fi

NAME=$(python3 -c "import json; print(json.load(open('$CONFIG'))['name'])")
TOPIC=$(python3 -c "import json; print(json.load(open('$CONFIG'))['kafka']['topic'])")
PARTITIONS=$(python3 -c "import json; print(json.load(open('$CONFIG'))['kafka']['partitions'])")

echo "=== Adding log type: $NAME ==="
echo "  Topic: $TOPIC ($PARTITIONS partitions)"

# 1. Generate Java logger
echo ""
echo "── Step 1: Generate logger ──"
python3 "$PROJECT_DIR/lib/structured-logger/generators/generate_loggers.py" \
  "$CONFIG" \
  --lang java \
  --java-output "$PROJECT_DIR/lib/structured-logger/java-logger/src/main/java/com/logging/generated"
echo "  ✓ Generated ${NAME}Logger.java"

# 2. Build logger library
echo ""
echo "── Step 2: Build logger library ──"
cd "$PROJECT_DIR/lib/structured-logger" && make build-java 2>&1 | tail -1
echo "  ✓ Logger library built"

# 3. Create Kafka topic
echo ""
echo "── Step 3: Create Kafka topic ──"
/opt/homebrew/bin/kafka-topics --create \
  --topic "$TOPIC" \
  --bootstrap-server localhost:9092 \
  --partitions "$PARTITIONS" \
  --replication-factor 1 2>/dev/null \
  && echo "  ✓ Created topic: $TOPIC" \
  || echo "  ✓ Topic already exists: $TOPIC"

# 4. Restart Spark consumer
echo ""
echo "── Step 4: Restart Spark consumer ──"
cd "$PROJECT_DIR"
docker compose -f docker-compose.data-warehouse.yml restart spark-consumer 2>&1 | tail -1
echo "  ✓ Spark consumer restarting"

echo ""
echo "=== Done ==="
echo ""
echo "Remaining manual steps:"
echo "  1. Add ${NAME}Logger to AnalyticsService.java (init, shutdown, log method)"
echo "  2. Call analyticsService.log${NAME}(...) from your code"
echo "  3. Rebuild: cd social-platform && mvn package -pl social-app -am -DskipTests"
echo "  4. Restart the social app"
echo "  5. Query: SELECT * FROM iceberg.worksphere.$(echo $NAME | tr '[:upper:]' '[:lower:]') LIMIT 10"
```

---

## Envelope Format

Every message on the wire looks like this:

```json
{
  "_log_type": "page_view",
  "_log_class": "PageView",
  "_version": "1.0.0",
  "data": {
    "timestamp": "2026-03-29T12:00:00Z",
    "event_date": "2026-03-29",
    "user_id": 72057594037927937,
    "page_type": "GROUP",
    "page_id": 288230376151711744,
    "load_time_ms": 342
  }
}
```

The Spark consumer strips the envelope and writes only the `data` fields to Iceberg.

---

## Troubleshooting

**Logger class not found after generation:**
Rebuild the logger library: `cd lib/structured-logger && make build-java`
Then rebuild social-app: `mvn package -pl social-app -am -DskipTests`

**Spark consumer doesn't pick up new schema:**
The config volume is mounted read-only from `log-configs/`. Restart picks up changes:
`docker compose -f docker-compose.data-warehouse.yml restart spark-consumer`

**Table exists but has 0 rows:**
- Check Kafka has data: `kafka-console-consumer --topic worksphere-page-views --from-beginning --max-messages 1 --timeout-ms 5000`
- Check Spark logs: `docker logs ws-spark-consumer 2>&1 | tail -20`
- Wait 30+ seconds for the next micro-batch

**Type mismatch between schema and data:**
The Spark consumer uses the JSON config to build the Iceberg schema. If you change field types after data has been written, drop the table and restart:
```sql
-- Via Trino
DROP TABLE IF EXISTS iceberg.worksphere.pageview;
```
Then restart the Spark consumer to recreate it.
