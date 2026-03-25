# WorkSphere — AWS Deployment Architecture

## Overview

WorkSphere deploys to AWS using managed services where possible, with all infrastructure defined as code using AWS CDK (TypeScript).

```mermaid
graph TB
    subgraph "Edge"
        CF["CloudFront<br/>(CDN + HTTPS)"]
    end

    subgraph "Load Balancing"
        ALB["Application Load Balancer"]
    end

    subgraph "ECS Fargate Cluster"
        FE["Frontend<br/>(nginx, 2 replicas)"]
        APP["Social App<br/>(Spring Boot, 2 replicas)"]
        AProxy["AOEE Proxy<br/>(Spring Boot, 1 replica)"]
        AServer["AOEE Server<br/>(Rust gRPC, 1 replica)"]
    end

    subgraph "Data Layer"
        RDS["RDS PostgreSQL 16<br/>(db.t3.medium)"]
        S3["S3 Bucket<br/>(uploads)"]
    end

    subgraph "Discovery"
        CM["Cloud Map<br/>(service discovery)"]
    end

    CF --> ALB
    ALB -->|"/"| FE
    ALB -->|"/api/*, /graphql"| APP
    CF -->|"/uploads/*"| S3

    APP --> RDS
    APP -->|"REST"| AProxy
    AProxy -->|"gRPC"| AServer
    AServer -->|"HTTP callback"| APP

    FE -.-> CM
    APP -.-> CM
    AProxy -.-> CM
    AServer -.-> CM
```

## CDK Stack Structure

```mermaid
graph LR
    subgraph "Stack Dependencies"
        VPC["WorkSphereVpcStack<br/>VPC, subnets, NAT"]
        Data["WorkSphereDataStack<br/>RDS, S3, Secrets"]
        ECR["WorkSphereEcrStack<br/>Container registries"]
        ECS["WorkSphereEcsStack<br/>Fargate, ALB, CloudFront"]
    end

    VPC --> Data
    VPC --> ECS
    Data --> ECS
    ECR --> ECS
```

| Stack | Resources | Depends On |
|-------|-----------|-----------|
| `WorkSphereVpcStack` | VPC (2 AZs), public/private/isolated subnets, NAT Gateway | — |
| `WorkSphereDataStack` | RDS PostgreSQL 16, S3 bucket, Secrets Manager (DB + JWT) | VPC |
| `WorkSphereEcrStack` | 4 ECR repositories | — |
| `WorkSphereEcsStack` | ECS cluster, 4 Fargate services, ALB, CloudFront | VPC, Data, ECR |

## Networking

```mermaid
graph TB
    subgraph "VPC 10.0.0.0/16"
        subgraph "Public Subnets"
            ALB2["ALB"]
            NAT["NAT Gateway"]
        end
        subgraph "Private Subnets (with NAT egress)"
            ECS2["ECS Fargate Tasks<br/>(social-app, frontend,<br/>AOEE server, AOEE proxy)"]
        end
        subgraph "Isolated Subnets (no internet)"
            RDS2["RDS PostgreSQL"]
        end
    end

    ALB2 --> ECS2
    ECS2 --> NAT
    ECS2 --> RDS2
```

- **Public subnets**: ALB, NAT Gateway
- **Private subnets**: All Fargate tasks (have internet via NAT for pulling images, calling external APIs)
- **Isolated subnets**: RDS (no internet access, only reachable from private subnets)

## Service Communication

```mermaid
sequenceDiagram
    participant Client
    participant CF as CloudFront
    participant ALB
    participant FE as Frontend (Fargate)
    participant App as Social App (Fargate)
    participant AOEE as AOEE Proxy (Fargate)
    participant RDS as RDS PostgreSQL
    participant S3

    Client->>CF: HTTPS request
    alt Static content (/, /index.html, /assets)
        CF->>ALB: Forward to frontend
        ALB->>FE: Serve React SPA
        FE-->>Client: HTML/JS/CSS
    else API request (/api/*, /graphql)
        CF->>ALB: Forward to app
        ALB->>App: REST/GraphQL
        App->>RDS: Query/persist
        App->>AOEE: Graph queries
        App-->>Client: JSON response
    else Upload (/uploads/*)
        CF->>S3: Serve from cache/origin
        S3-->>Client: File (cached at edge)
    end
```

### Service Discovery (Cloud Map)

ECS services register with AWS Cloud Map for internal DNS:

| Service | DNS Name | Port |
|---------|----------|------|
| social-app | `social-app.worksphere.local` | 8080 |
| aoee-proxy | `aoee-proxy.worksphere.local` | 8082 |
| aoee-server | `aoee-server.worksphere.local` | 50051 |

