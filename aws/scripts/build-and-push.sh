#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# Get AWS account and region
ACCOUNT=$(aws sts get-caller-identity --query Account --output text)
REGION=${AWS_DEFAULT_REGION:-us-west-2}
ECR_BASE="${ACCOUNT}.dkr.ecr.${REGION}.amazonaws.com"

echo "=== Building and pushing WorkSphere Docker images ==="
echo "  Account: $ACCOUNT"
echo "  Region:  $REGION"
echo "  ECR:     $ECR_BASE"
echo ""

# Login to ECR
aws ecr get-login-password --region "$REGION" | docker login --username AWS --password-stdin "$ECR_BASE"

# 1. Build social-app
echo "[1/4] Building social-app..."
cd "$PROJECT_ROOT/social-platform"
mvn clean package -DskipTests -q
docker build -t "$ECR_BASE/worksphere/social-app:latest" -f Dockerfile .
docker push "$ECR_BASE/worksphere/social-app:latest"
echo "  Pushed social-app"

# 2. Build frontend
echo "[2/4] Building frontend..."
cd "$PROJECT_ROOT/social-platform/social-frontend"
npm install --silent 2>/dev/null
npm run build
docker build -t "$ECR_BASE/worksphere/frontend:latest" -f Dockerfile .
docker push "$ECR_BASE/worksphere/frontend:latest"
echo "  Pushed frontend"

# 3. Build AOEE server
echo "[3/4] Building aoee-server..."
cd "$PROJECT_ROOT"
bash setup-aoee.sh 2>/dev/null || true
cd aoee
docker build -t "$ECR_BASE/worksphere/aoee-server:latest" -f Dockerfile .
docker push "$ECR_BASE/worksphere/aoee-server:latest"
echo "  Pushed aoee-server"

# 4. Build AOEE proxy
echo "[4/4] Building aoee-proxy..."
docker build -t "$ECR_BASE/worksphere/aoee-proxy:latest" -f Dockerfile.proxy .
docker push "$ECR_BASE/worksphere/aoee-proxy:latest"
echo "  Pushed aoee-proxy"

echo ""
echo "=== All images pushed to ECR ==="
