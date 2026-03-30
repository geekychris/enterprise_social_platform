import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as ecs from 'aws-cdk-lib/aws-ecs';
import * as elbv2 from 'aws-cdk-lib/aws-elasticloadbalancingv2';
import * as rds from 'aws-cdk-lib/aws-rds';
import * as s3 from 'aws-cdk-lib/aws-s3';
import * as secretsmanager from 'aws-cdk-lib/aws-secretsmanager';
import * as iam from 'aws-cdk-lib/aws-iam';
import * as logs from 'aws-cdk-lib/aws-logs';
import * as cloudfront from 'aws-cdk-lib/aws-cloudfront';
import * as origins from 'aws-cdk-lib/aws-cloudfront-origins';
import { Construct } from 'constructs';
import { EcrRepos } from './ecr-stack';

interface EcsStackProps extends cdk.StackProps {
  vpc: ec2.Vpc;
  database: rds.DatabaseInstance;
  uploadsBucket: s3.Bucket;
  warehouseBucket: s3.Bucket;
  dbSecret: secretsmanager.ISecret;
  jwtSecret: secretsmanager.Secret;
  ecrRepos: EcrRepos;
  redisEndpoint: string;
  redisPort: string;
  opensearchEndpoint: string;
  kafkaBootstrap: string;
  kafkaSecurityGroup: ec2.SecurityGroup;
  redisSecurityGroup: ec2.SecurityGroup;
  opensearchSecurityGroup: ec2.SecurityGroup;
  glueDatabaseName: string;
}

export class EcsStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props: EcsStackProps) {
    super(scope, id, props);

    const appDesiredCount = this.node.tryGetContext('appDesiredCount') ?? 2;
    const frontendDesiredCount = this.node.tryGetContext('frontendDesiredCount') ?? 2;

    // ECS Cluster with Cloud Map namespace for service discovery
    const cluster = new ecs.Cluster(this, 'Cluster', {
      vpc: props.vpc,
      clusterName: 'worksphere',
      containerInsights: true,
      defaultCloudMapNamespace: { name: 'worksphere.local' },
    });

    // Log groups
    const appLogGroup = new logs.LogGroup(this, 'AppLogs', {
      logGroupName: '/worksphere/social-app',
      retention: logs.RetentionDays.TWO_WEEKS,
      removalPolicy: cdk.RemovalPolicy.DESTROY,
    });

    const pipelineLogGroup = new logs.LogGroup(this, 'PipelineLogs', {
      logGroupName: '/worksphere/pipeline',
      retention: logs.RetentionDays.TWO_WEEKS,
      removalPolicy: cdk.RemovalPolicy.DESTROY,
    });

    // ALB
    const alb = new elbv2.ApplicationLoadBalancer(this, 'ALB', {
      vpc: props.vpc,
      internetFacing: true,
    });

    const listener = alb.addListener('HttpListener', { port: 80 });

    // ── AOEE Server ──────────────────────────────────────────────

    const aoeeServerTask = new ecs.FargateTaskDefinition(this, 'AoeeServerTask', {
      memoryLimitMiB: 512,
      cpu: 256,
    });

    aoeeServerTask.addContainer('aoee-server', {
      image: ecs.ContainerImage.fromEcrRepository(props.ecrRepos.aoeeServer),
      portMappings: [{ containerPort: 50051 }],
      environment: {
        AOEE_LISTEN_ADDR: '0.0.0.0:50051',
        AOEE_STORAGE_TYPE: 'http',
        AOEE_HTTP_URL: 'http://social-app.worksphere.local:8080',
        AOEE_WRITE_THROUGH: 'true',
        RUST_LOG: 'info',
      },
      logging: ecs.LogDrivers.awsLogs({ streamPrefix: 'aoee-server', logGroup: appLogGroup }),
    });

    const aoeeServerService = new ecs.FargateService(this, 'AoeeServerService', {
      cluster,
      taskDefinition: aoeeServerTask,
      desiredCount: 1,
      cloudMapOptions: { name: 'aoee-server' },
    });

    // ── AOEE Proxy ───────────────────────────────────────────────

    const aoeeProxyTask = new ecs.FargateTaskDefinition(this, 'AoeeProxyTask', {
      memoryLimitMiB: 512,
      cpu: 256,
    });

    aoeeProxyTask.addContainer('aoee-proxy', {
      image: ecs.ContainerImage.fromEcrRepository(props.ecrRepos.aoeeProxy),
      portMappings: [{ containerPort: 8082 }],
      environment: {
        AOEE_HOST: 'aoee-server.worksphere.local',
        AOEE_PORT: '50051',
        SERVER_PORT: '8082',
      },
      logging: ecs.LogDrivers.awsLogs({ streamPrefix: 'aoee-proxy', logGroup: appLogGroup }),
    });