## Data Persistence

### RDS PostgreSQL

| Setting | Value |
|---------|-------|
| Engine | PostgreSQL 16.2 |
| Instance | db.t3.medium (configurable) |
| Storage | 20 GB (auto-scales to 100 GB) |
| Backup | 7-day retention |
| Multi-AZ | No (configurable) |
| Deletion Protection | Enabled |
| Subnet | Isolated (no internet) |

Flyway migrations run automatically on social-app startup.

### S3 (Uploads)

| Setting | Value |
|---------|-------|
| Bucket | `worksphere-uploads-{account-id}` |
| Encryption | S3-managed |
| Public Access | Blocked |
| Versioning | Disabled |
| Access | Via CloudFront (cached) + social-app IAM role |

### Secrets Manager

| Secret | Purpose |
|--------|---------|
| `worksphere/db-credentials` | RDS username + auto-generated password |
| `worksphere/jwt-secret` | JWT signing key (auto-generated) |

## ALB Routing

```mermaid
flowchart LR
    ALB["ALB :80"]
    ALB -->|"/api/*<br/>/graphql<br/>/graphiql<br/>/uploads/*<br/>(priority 10)"| App["social-app:8080"]
    ALB -->|"/* (default)"| FE["frontend:80"]
```

## Deployment Process

```mermaid
flowchart TB
    subgraph "Build Phase"
        B1["mvn package<br/>(social-app JAR)"]
        B2["npm run build<br/>(React SPA)"]
        B3["setup-aoee.sh<br/>(fetch AOEE)"]
    end

    subgraph "Docker Phase"
        D1["docker build social-app"]
        D2["docker build frontend"]
        D3["docker build aoee-server"]
        D4["docker build aoee-proxy"]
    end

    subgraph "Push Phase"
        P["docker push → ECR"]
    end

    subgraph "Deploy Phase"
        CDK["cdk deploy --all"]
    end

    B1 --> D1
    B2 --> D2
    B3 --> D3
    B3 --> D4
    D1 --> P
    D2 --> P
    D3 --> P
    D4 --> P
    P --> CDK
```

### Commands

```bash
# First-time setup
cd aws
npm install
cdk bootstrap

# Build and push images
./scripts/build-and-push.sh

# Deploy/update infrastructure
cdk deploy --all

# View outputs
aws cloudformation describe-stacks \
  --stack-name WorkSphereEcs \
  --query 'Stacks[0].Outputs'
```

## Scaling

| Service | Default | Scalable | Notes |
|---------|---------|----------|-------|
| social-app | 2 | Yes | Stateless, safe to scale horizontally |
| frontend | 2 | Yes | Stateless nginx |
| aoee-server | 1 | No | In-memory cache, single instance |
| aoee-proxy | 1 | Yes | Stateless proxy |
| RDS | 1 | Vertical | Change instance class; enable Multi-AZ for HA |

To scale:
```bash
# Update desired count in cdk.json context
# Or use AWS CLI:
aws ecs update-service \
  --cluster worksphere \
  --service social-app \
  --desired-count 4
```

## Estimated Costs

| Service | Monthly Cost |
|---------|-------------|
| RDS db.t3.medium | ~$30 |
| ECS Fargate (4 services) | ~$60 |
| ALB | ~$20 |
| NAT Gateway | ~$35 |
| S3 + CloudFront | ~$5 |
| Secrets Manager | ~$2 |
| CloudWatch Logs | ~$5 |
| **Total** | **~$157/month** |

*Based on us-west-2 pricing. Costs vary by region and usage.*

## Security

- All traffic HTTPS via CloudFront
- RDS in isolated subnets (no internet access)
- S3 bucket blocks all public access
- Secrets in AWS Secrets Manager (not environment variables)
- ECS tasks use IAM roles (not access keys)
- Security groups restrict traffic to required ports only
- `debug-bypass` disabled in production

## Differences from Local Development

| Aspect | Local (docker-compose) | AWS (CDK) |
|--------|----------------------|-----------|
| Database | PostgreSQL container | RDS PostgreSQL (managed) |
| Uploads | Local filesystem | S3 + CloudFront CDN |
| Search | OpenSearch container | Self-hosted on Fargate (or Amazon OpenSearch Service) |
| Networking | Docker bridge | VPC with public/private/isolated subnets |
| Secrets | Plaintext in env | AWS Secrets Manager |
| Load balancing | Vite proxy | ALB with health checks |
| HTTPS | None | CloudFront with auto-certs |
| Containers | Docker Compose | ECS Fargate (serverless) |
| Scaling | Manual | ECS desired count / auto-scaling |
| Auth debug | Enabled | Disabled |
