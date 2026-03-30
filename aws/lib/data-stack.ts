import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as rds from 'aws-cdk-lib/aws-rds';
import * as s3 from 'aws-cdk-lib/aws-s3';
import * as secretsmanager from 'aws-cdk-lib/aws-secretsmanager';
import * as elasticache from 'aws-cdk-lib/aws-elasticache';
import * as opensearch from 'aws-cdk-lib/aws-opensearchservice';
import * as msk from 'aws-cdk-lib/aws-msk';
import * as glue from 'aws-cdk-lib/aws-glue';
import * as athena from 'aws-cdk-lib/aws-athena';
import * as iam from 'aws-cdk-lib/aws-iam';
import { Construct } from 'constructs';

interface DataStackProps extends cdk.StackProps {
  vpc: ec2.Vpc;
}

export class DataStack extends cdk.Stack {
  public readonly database: rds.DatabaseInstance;
  public readonly uploadsBucket: s3.Bucket;
  public readonly warehouseBucket: s3.Bucket;
  public readonly dbSecret: secretsmanager.ISecret;
  public readonly jwtSecret: secretsmanager.Secret;
  public readonly redisEndpoint: string;
  public readonly redisPort: string;
  public readonly opensearchEndpoint: string;
  public readonly kafkaBootstrap: string;
  public readonly kafkaSecurityGroup: ec2.SecurityGroup;
  public readonly redisSecurityGroup: ec2.SecurityGroup;
  public readonly opensearchSecurityGroup: ec2.SecurityGroup;
  public readonly glueDatabaseName: string;