    const aoeeProxyService = new ecs.FargateService(this, 'AoeeProxyService', {
      cluster,
      taskDefinition: aoeeProxyTask,
      desiredCount: 1,
      cloudMapOptions: { name: 'aoee-proxy' },
    });

    // ── Social App ───────────────────────────────────────────────

    const appTask = new ecs.FargateTaskDefinition(this, 'AppTask', {
      memoryLimitMiB: 2048,
      cpu: 1024,
    });

    // Grant S3 access for media uploads + warehouse reads
    props.uploadsBucket.grantReadWrite(appTask.taskRole);
    props.warehouseBucket.grantRead(appTask.taskRole);

    const dbUrl = `jdbc:postgresql://${props.database.dbInstanceEndpointAddress}:5432/social_enterprise`;

    appTask.addContainer('social-app', {
      image: ecs.ContainerImage.fromEcrRepository(props.ecrRepos.socialApp),
      portMappings: [{ containerPort: 8080 }],
      environment: {
        // Database
        SPRING_DATASOURCE_URL: dbUrl,
        // Redis
        REDIS_HOST: props.redisEndpoint,
        REDIS_PORT: props.redisPort,
        // Kafka
        KAFKA_SERVERS: props.kafkaBootstrap,
        // OpenSearch (Amazon OpenSearch uses HTTPS on 443)
        SOCIAL_OPENSEARCH_HOST: props.opensearchEndpoint,
        SOCIAL_OPENSEARCH_PORT: '443',
        // AOEE
        SOCIAL_AOEE_HOST: 'aoee-proxy.worksphere.local',
        SOCIAL_AOEE_PROXY_PORT: '8082',
        // S3 media storage (uses real S3, not MinIO)
        S3_ENDPOINT: `https://s3.${this.region}.amazonaws.com`,
        S3_BUCKET: props.uploadsBucket.bucketName,
        S3_REGION: this.region!,
        // No access key/secret needed — uses IAM task role
        // AI (Ollama not available on AWS; use Bedrock or disable)
        SOCIAL_AI_OLLAMA_URL: 'http://ollama.worksphere.local:11434',
        // Security
        SOCIAL_AUTH_DEBUG_BYPASS: 'false',
      },
      secrets: {
        SPRING_DATASOURCE_USERNAME: ecs.Secret.fromSecretsManager(props.dbSecret, 'username'),
        SPRING_DATASOURCE_PASSWORD: ecs.Secret.fromSecretsManager(props.dbSecret, 'password'),
        SOCIAL_JWT_SECRET: ecs.Secret.fromSecretsManager(props.jwtSecret),
      },
      logging: ecs.LogDrivers.awsLogs({ streamPrefix: 'social-app', logGroup: appLogGroup }),
      healthCheck: {
        command: ['CMD-SHELL', 'curl -f http://localhost:8080/actuator/health || exit 1'],
        interval: cdk.Duration.seconds(30),
        timeout: cdk.Duration.seconds(10),
        retries: 3,
        startPeriod: cdk.Duration.seconds(90),
      },
    });

    const appService = new ecs.FargateService(this, 'AppService', {
      cluster,
      taskDefinition: appTask,
      desiredCount: appDesiredCount,
      cloudMapOptions: { name: 'social-app' },
    });

    // Allow app to reach data services
    props.database.connections.allowFrom(appService, ec2.Port.tcp(5432));
    props.redisSecurityGroup.addIngressRule(appService.connections.securityGroups[0], ec2.Port.tcp(6379));
    props.kafkaSecurityGroup.addIngressRule(appService.connections.securityGroups[0], ec2.Port.tcp(9092));
    props.opensearchSecurityGroup.addIngressRule(appService.connections.securityGroups[0], ec2.Port.tcp(443));

    // ── Spark Consumer (Kafka → S3 Iceberg via Glue catalog) ────

    const sparkTask = new ecs.FargateTaskDefinition(this, 'SparkConsumerTask', {
      memoryLimitMiB: 4096,
      cpu: 2048,
    });

    // Spark needs S3 + Glue access
    props.warehouseBucket.grantReadWrite(sparkTask.taskRole);
    sparkTask.taskRole.addManagedPolicy(
      iam.ManagedPolicy.fromAwsManagedPolicyName('service-role/AWSGlueServiceRole'),
    );
    (sparkTask.taskRole as iam.Role).addToPolicy(new iam.PolicyStatement({
      actions: ['glue:*'],
      resources: ['*'],
    }));

