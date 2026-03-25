#!/usr/bin/env bash
set -euo pipefail

WORKSPHERE_DATA_DIR="${WORKSPHERE_DATA_DIR:-$HOME/data/worksphere}"
export WORKSPHERE_DATA_DIR

echo "Using data directory: $WORKSPHERE_DATA_DIR"
mkdir -p "$WORKSPHERE_DATA_DIR/postgres" "$WORKSPHERE_DATA_DIR/uploads"

# Substitute variables and apply via kustomize
kubectl kustomize k8s/ | envsubst '${WORKSPHERE_DATA_DIR}' | kubectl apply -f -

echo "Waiting for rollout..."
kubectl -n worksphere rollout status deployment/social-app
kubectl -n worksphere rollout status deployment/frontend