  constructor(scope: Construct, id: string, props: DataStackProps) {
    super(scope, id, props);

    const instanceClass = this.node.tryGetContext('dbInstanceClass') ?? 'db.t3.medium';
    const allocatedStorage = this.node.tryGetContext('dbAllocatedStorage') ?? 20;

    // ── Secrets ────────────────────────────────────────────────

    this.dbSecret = new secretsmanager.Secret(this, 'DbSecret', {
      secretName: 'worksphere/db-credentials',
      generateSecretString: {
        secretStringTemplate: JSON.stringify({ username: 'social' }),
        generateStringKey: 'password',
        excludePunctuation: true,
        passwordLength: 32,
      },
    });

    this.jwtSecret = new secretsmanager.Secret(this, 'JwtSecret', {
      secretName: 'worksphere/jwt-secret',
      generateSecretString: {
        excludePunctuation: false,
        passwordLength: 64,
      },
    });

    // ── RDS PostgreSQL ─────────────────────────────────────────

    const dbSecurityGroup = new ec2.SecurityGroup(this, 'DbSg', {
      vpc: props.vpc,
      description: 'WorkSphere RDS security group',
    });
    dbSecurityGroup.addIngressRule(
      ec2.Peer.ipv4(props.vpc.vpcCidrBlock),
      ec2.Port.tcp(5432),
      'Allow PostgreSQL from VPC',
    );

    this.database = new rds.DatabaseInstance(this, 'Database', {
      engine: rds.DatabaseInstanceEngine.postgres({
        version: rds.PostgresEngineVersion.VER_16_2,
      }),
      instanceType: new ec2.InstanceType(instanceClass),
      vpc: props.vpc,
      vpcSubnets: { subnetType: ec2.SubnetType.PRIVATE_ISOLATED },
      securityGroups: [dbSecurityGroup],
      credentials: rds.Credentials.fromSecret(this.dbSecret),
      databaseName: 'social_enterprise',
      allocatedStorage,
      maxAllocatedStorage: 100,
      deletionProtection: true,
      removalPolicy: cdk.RemovalPolicy.RETAIN,
      backupRetention: cdk.Duration.days(7),
      multiAz: false,
    });

    // ── S3 Buckets ─────────────────────────────────────────────

    // Media uploads (replaces MinIO media bucket)
    this.uploadsBucket = new s3.Bucket(this, 'UploadsBucket', {
      bucketName: `worksphere-media-${this.account}`,
      removalPolicy: cdk.RemovalPolicy.RETAIN,
      blockPublicAccess: s3.BlockPublicAccess.BLOCK_ALL,
      encryption: s3.BucketEncryption.S3_MANAGED,
      versioned: false,
      cors: [
        {
          allowedMethods: [s3.HttpMethods.GET, s3.HttpMethods.PUT],
          allowedOrigins: ['*'],
          allowedHeaders: ['*'],
          maxAge: 3600,
        },
      ],
    });

    // Data warehouse (replaces MinIO warehouse bucket — Iceberg tables)
    this.warehouseBucket = new s3.Bucket(this, 'WarehouseBucket', {
      bucketName: `worksphere-warehouse-${this.account}`,
      removalPolicy: cdk.RemovalPolicy.RETAIN,
      blockPublicAccess: s3.BlockPublicAccess.BLOCK_ALL,
      encryption: s3.BucketEncryption.S3_MANAGED,
      versioned: false,
      lifecycleRules: [
        {
          id: 'archive-old-data',
          transitions: [
            { storageClass: s3.StorageClass.INFREQUENT_ACCESS, transitionAfter: cdk.Duration.days(90) },
            { storageClass: s3.StorageClass.GLACIER, transitionAfter: cdk.Duration.days(365) },
          ],
        },
      ],
    });

    // ── ElastiCache Redis ──────────────────────────────────────

    this.redisSecurityGroup = new ec2.SecurityGroup(this, 'RedisSg', {
      vpc: props.vpc,
      description: 'WorkSphere Redis security group',
    });
    this.redisSecurityGroup.addIngressRule(
      ec2.Peer.ipv4(props.vpc.vpcCidrBlock),
      ec2.Port.tcp(6379),
      'Allow Redis from VPC',
    );

    const redisSubnetGroup = new elasticache.CfnSubnetGroup(this, 'RedisSubnetGroup', {
      description: 'WorkSphere Redis subnet group',
      subnetIds: props.vpc.selectSubnets({ subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS }).subnetIds,
    });

    const redisCluster = new elasticache.CfnCacheCluster(this, 'Redis', {
      cacheNodeType: 'cache.t3.micro',
      engine: 'redis',
      numCacheNodes: 1,
      clusterName: 'worksphere-redis',
      vpcSecurityGroupIds: [this.redisSecurityGroup.securityGroupId],
      cacheSubnetGroupName: redisSubnetGroup.ref,
      engineVersion: '7.1',
    });

    this.redisEndpoint = redisCluster.attrRedisEndpointAddress;
    this.redisPort = redisCluster.attrRedisEndpointPort;

    // ── Amazon OpenSearch Service ──────────────────────────────

    this.opensearchSecurityGroup = new ec2.SecurityGroup(this, 'OpenSearchSg', {
      vpc: props.vpc,
      description: 'WorkSphere OpenSearch security group',
    });
    this.opensearchSecurityGroup.addIngressRule(
      ec2.Peer.ipv4(props.vpc.vpcCidrBlock),
      ec2.Port.tcp(443),
      'Allow HTTPS from VPC',
    );

    const osDomain = new opensearch.Domain(this, 'OpenSearch', {
      domainName: 'worksphere',
      version: opensearch.EngineVersion.OPENSEARCH_2_11,
      vpc: props.vpc,
      vpcSubnets: [{ subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS, onePerAz: true }],
      securityGroups: [this.opensearchSecurityGroup],
      capacity: {
        dataNodeInstanceType: 't3.small.search',
        dataNodes: 1,
      },
      ebs: {
        volumeSize: 20,
        volumeType: ec2.EbsDeviceVolumeType.GP3,
      },
      nodeToNodeEncryption: true,
      encryptionAtRest: { enabled: true },
      enforceHttps: true,
      removalPolicy: cdk.RemovalPolicy.DESTROY,
    });

    this.opensearchEndpoint = osDomain.domainEndpoint;

    // ── Amazon MSK (Managed Kafka) ─────────────────────────────

    this.kafkaSecurityGroup = new ec2.SecurityGroup(this, 'KafkaSg', {
      vpc: props.vpc,
      description: 'WorkSphere MSK security group',
    });
    this.kafkaSecurityGroup.addIngressRule(
      ec2.Peer.ipv4(props.vpc.vpcCidrBlock),
      ec2.Port.tcp(9092),
      'Allow Kafka plaintext from VPC',
    );
    this.kafkaSecurityGroup.addIngressRule(
      ec2.Peer.ipv4(props.vpc.vpcCidrBlock),
      ec2.Port.tcp(9094),
      'Allow Kafka TLS from VPC',
    );

    const mskCluster = new msk.CfnCluster(this, 'Kafka', {
      clusterName: 'worksphere-kafka',
      kafkaVersion: '3.6.0',
      numberOfBrokerNodes: 2,
      brokerNodeGroupInfo: {
        instanceType: 'kafka.t3.small',
        clientSubnets: props.vpc.selectSubnets({ subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS }).subnetIds,
        securityGroups: [this.kafkaSecurityGroup.securityGroupId],
        storageInfo: {
          ebsStorageInfo: { volumeSize: 50 },
        },
      },
      encryptionInfo: {
        encryptionInTransit: { clientBroker: 'TLS_PLAINTEXT' },
      },
    });

    this.kafkaBootstrap = cdk.Fn.join(',', [
      // MSK bootstrap brokers are resolved at deploy time via custom resource or manual config
      // For now, output the cluster ARN — bootstrap string is available post-deploy
      `b-1.worksphere-kafka.${this.region}.amazonaws.com:9092`,
      `b-2.worksphere-kafka.${this.region}.amazonaws.com:9092`,
    ]);

    // ── AWS Glue (Iceberg Catalog — replaces Hive Metastore) ──

    this.glueDatabaseName = 'worksphere';

    new glue.CfnDatabase(this, 'GlueDatabase', {
      catalogId: this.account,
      databaseInput: {
        name: this.glueDatabaseName,
        description: 'WorkSphere analytics data warehouse (Iceberg tables)',
        locationUri: `s3://${this.warehouseBucket.bucketName}/iceberg/`,
      },
    });

    // Glue crawler role for Iceberg table discovery
    const glueCrawlerRole = new iam.Role(this, 'GlueCrawlerRole', {
      assumedBy: new iam.ServicePrincipal('glue.amazonaws.com'),
      managedPolicies: [
        iam.ManagedPolicy.fromAwsManagedPolicyName('service-role/AWSGlueServiceRole'),
      ],
    });
    this.warehouseBucket.grantRead(glueCrawlerRole);

    // ── Athena (replaces Trino — SQL over Iceberg on S3) ──────

    const athenaResultsBucket = new s3.Bucket(this, 'AthenaResultsBucket', {
      bucketName: `worksphere-athena-results-${this.account}`,
      removalPolicy: cdk.RemovalPolicy.DESTROY,
      autoDeleteObjects: true,
      lifecycleRules: [
        { expiration: cdk.Duration.days(7) },
      ],
    });

    new athena.CfnWorkGroup(this, 'AthenaWorkGroup', {
      name: 'worksphere',
      description: 'WorkSphere analytics queries',
      workGroupConfiguration: {
        resultConfiguration: {
          outputLocation: `s3://${athenaResultsBucket.bucketName}/results/`,
        },
        engineVersion: { selectedEngineVersion: 'Athena engine version 3' },
        bytesScannedCutoffPerQuery: 10_737_418_240, // 10 GB
      },
    });

    // ── Outputs ────────────────────────────────────────────────

    new cdk.CfnOutput(this, 'DatabaseEndpoint', {
      value: this.database.dbInstanceEndpointAddress,
    });
    new cdk.CfnOutput(this, 'MediaBucketName', {
      value: this.uploadsBucket.bucketName,
    });
    new cdk.CfnOutput(this, 'WarehouseBucketName', {
      value: this.warehouseBucket.bucketName,
    });
    new cdk.CfnOutput(this, 'RedisEndpoint', {
      value: this.redisEndpoint,
    });
    new cdk.CfnOutput(this, 'OpenSearchEndpoint', {
      value: this.opensearchEndpoint,
    });
    new cdk.CfnOutput(this, 'KafkaClusterArn', {
      value: mskCluster.ref,
    });
    new cdk.CfnOutput(this, 'GlueDatabase', {
      value: this.glueDatabaseName,
    });
    new cdk.CfnOutput(this, 'AthenaBucketName', {
      value: athenaResultsBucket.bucketName,
    });
  }
}
