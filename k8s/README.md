# WorkSphere Kubernetes Deployment

## Prerequisites

- Kubernetes cluster (minikube, kind, EKS, GKE, etc.)
- kubectl configured
- Docker images built and available

## Build Docker Images

```bash
# From the project root:

# 1. Build social-app image
cd social-platform
mvn clean package -DskipTests
docker build -t worksphere/social-app:latest -f Dockerfile .

# 2. Build frontend image
cd social-frontend
npm install && npm run build
docker build -t worksphere/frontend:latest -f Dockerfile .

# 3. Build AOEE images (from setup-aoee.sh)
cd ../../aoee
docker build -t worksphere/aoee-server:latest -f Dockerfile .
docker build -t worksphere/aoee-proxy:latest -f Dockerfile.proxy .

# 4. Build datagen image (for --generate flag)
cd ../social-platform
docker build -t worksphere/datagen:latest -f Dockerfile.datagen .
```

## Deploy

```bash
# Default: uses ~/data/worksphere
./k8s/deploy.sh

# Deploy and generate test data (200 users, 2K posts, comments, reactions)
./k8s/deploy.sh --generate

# Deploy with large dataset (2K users, 20K posts)
./k8s/deploy.sh --generate-large

# Custom data directory
WORKSPHERE_DATA_DIR=/mnt/storage/worksphere ./k8s/deploy.sh
```

The `WORKSPHERE_DATA_DIR` environment variable controls where persistent data is stored
on the host. It defaults to `$HOME/data/worksphere`. The deploy script creates the
directories automatically.

The `--generate` flag runs a Kubernetes Job after deployment that populates the database
with realistic test data including users, groups, posts, comments, reactions, and
social graph relationships. The first 3 users are admins (password: `password123`).

## Verify

```bash
kubectl -n worksphere get pods
kubectl -n worksphere get services
kubectl -n worksphere logs deployment/social-app --tail=50
```

## Access

Add to /etc/hosts:
```
127.0.0.1 worksphere.local
```

Then visit: http://worksphere.local

## Persistent Data

| Data | Host Path | Purpose |
|------|-----------|---------|
| PostgreSQL | `$WORKSPHERE_DATA_DIR/postgres` | Database files |
| Uploads | `$WORKSPHERE_DATA_DIR/uploads` | User-uploaded images and files |

Both use `hostPath` volumes with `Retain` reclaim policy — data persists across pod restarts and redeployments.

## Services

| Service | Image | Port | Purpose |
|---------|-------|------|---------|
| PostgreSQL | postgres:16 | 5432 | Primary database |
| Redis | redis:7-alpine | 6379 | Caching (L2), Pub/Sub for WebSocket broadcast, feed sorted sets |
| Kafka | bitnami/kafka:3.7 | 9092 | Event streaming (message events, feed fan-out, reactions) |
| OpenSearch | opensearchproject/opensearch:2 | 9200 | Full-text search |
| Ollama | ollama/ollama | 11434 | AI/LLM for Roid bot and summarization |
| AOEE | worksphere/aoee-server + proxy | 50051/8082 | Social graph engine |
| social-app | worksphere/social-app | 8080 | Main API + WebSocket gateway |
| frontend | worksphere/frontend | 80 | React web UI |

## WebSocket

The social-app exposes a STOMP WebSocket endpoint at `/ws` for real-time messaging.
The ingress is configured with WebSocket support (long timeouts, upgrade headers).

## Scaling

```bash
# Scale social-app (stateless, safe to scale)
kubectl -n worksphere scale deployment/social-app --replicas=3

# Scale frontend (stateless)
kubectl -n worksphere scale deployment/frontend --replicas=3

# AOEE and PostgreSQL should remain at 1 replica
```

## Secrets

Update `k8s/secrets.yaml` before deploying to production:
- `postgres-password`: Strong random password
- `jwt-secret`: Random 256-bit string for JWT signing

## Teardown

```bash
kubectl delete -k k8s/
# Data in your worksphere data directory is preserved (Retain policy)
```
