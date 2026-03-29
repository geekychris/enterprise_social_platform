# Running WorkSphere from IntelliJ IDEA

## Quick Start

```bash
# Start all infrastructure (everything except the social app)
./scripts/start-all.sh --no-app

# Then run/debug the app from IntelliJ
```

## IntelliJ Run Configuration

### 1. Open the Project

Open `social-platform/social-app` as a Maven project in IntelliJ. If prompted, import the Maven project and let it resolve dependencies.

### 2. Create a Run Configuration

**Run > Edit Configurations > + > Spring Boot**

| Field | Value |
|---|---|
| **Name** | `SocialApplication` |
| **Main class** | `com.social.app.SocialApplication` |
| **Module** | `social-app` |
| **JDK** | Java 21 |
| **Working directory** | `$ProjectFileDir$` |

### 3. Program Arguments

In the **Program arguments** field, paste:

```
--spring.datasource.password=social_dev_password
--social.media.upload-dir=/Users/chris/code/claude_world/social_enterprise/uploads
```

Alternatively, use **VM options** (prefix with `-D`):

```
-Dspring.datasource.password=social_dev_password
-Dsocial.media.upload-dir=/Users/chris/code/claude_world/social_enterprise/uploads
```

### 4. Environment Variables (optional overrides)

Click the **Environment variables** field and add any of these if your setup differs from defaults:

| Variable | Default | Purpose |
|---|---|---|
| `REDIS_HOST` | `localhost` | Redis hostname |
| `REDIS_PORT` | `6379` | Redis port |
| `KAFKA_SERVERS` | `localhost:9092` | Kafka bootstrap servers |
| `SOCIAL_AI_OLLAMA_URL` | `http://localhost:11434` | Ollama LLM endpoint |
| `SOCIAL_AI_MODEL` | `llama3.2:latest` | Ollama model name |
| `SOCIAL_BOT_USER_ID` | `72057594037999999` | Bot user ID |
| `WORKSPHERE_NODE_ID` | `1` | Snowflake node ID |

For the default local setup, **no environment variables are needed** — everything uses sensible defaults from `application.yml`.

### 5. Run or Debug

Click **Run** or **Debug**. The app starts on `http://localhost:8080`.

Set breakpoints anywhere in the codebase. Hot-reload works for most changes via IntelliJ's auto-build.

## What Each Parameter Does

The `application.yml` already has defaults for everything except the database password (which is blank by default for safety):

```yaml
# These are the defaults — you only need to override what differs
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/social_enterprise  # default
    username: social                                          # default
    password:                                                 # MUST override
  data:
    redis:
      host: localhost    # default
      port: 6379         # default
  kafka:
    bootstrap-servers: localhost:9092  # default

social:
  media:
    upload-dir: ./uploads  # override to absolute path for consistency
  auth:
    debug-bypass: true     # allows X-Debug-User-Id header
  aoee:
    proxy-port: 8082       # default
  opensearch:
    host: localhost        # default
    port: 9200             # default
  ai:
    ollama-url: http://localhost:11434  # default
```

## Infrastructure Services

The `--no-app` flag starts everything the app depends on:

| Service | Port | Started By |
|---|---|---|
| PostgreSQL | 5432 | `docker compose up` |
| OpenSearch | 9200 | `docker compose up` |
| AOEE Server | 50051 | `docker compose up` |
| AOEE Proxy | 8082 | `docker compose up` |
| Redis | 6379 | `brew services` |
| Kafka | 9092 | `brew services` |
| Ollama | 11434 | manual (`ollama serve`) |
| MinIO | 9000/9001 | `docker-compose.data-warehouse.yml` |
| Hive Metastore | 9083 | `docker-compose.data-warehouse.yml` |
| Trino | 8081 | `docker-compose.data-warehouse.yml` |
| Spark Consumer | — | `docker-compose.data-warehouse.yml` |

## Verifying Infrastructure

After `./scripts/start-all.sh --no-app`, verify all services are healthy:

```bash
# Quick check
redis-cli ping                                          # PONG
curl -s http://localhost:9200/_cluster/health | jq .status  # green/yellow
curl -s http://localhost:8082/api/stats | jq .             # AOEE stats
curl -s http://localhost:11434/api/tags | jq .models       # Ollama models
docker exec ws-trino trino --execute "SELECT 1"            # Trino
```

## Stopping

```bash
# Stop infrastructure
docker compose down
docker compose -f docker-compose.data-warehouse.yml down
brew services stop kafka
brew services stop redis
```
