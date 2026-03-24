#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="${SCRIPT_DIR}/logs"
AOEE_DIR="${SCRIPT_DIR}/aoee"
AOEE_REPO="https://github.com/geekychris/AOEE_Attribute_object_enterprise_edition.git"
mkdir -p "$LOG_DIR"

usage() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  --infra-only     Start only infrastructure (PostgreSQL, OpenSearch)"
    echo "  --with-aoee      Also start AOEE graph cache services (Docker)"
    echo "  --no-frontend    Skip frontend dev server"
    echo "  --generate       Generate test data (hundreds mode)"
    echo "  --generate-large Generate test data (thousands mode)"
    echo "  --stop           Stop all services"
    echo "  -h, --help       Show this help"
}

stop_all() {
    echo "Stopping services..."
    cd "$SCRIPT_DIR"
    docker-compose down 2>/dev/null || true
    # Kill background Java/Node processes we started
    for pidfile in "$LOG_DIR"/*.pid; do
        if [ -f "$pidfile" ]; then
            pid=$(cat "$pidfile")
            kill "$pid" 2>/dev/null || true
            rm -f "$pidfile"
        fi
    done
    echo "All services stopped."
    exit 0
}

wait_for_url() {
    local url=$1
    local name=$2
    local max_wait=${3:-60}
    local elapsed=0
    while ! curl -sf "$url" >/dev/null 2>&1; do
        sleep 2
        elapsed=$((elapsed + 2))
        if [ $elapsed -ge $max_wait ]; then
            echo "  ERROR: $name did not start within ${max_wait}s. Check $LOG_DIR/"
            return 1
        fi
    done
}

# Ensure AOEE is cloned from git (lightweight check — no build)
ensure_aoee() {
    if [ -d "$AOEE_DIR/.git" ]; then
        echo "  AOEE already present, pulling latest..."
        cd "$AOEE_DIR" && git pull --ff-only 2>/dev/null || true
    else
        echo "  Cloning AOEE from $AOEE_REPO..."
        git clone "$AOEE_REPO" "$AOEE_DIR"
    fi
}

INFRA_ONLY=false
WITH_AOEE=false
NO_FRONTEND=false
GENERATE=""

for arg in "$@"; do
    case $arg in
        --infra-only) INFRA_ONLY=true ;;
        --with-aoee) WITH_AOEE=true ;;
        --no-frontend) NO_FRONTEND=true ;;
        --generate) GENERATE="hundreds" ;;
        --generate-large) GENERATE="thousands" ;;
        --stop) stop_all ;;
        -h|--help) usage; exit 0 ;;
    esac
done

echo "=== Starting Social Enterprise Platform ==="
echo ""

# Step 0: Ensure AOEE is available from git
echo "[0/5] Ensuring AOEE is available..."
ensure_aoee
echo ""

# Step 1: Infrastructure
echo "[1/5] Starting PostgreSQL and OpenSearch..."
cd "$SCRIPT_DIR"
docker-compose up -d postgres opensearch 2>&1 | grep -v "^$" | tail -5

echo "  Waiting for PostgreSQL..."
until docker exec social-postgres pg_isready -U social -d social_enterprise -q 2>/dev/null; do
    sleep 1
done
echo "  PostgreSQL ready (port 5432)."

echo "  Waiting for OpenSearch..."
wait_for_url "http://localhost:9200/_cluster/health" "OpenSearch" 60
echo "  OpenSearch ready (port 9200)."

# Optionally start AOEE Docker services
if [ "$WITH_AOEE" = true ]; then
    echo ""
    echo "  Starting AOEE services (Docker build)..."
    # Ensure Dockerfiles exist
    if [ ! -f "$AOEE_DIR/Dockerfile" ]; then
        echo "  Running setup-aoee.sh to create Dockerfiles..."
        bash "$SCRIPT_DIR/setup-aoee.sh" 2>&1 | tail -5
    fi
    docker-compose up -d aoee-server aoee-proxy 2>&1 | grep -v "^$" | tail -5
    echo "  AOEE services starting (gRPC :50051, proxy :8082)."
fi

if [ "$INFRA_ONLY" = true ]; then
    echo ""
    echo "Infrastructure started."
    exit 0
fi

# Step 2: Build if needed
if [ ! -f "$SCRIPT_DIR/social-platform/social-app/target/social-app-1.0.0-SNAPSHOT.jar" ]; then
    echo ""
    echo "[2/5] Building backend (first run)..."
    cd "$SCRIPT_DIR/social-platform"
    mvn package -DskipTests -q
    echo "  Build complete."
else
    echo ""
    echo "[2/5] Backend already built."
fi

# Step 3: Start social-app
echo ""
echo "[3/5] Starting social-app..."
cd "$SCRIPT_DIR/social-platform"

# Kill any existing instance
if [ -f "$LOG_DIR/social-app.pid" ]; then
    kill "$(cat "$LOG_DIR/social-app.pid")" 2>/dev/null || true
fi

UPLOAD_DIR="$SCRIPT_DIR/uploads"
mkdir -p "$UPLOAD_DIR"
java -jar social-app/target/social-app-1.0.0-SNAPSHOT.jar \
    --spring.datasource.url=jdbc:postgresql://localhost:5432/social_enterprise \
    --spring.datasource.username=social \
    --spring.datasource.password=social_dev_password \
    --social.media.upload-dir="$UPLOAD_DIR" \
    > "$LOG_DIR/social-app.log" 2>&1 &
echo $! > "$LOG_DIR/social-app.pid"

echo "  Waiting for social-app..."
wait_for_url "http://localhost:8080/actuator/health" "social-app" 60
echo "  social-app ready (port 8080)."

# Step 4: Start frontend
if [ "$NO_FRONTEND" = false ]; then
    echo ""
    echo "[4/5] Starting frontend dev server..."
    cd "$SCRIPT_DIR/social-platform/social-frontend"

    if [ -f "$LOG_DIR/frontend.pid" ]; then
        kill "$(cat "$LOG_DIR/frontend.pid")" 2>/dev/null || true
    fi

    npx vite --host 2>/dev/null > "$LOG_DIR/frontend.log" 2>&1 &
    echo $! > "$LOG_DIR/frontend.pid"
    sleep 3
    echo "  Frontend ready (port 5173)."
else
    echo ""
    echo "[4/5] Skipping frontend."
fi

# Optional: generate data
if [ -n "$GENERATE" ]; then
    echo ""
    echo "[5/5] Generating test data (mode=$GENERATE)..."
    cd "$SCRIPT_DIR/social-platform"
    java -jar social-datagen/target/social-datagen-1.0.0-SNAPSHOT.jar \
        --mode="$GENERATE" \
        --spring.datasource.url=jdbc:postgresql://localhost:5432/social_enterprise \
        --spring.datasource.username=social \
        --spring.datasource.password=social_dev_password \
        2>&1 | tail -30
    echo "  Data generation complete."
else
    echo ""
    echo "[5/5] Skipping data generation (use --generate to enable)."
fi

echo ""
echo "=== Social Enterprise Platform Running ==="
echo ""
echo "  App:        http://localhost:8080"
echo "  Frontend:   http://localhost:5173"
echo "  GraphiQL:   http://localhost:8080/graphiql"
echo "  OpenSearch:  http://localhost:9200"
echo "  PostgreSQL:  localhost:5432"
if [ "$WITH_AOEE" = true ]; then
echo "  AOEE gRPC:   localhost:50051"
echo "  AOEE proxy:  http://localhost:8082"
fi
echo ""
echo "  Debug login: Use X-Debug-User-Id header or debug mode on login page"
echo "  Logs:        $LOG_DIR/"
echo "  Stop:        $0 --stop"
