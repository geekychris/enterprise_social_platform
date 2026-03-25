#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# Find kubectl — support microk8s alias
if command -v kubectl &>/dev/null; then
    KUBECTL=kubectl
elif command -v microk8s.kubectl &>/dev/null; then
    KUBECTL=microk8s.kubectl
elif command -v microk8s &>/dev/null; then
    KUBECTL="microk8s kubectl"
else
    echo "ERROR: kubectl not found" >&2; exit 1
fi

# Parse arguments
GENERATE_MODE=""
for arg in "$@"; do
    case "$arg" in
        --generate)       GENERATE_MODE="hundreds" ;;
        --generate-large) GENERATE_MODE="thousands" ;;
        --help|-h)
            echo "Usage: $0 [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  --generate        Generate test data (200 users, 2K posts)"
            echo "  --generate-large  Generate large test data (2K users, 20K posts)"
            echo ""
            echo "Environment variables:"
            echo "  WORKSPHERE_DATA_DIR  Host data directory (default: ~/data/worksphere)"
            exit 0
            ;;
    esac
done

# Data directory — default to ~/data/worksphere
export WORKSPHERE_DATA_DIR="${WORKSPHERE_DATA_DIR:-$HOME/data/worksphere}"

echo "==> WorkSphere Kubernetes Deployment"
echo "    Data directory: $WORKSPHERE_DATA_DIR"
echo "    Using: $KUBECTL"
[[ -n "$GENERATE_MODE" ]] && echo "    Data generation: $GENERATE_MODE"

# ── Enable ingress controller if needed (microk8s) ────────────────────
if command -v microk8s &>/dev/null; then
    if ! microk8s status 2>/dev/null | grep -q "ingress.*enabled"; then
        echo "==> Enabling microk8s ingress addon..."
        microk8s enable ingress 2>&1 || true
    fi
fi

# For non-microk8s clusters, check if an ingress controller exists
if ! $KUBECTL get ingressclass 2>/dev/null | grep -q nginx; then
    echo ""
    echo "WARNING: No nginx IngressClass found."
    echo "         Install an ingress controller for external access."
    echo "         e.g. kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.5.1/deploy/static/provider/cloud/deploy.yaml"
    echo ""
fi

# Create host data directories
echo "==> Creating data directories..."
mkdir -p "$WORKSPHERE_DATA_DIR/postgres"
mkdir -p "$WORKSPHERE_DATA_DIR/uploads"

# Process templates: substitute ${WORKSPHERE_DATA_DIR} in manifests
TMPDIR=$(mktemp -d)
trap 'rm -rf "$TMPDIR"' EXIT

echo "==> Processing manifests..."
for f in "$SCRIPT_DIR"/*.yaml; do
    # Skip datagen-job.yaml from kustomize apply — it's applied separately
    [[ "$(basename "$f")" == "datagen-job.yaml" ]] && continue
    envsubst '$WORKSPHERE_DATA_DIR' < "$f" > "$TMPDIR/$(basename "$f")"
done

# Apply with kustomize from the temp directory
echo "==> Applying to Kubernetes..."
$KUBECTL apply -k "$TMPDIR"

echo ""
echo "==> Waiting for pods to start..."
$KUBECTL -n worksphere rollout status statefulset/postgres --timeout=120s 2>/dev/null || true
$KUBECTL -n worksphere rollout status deployment/opensearch --timeout=120s 2>/dev/null || true
$KUBECTL -n worksphere rollout status deployment/aoee-server --timeout=120s 2>/dev/null || true
$KUBECTL -n worksphere rollout status deployment/aoee-proxy --timeout=120s 2>/dev/null || true
$KUBECTL -n worksphere rollout status deployment/social-app --timeout=120s 2>/dev/null || true
$KUBECTL -n worksphere rollout status deployment/frontend --timeout=120s 2>/dev/null || true

# ── Data Generation ──────────────────────────────────────────────────
if [[ -n "$GENERATE_MODE" ]]; then
    echo ""
    echo "==> Running data generation (mode: $GENERATE_MODE)..."

    # Delete any previous datagen job
    $KUBECTL -n worksphere delete job datagen 2>/dev/null || true

    # Apply datagen job with mode substituted
    export DATAGEN_MODE="$GENERATE_MODE"
    envsubst '$DATAGEN_MODE' < "$SCRIPT_DIR/datagen-job.yaml" | $KUBECTL apply -f -

    echo "    Waiting for datagen job to complete..."
    if $KUBECTL -n worksphere wait --for=condition=complete job/datagen --timeout=300s 2>/dev/null; then
        echo "==> Data generation completed successfully!"
        $KUBECTL -n worksphere logs job/datagen --tail=15
    else
        echo "==> Data generation may have failed. Logs:"
        $KUBECTL -n worksphere logs job/datagen --tail=30
    fi
fi

echo ""
echo "==> Current pod status:"
$KUBECTL -n worksphere get pods

echo ""
echo "==> Services:"
$KUBECTL -n worksphere get services

# ── Print access info ─────────────────────────────────────────────────
echo ""
echo "==> Deployment complete!"

# Detect host IP for LAN access
HOST_IP=""
if command -v hostname &>/dev/null; then
    HOST_IP=$(hostname -I 2>/dev/null | awk '{print $1}')
fi

echo ""
echo "    Access WorkSphere:"
echo "      Local:   http://localhost"
if [[ -n "$HOST_IP" ]]; then
    echo "      Network: http://$HOST_IP"
fi
echo ""
echo "    The ingress controller listens on port 80/443 on this machine."
echo "    Any computer on the same network can access it via the network URL above."
if [[ -n "$GENERATE_MODE" ]]; then
    echo ""
    echo "    Test accounts (password: password123):"
    echo "      Admin: lamar.lehner, joshua.padberg, cecilia.watsica"
    echo "      Or use Debug Mode on the login page with user ID: 72057594037927937"
fi
