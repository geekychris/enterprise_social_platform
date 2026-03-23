#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

usage() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  --skip-aoee      Skip AOEE fetch/build"
    echo "  --skip-frontend   Skip frontend build"
    echo "  --aoee-only       Only fetch and build AOEE"
    echo "  -h, --help        Show this help"
}

SKIP_AOEE=false
SKIP_FRONTEND=false
AOEE_ONLY=false

for arg in "$@"; do
    case $arg in
        --skip-aoee) SKIP_AOEE=true ;;
        --skip-frontend) SKIP_FRONTEND=true ;;
        --aoee-only) AOEE_ONLY=true ;;
        -h|--help) usage; exit 0 ;;
    esac
done

echo "=== Building Social Enterprise Platform ==="
echo ""

# Step 1: Fetch and build AOEE from git
if [ "$SKIP_AOEE" = false ]; then
    echo "[1/3] Fetching and building AOEE..."
    bash "$SCRIPT_DIR/setup-aoee.sh"
    echo ""
else
    echo "[1/3] Skipping AOEE (--skip-aoee)"
    echo ""
fi

if [ "$AOEE_ONLY" = true ]; then
    echo "=== AOEE Build Complete ==="
    exit 0
fi

# Step 2: Build Java backend
echo "[2/3] Building Java backend (social-core, social-app, social-datagen)..."
cd "$SCRIPT_DIR/social-platform"
mvn clean package -DskipTests -q
echo "  Backend built successfully."

# Step 3: Build React frontend
if [ "$SKIP_FRONTEND" = false ]; then
    echo ""
    echo "[3/3] Building React frontend..."
    cd "$SCRIPT_DIR/social-platform/social-frontend"
    npm install --silent 2>/dev/null
    npm run build
    echo "  Frontend built successfully."
else
    echo ""
    echo "[3/3] Skipping frontend (--skip-frontend)"
fi

echo ""
echo "=== Build Complete ==="
echo "  Backend JARs in social-platform/*/target/"
echo "  Frontend assets in social-platform/social-frontend/target/classes/static/"
echo "  AOEE binaries in aoee/ (fetched from git)"
