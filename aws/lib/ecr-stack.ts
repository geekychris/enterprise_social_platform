import * as cdk from 'aws-cdk-lib';
import * as ecr from 'aws-cdk-lib/aws-ecr';
import { Construct } from 'constructs';

export interface EcrRepos {
  socialApp: ecr.Repository;
  frontend: ecr.Repository;
  aoeeServer: ecr.Repository;
  aoeeProxy: ecr.Repository;
}

export class EcrStack extends cdk.Stack {
  public readonly repos: EcrRepos;

  constructor(scope: Construct, id: string, props?: cdk.StackProps) {
    super(scope, id, props);

    const createRepo = (name: string) =>
      new ecr.Repository(this, name, {
        repositoryName: `worksphere/${name}`,
        removalPolicy: cdk.RemovalPolicy.RETAIN,
        lifecycleRules: [{ maxImageCount: 10 }],
      });

    this.repos = {
      socialApp: createRepo('social-app'),
      frontend: createRepo('frontend'),
      aoeeServer: createRepo('aoee-server'),
      aoeeProxy: createRepo('aoee-proxy'),
    };
  }
}
