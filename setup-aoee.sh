#!/bin/bash
set -euo pipefail

# Setup script for AOEE (Attribute Object Enterprise Edition)
# Clones, builds, and configures AOEE to work with the social platform

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
AOEE_DIR="${SCRIPT_DIR}/aoee"
AOEE_REPO="https://github.com/geekychris/AOEE_Attribute_object_enterprise_edition.git"

echo "=== AOEE Setup for Social Enterprise Platform ==="
echo ""

# Step 1: Clone AOEE if not present
if [ -d "$AOEE_DIR" ]; then
    echo "[1/4] AOEE already cloned at $AOEE_DIR"
    cd "$AOEE_DIR" && git pull --ff-only 2>/dev/null || true
else
    echo "[1/4] Cloning AOEE from $AOEE_REPO..."
    git clone "$AOEE_REPO" "$AOEE_DIR"
fi

echo ""

# Step 2: Build AOEE Rust server
echo "[2/4] Building AOEE Rust server..."
cd "$AOEE_DIR"
if command -v cargo &>/dev/null; then
    cd aoee && cargo build --release 2>&1 | tail -5
    echo "  Built: $(ls -la target/release/aoee-server 2>/dev/null || echo 'binary not found')"
    cd ..
else
    echo "  WARNING: Rust/Cargo not installed. Install via: curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh"
    echo "  Skipping Rust build. You can use Docker instead (docker-compose --profile aoee up)"
fi

echo ""

# Step 3: Build AOEE Java client & Spring proxy
echo "[3/4] Building AOEE Java client and Spring proxy..."
cd "$AOEE_DIR"
if [ -f "build-java.sh" ]; then
    bash build-java.sh --no-clean 2>&1 | tail -10
else
    # Manual build
    if [ -d "aoee-java-client" ]; then
        cd aoee-java-client && mvn install -DskipTests -q && cd ..
        echo "  Installed aoee-java-client to local Maven repo"
    fi
    if [ -d "aoee-spring" ]; then
        cd aoee-spring && mvn package -DskipTests -q && cd ..
        echo "  Built aoee-spring proxy"
    fi
fi

echo ""

# Step 4: Create Dockerfiles if they don't exist (for docker-compose)
echo "[4/4] Creating Docker configuration..."

# Dockerfile for AOEE Rust server
cat > "$AOEE_DIR/Dockerfile" << 'DOCKERFILE'
FROM rust:1.77-slim-bookworm AS builder
WORKDIR /app
COPY aoee/ ./aoee/
WORKDIR /app/aoee
RUN cargo build --release

FROM debian:bookworm-slim
RUN apt-get update && apt-get install -y ca-certificates && rm -rf /var/lib/apt/lists/*
COPY --from=builder /app/aoee/target/release/aoee-server /usr/local/bin/aoee-server
EXPOSE 50051
CMD ["aoee-server"]
DOCKERFILE

# Dockerfile for AOEE Spring proxy
cat > "$AOEE_DIR/Dockerfile.proxy" << 'DOCKERFILE'
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY aoee-java-client/ ./aoee-java-client/
COPY aoee-spring/ ./aoee-spring/
RUN cd aoee-java-client && mvn install -DskipTests -q
RUN cd aoee-spring && mvn package -DskipTests -q

FROM eclipse-temurin:21-jre
COPY --from=builder /app/aoee-spring/target/*.jar /app/aoee-proxy.jar
EXPOSE 8081
CMD ["java", "-jar", "/app/aoee-proxy.jar", "--server.port=8081"]
DOCKERFILE

echo "  Created Dockerfile and Dockerfile.proxy"

echo ""
echo "=== Setup Complete ==="
echo ""
echo "To run AOEE locally (without Docker):"
echo "  # Terminal 1: Start AOEE Rust server (configured to use our social-app as persistence backend)"
echo "  export AOEE_LISTEN_ADDR=127.0.0.1:50051"
echo "  export AOEE_STORAGE_TYPE=http"
echo "  export AOEE_HTTP_URL=http://localhost:8080"
echo "  export AOEE_WRITE_THROUGH=true"
echo "  $AOEE_DIR/aoee/target/release/aoee-server"
echo ""
echo "  # Terminal 2: Start AOEE Spring proxy"
echo "  cd $AOEE_DIR/aoee-spring"
echo "  mvn spring-boot:run -Dspring-boot.run.arguments='--server.port=8081 --aoee.grpc.host=localhost --aoee.grpc.port=50051'"
echo ""
echo "To run with Docker:"
echo "  cd $SCRIPT_DIR"
echo "  docker-compose --profile aoee up -d"
echo ""
echo "AOEE will call our social-app at http://localhost:8080/api/v1/edges for edge persistence."
echo "Our social-app calls the AOEE proxy at http://localhost:8081/api/edges for graph queries."
