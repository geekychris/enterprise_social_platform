#!/usr/bin/env npx ts-node
import 'source-map-support/register';
import * as cdk from 'aws-cdk-lib';
import { VpcStack } from './vpc-stack';
import { DataStack } from './data-stack';
import { EcrStack } from './ecr-stack';
import { EcsStack } from './ecs-stack';

const app = new cdk.App();

const env = {
  account: process.env.CDK_DEFAULT_ACCOUNT,
  region: process.env.CDK_DEFAULT_REGION ?? 'us-west-2',
};

const vpc = new VpcStack(app, 'WorkSphereVpc', { env });
const data = new DataStack(app, 'WorkSphereData', { env, vpc: vpc.vpc });
const ecr = new EcrStack(app, 'WorkSphereEcr', { env });
new EcsStack(app, 'WorkSphereEcs', {
  env,
  vpc: vpc.vpc,
  database: data.database,
  uploadsBucket: data.uploadsBucket,
  dbSecret: data.dbSecret,
  jwtSecret: data.jwtSecret,
  ecrRepos: ecr.repos,
});