    sparkTask.addContainer('spark-consumer', {
      image: ecs.ContainerImage.fromEcrRepository(props.ecrRepos.socialApp, 'spark-consumer'),
      environment: {
        KAFKA_BOOTSTRAP_SERVERS: props.kafkaBootstrap,
        // Use Glue as Iceberg catalog instead of Hive Metastore
        ICEBERG_CATALOG_TYPE: 'glue',
        ICEBERG_WAREHOUSE: `s3://${props.warehouseBucket.bucketName}/iceberg/`,
        GLUE_DATABASE: props.glueDatabaseName,
        AWS_REGION: this.region!,
        CONFIG_DIR: '/opt/spark-apps/log-configs',
        CHECKPOINT_DIR: '/opt/spark-data/checkpoints',
      },
      logging: ecs.LogDrivers.awsLogs({ streamPrefix: 'spark-consumer', logGroup: pipelineLogGroup }),
    });

    const sparkService = new ecs.FargateService(this, 'SparkConsumerService', {
      cluster,
      taskDefinition: sparkTask,
      desiredCount: 1,
    });

    props.kafkaSecurityGroup.addIngressRule(sparkService.connections.securityGroups[0], ec2.Port.tcp(9092));

    // ── Ollama (optional — GPU instance for AI features) ────────

    const ollamaTask = new ecs.FargateTaskDefinition(this, 'OllamaTask', {
      memoryLimitMiB: 8192,
      cpu: 4096,
    });

    ollamaTask.addContainer('ollama', {
      image: ecs.ContainerImage.fromRegistry('ollama/ollama:latest'),
      portMappings: [{ containerPort: 11434 }],
      environment: {
        OLLAMA_HOST: '0.0.0.0',
      },
      logging: ecs.LogDrivers.awsLogs({ streamPrefix: 'ollama', logGroup: pipelineLogGroup }),
    });

    const ollamaService = new ecs.FargateService(this, 'OllamaService', {
      cluster,
      taskDefinition: ollamaTask,
      desiredCount: 1,
      cloudMapOptions: { name: 'ollama' },
    });

    // ── Frontend ─────────────────────────────────────────────────

    const frontendTask = new ecs.FargateTaskDefinition(this, 'FrontendTask', {
      memoryLimitMiB: 256,
      cpu: 128,
    });

    frontendTask.addContainer('frontend', {
      image: ecs.ContainerImage.fromEcrRepository(props.ecrRepos.frontend),
      portMappings: [{ containerPort: 80 }],
      logging: ecs.LogDrivers.awsLogs({ streamPrefix: 'frontend', logGroup: appLogGroup }),
    });

    const frontendService = new ecs.FargateService(this, 'FrontendService', {
      cluster,
      taskDefinition: frontendTask,
      desiredCount: frontendDesiredCount,
    });

    // ── ALB Target Groups ────────────────────────────────────────

    listener.addTargets('AppTarget', {
      port: 8080,
      targets: [appService],
      healthCheck: { path: '/actuator/health', interval: cdk.Duration.seconds(30) },
      conditions: [
        elbv2.ListenerCondition.pathPatterns(['/api/*', '/graphql', '/graphiql', '/ws/*']),
      ],
      priority: 10,
    });

    listener.addTargets('FrontendTarget', {
      port: 80,
      targets: [frontendService],
      healthCheck: { path: '/', interval: cdk.Duration.seconds(30) },
    });

    // ── CloudFront CDN ───────────────────────────────────────────

    const cdn = new cloudfront.Distribution(this, 'CDN', {
      defaultBehavior: {
        origin: new origins.LoadBalancerV2Origin(alb, { protocolPolicy: cloudfront.OriginProtocolPolicy.HTTP_ONLY }),
        viewerProtocolPolicy: cloudfront.ViewerProtocolPolicy.REDIRECT_TO_HTTPS,
        cachePolicy: cloudfront.CachePolicy.CACHING_DISABLED,
        allowedMethods: cloudfront.AllowedMethods.ALLOW_ALL,
        originRequestPolicy: cloudfront.OriginRequestPolicy.ALL_VIEWER,
      },
      additionalBehaviors: {
        // Media served directly from S3 via CloudFront (bypass ALB)
        '/api/media/*': {
          origin: new origins.S3Origin(props.uploadsBucket),
          viewerProtocolPolicy: cloudfront.ViewerProtocolPolicy.REDIRECT_TO_HTTPS,
          cachePolicy: cloudfront.CachePolicy.CACHING_OPTIMIZED,
        },
      },
    });

    // ── Outputs ──────────────────────────────────────────────────

    new cdk.CfnOutput(this, 'AlbDnsName', { value: alb.loadBalancerDnsName });
    new cdk.CfnOutput(this, 'CloudFrontUrl', { value: `https://${cdn.distributionDomainName}` });
    new cdk.CfnOutput(this, 'ClusterName', { value: cluster.clusterName });
  }
}
