# WorkSphere — AWS Deployment

## Architecture

WorkSphere on AWS uses managed services where possible:

- **ECS Fargate** — serverless containers for all application services
- **RDS PostgreSQL** — managed database (replaces self-hosted PostgreSQL)
- **S3 + CloudFront** — upload storage and CDN (replaces local /uploads)
- **Application Load Balancer** — HTTPS termination and routing
- **OpenSearch Service** — managed search (optional, can use self-hosted)
- **ECR** — private container registry
- **Secrets Manager** — credentials management
- **VPC** — isolated networking with public/private subnets

### Architecture Diagram

```
                    ┌─────────────┐
                    │ CloudFront  │
                    │   (CDN)     │
                    └──────┬──────┘
                           │
                    ┌──────┴──────┐
         ┌─────────┤     ALB     ├─────────┐
         │         └─────────────┘         │
         │ /api, /graphql                  │ /
         │                                 │
    ┌────┴────┐                    ┌───────┴───────┐
    │ ECS:    │                    │ ECS:          │
    │social-app│                   │ frontend      │
    │(Fargate) │                   │ (Fargate)     │
    └────┬────┘                    └───────────────┘
         │
    ┌────┴──────────┬──────────────┐
    │               │              │
┌───┴───┐    ┌──────┴──────┐  ┌───┴────┐
│  RDS  │    │ ECS: AOEE   │  │   S3   │
│Postgres│   │ server+proxy│  │uploads │
└───────┘    └─────────────┘  └────────┘
```

## Prerequisites

- AWS CLI configured with appropriate credentials
- Node.js 18+
- AWS CDK CLI: `npm install -g aws-cdk`
- Docker (for building images)

## Quick Start

```bash
cd aws

# Install dependencies
npm install

# Bootstrap CDK (first time only)
cdk bootstrap

# Build and push Docker images to ECR
./scripts/build-and-push.sh

# Deploy infrastructure + services
cdk deploy --all

# Get outputs (ALB URL, etc.)
cdk output
```

## Stack Structure

| Stack | Resources |
|-------|-----------|
| `WorkSphereVpcStack` | VPC, subnets, NAT gateway, security groups |
| `WorkSphereDataStack` | RDS PostgreSQL, S3 bucket, Secrets Manager |
| `WorkSphereEcrStack` | ECR repositories for all images |
| `WorkSphereEcsStack` | ECS cluster, Fargate services, ALB, CloudFront |

## Configuration

Environment variables in `cdk.json`:
```json
{
  "context": {
    "environment": "production",
    "dbInstanceClass": "db.t3.medium",
    "dbAllocatedStorage": 20,
    "appDesiredCount": 2,
    "frontendDesiredCount": 2,
    "domainName": "worksphere.example.com"
  }
}
```

## Costs (estimated)

| Service | Estimated Monthly Cost |
|---------|----------------------|
| RDS db.t3.medium | ~$30 |
| ECS Fargate (4 services) | ~$60 |
| ALB | ~$20 |
| S3 + CloudFront | ~$5 |
| NAT Gateway | ~$35 |
| **Total** | **~$150/month** |

## Destroy

```bash
cdk destroy --all
```

Note: RDS has deletion protection enabled by default. Disable it in the console before destroying, or set `removalPolicy: RemovalPolicy.DESTROY` in the CDK code for dev environments.
