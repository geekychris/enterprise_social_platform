# WorkSphere WebSocket Gateway (Spring Boot WebFlux + Netty)
# Multi-stage: build the fat JAR inside a maven builder image, then run it on a slim JRE.
# Build context is social-platform/ws-gateway/ (declared in ESP's spec.docker.images).
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /src
COPY pom.xml /src/pom.xml
# Pre-fetch dependencies into the cache layer so iterative builds skip the network.
RUN mvn -B -DskipTests dependency:go-offline || true
COPY src /src/src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /src/target/ws-gateway-1.0.0-SNAPSHOT.jar /app/app.jar
ENV SERVER_PORT=8090 \
    WS_GATEWAY_PORT=8090
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
