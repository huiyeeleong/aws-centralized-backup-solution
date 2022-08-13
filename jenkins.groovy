#!/usr/bin/env groovy

pipeline {

    agent { label 'ecs' }

    parameters {
        string(name: 'account', description: 'Deploy into AWS Account', defaultValue: 'billing')
        string(name: 'ProjectName', description: 'Provide the unique stack name. MANDATORY field.', defaultValue: 'thryv-au-backup')
       
       
    }

    environment {
        AWS_CREDENTIALS = "sensis-${params.account}"
        STACK_NAME = "${params.ProjectName}"
        TAG_KEY = "project"
        TAG_VALUE = "aws-backup"
        S3 = "aws-centralized-backup"
    }

    stages {

        stage('initialise-aws-backup-project') {
            steps {
                script {
                    currentBuild.displayName = "#${env.BUILD_NUMBER} - ${params.action}"
                    currentBuild.description = "Build #${env.BUILD_NUMBER} - Deploying to ${params.account} - ProjectName ${params.ProjectName} "
                    sh 'printenv'
                }
            }
        }

        stage('create-cross-account-backup-role'){
             steps {
                 withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', credentialsId: env.AWS_CREDENTIALS]]) {
                    sh '''
                          if /usr/local/bin/aws cloudformation list-stack-instances --stack-set-name $STACK_NAME-cross-account-role-stackset; then
                          /usr/local/bin/aws cloudformation update-stack-set --stack-set-name $STACK_NAME-cross-account-role-stackset --template-body file://CloudFormation/aws-backup-member-account-iam-role.yaml --region ap-southeast-2 --permission-model SERVICE_MANAGED --auto-deployment Enabled=true,RetainStacksOnAccountRemoval=true --capabilities CAPABILITY_NAMED_IAM
                          else
                          /usr/local/bin/aws cloudformation create-stack-set --stack-set-name $STACK_NAME-cross-account-role-stackset --template-body file://CloudFormation/aws-backup-member-account-iam-role.yaml --region ap-southeast-2   --permission-model SERVICE_MANAGED   --auto-deployment Enabled=true,RetainStacksOnAccountRemoval=true --capabilities CAPABILITY_NAMED_IAM
                          /usr/local/bin/aws cloudformation create-stack-instances --stack-set-name $STACK_NAME-cross-account-role-stackset --deployment-targets OrganizationalUnitIds='["ou-vu7c-j3mq43jt", "ou-vu7c-04r9xjpe", "ou-vu7c-cvh11cjm", "ou-vu7c-ikloaj1z", "ou-vu7c-nfz6snwu", "ou-vu7c-kssdxqjd", "ou-vu7c-osqg9whm", "ou-vu7c-ml5eq8gw", "ou-vu7c-hugo3gqt"]' --regions '["ap-southeast-2"]' 
                          fi
                    '''
                  }
             }
        }
        

        stage('create-member-backup-resource'){
             steps {
                 withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', credentialsId: env.AWS_CREDENTIALS]]) {
                    sh(returnStdout: true, script: '''
                          #!/bin/bash
                          if /usr/local/bin/aws cloudformation list-stack-instances --stack-set-name $STACK_NAME-automated-vault-stackset; then
                          /usr/local/bin/aws cloudformation update-stack-set --stack-set-name $STACK_NAME-automated-vault-stackset \
                          --template-body file://CloudFormation/aws-backup-vault-member-account.yaml \
                          --region ap-southeast-2 \
                          --permission-model SERVICE_MANAGED \
                          --auto-deployment Enabled=true,RetainStacksOnAccountRemoval=true \
                          --deployment-targets OrganizationalUnitIds='["ou-vu7c-j3mq43jt", "ou-vu7c-04r9xjpe", "ou-vu7c-cvh11cjm", "ou-vu7c-ikloaj1z", "ou-vu7c-nfz6snwu", "ou-vu7c-kssdxqjd", "ou-vu7c-osqg9whm", "ou-vu7c-ml5eq8gw", "ou-vu7c-hugo3gqt"]' \
                          --regions '["ap-southeast-2", "us-east-1"]' \
                          --capabilities CAPABILITY_NAMED_IAM

                          else

                          /usr/local/bin/aws cloudformation create-stack-set --stack-set-name $STACK_NAME-automated-vault-stackset \
                          --template-body file://CloudFormation/aws-backup-vault-member-account.yaml \
                          --region ap-southeast-2 \
                          --permission-model SERVICE_MANAGED \
                          --auto-deployment Enabled=true,RetainStacksOnAccountRemoval=true \
                          --capabilities CAPABILITY_NAMED_IAM \

                          /usr/local/bin/aws cloudformation create-stack-instances --stack-set-name $STACK_NAME-automated-vault-stackset \
                          --deployment-targets OrganizationalUnitIds='["ou-vu7c-j3mq43jt", "ou-vu7c-04r9xjpe", "ou-vu7c-cvh11cjm", "ou-vu7c-ikloaj1z", "ou-vu7c-nfz6snwu", "ou-vu7c-kssdxqjd", "ou-vu7c-osqg9whm", "ou-vu7c-ml5eq8gw", "ou-vu7c-hugo3gqt"]' \
                          --regions '["ap-southeast-2", "us-east-1"]' 
                          fi
                    ''')
                  }
             }
        }

        stage('create-aws-backup-org-policy'){
             steps {
                 withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', credentialsId: env.AWS_CREDENTIALS]]) {
                    sh '/usr/local/bin/sam deploy \
                    --no-fail-on-empty-changeset \
                    --stack-name centralized-$STACK_NAME-policy \
                    --template-file CloudFormation/aws-backup-org-policy.yaml \
                    --parameter-overrides pTagKey=$TAG_KEY pTagValue=$TAG_VALUE S3Bucket=$S3 \
                    --region ap-southeast-2 \
                    --capabilities CAPABILITY_NAMED_IAM'
                  }
             }
        }

    }
}

