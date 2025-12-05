# Terraform ECS Fargate Deployment

This directory contains Terraform configuration to deploy the Spring Boot application on AWS ECS Fargate with 3 instances.

## Architecture

The infrastructure includes:

- **VPC**: Custom VPC with public and private subnets across 2 availability zones
- **Application Load Balancer**: Public-facing ALB in public subnets
- **ECS Fargate**: 3 tasks running in private subnets
- **ECR**: Container registry for Docker images
- **CloudWatch**: Centralized logging for ECS tasks
- **IAM Roles**: Task execution and task roles with necessary permissions
- **Security Groups**: Restricted network access (ALB → ECS tasks only)

## Prerequisites

1. **AWS CLI** configured with appropriate credentials
2. **Terraform** >= 1.0
3. **Docker** (for building images with buildpacks)
4. **Maven** (Maven Wrapper included in project)

## Quick Start

### 1. Build and Push Docker Image

First, deploy the Terraform infrastructure to create the ECR repository, then build and push your image:

```bash
# Initialize Terraform
cd terraform
terraform init

# Deploy infrastructure (this creates ECR repository)
terraform apply

# Get ECR repository URL from output
ECR_REPO_URL=$(terraform output -raw ecr_repository_url)
AWS_REGION=$(terraform output -raw aws_region || echo "us-east-1")

# Build and push image using buildpacks
cd ..
chmod +x scripts/build-and-push.sh
./scripts/build-and-push.sh ${AWS_REGION} spring-playground-dev
```

### 2. Deploy Infrastructure

```bash
cd terraform

# Initialize Terraform
terraform init

# Review the plan
terraform plan

# Apply configuration
terraform apply

# Get outputs
terraform output
```

### 3. Access Application

After deployment, get the ALB URL:

```bash
terraform output alb_url
```

Access your application at `http://<alb-dns-name>`

Health check: `http://<alb-dns-name>/actuator/health`

## Configuration

### Variables

Create a `terraform.tfvars` file (copy from `terraform.tfvars.example`):

```hcl
aws_region = "us-east-1"
project_name = "spring-playground"
environment = "dev"
desired_count = 3
cpu = 512
memory = 1024
```

### Key Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `aws_region` | AWS region | `us-east-1` |
| `desired_count` | Number of ECS tasks | `3` |
| `cpu` | Fargate CPU units | `512` (0.5 vCPU) |
| `memory` | Fargate memory (MB) | `1024` (1 GB) |
| `container_port` | Application port | `8080` |
| `health_check_path` | Health check endpoint | `/actuator/health` |
| `enable_auto_scaling` | Enable auto-scaling | `false` |

### Fargate CPU/Memory Combinations

Valid combinations:
- CPU 256 (.25 vCPU): Memory 512, 1024, 2048
- CPU 512 (.5 vCPU): Memory 1024-4096 (1 GB increments)
- CPU 1024 (1 vCPU): Memory 2048-8192 (1 GB increments)
- CPU 2048 (2 vCPU): Memory 4096-16384 (1 GB increments)
- CPU 4096 (4 vCPU): Memory 8192-30720 (1 GB increments)

## Building Images with Cloud Native Buildpacks

This project uses Spring Boot's built-in Cloud Native Buildpacks support instead of traditional Dockerfile.

### Benefits

- ✅ No Dockerfile maintenance required
- ✅ Optimized layer caching
- ✅ Best practices built-in
- ✅ Automatic security updates
- ✅ Smaller image sizes

### Build Locally

```bash
# Build image locally
./mvnw spring-boot:build-image -Dspring-boot.build-image.imageName=spring-playground:latest

# Run locally
docker run -p 8080:8080 -e SPRING_PROFILES_ACTIVE=ecs spring-playground:latest
```

### Build and Push to ECR

Use the provided script:

```bash
./scripts/build-and-push.sh [aws-region] [ecr-repository-name]

# Example
./scripts/build-and-push.sh us-east-1 spring-playground-dev
```

The script:
1. Authenticates Docker to ECR
2. Builds image using Cloud Native Buildpacks
3. Tags with `latest` and git commit hash
4. Pushes both tags to ECR

## Deployment Workflow

### Initial Deployment

```bash
# 1. Deploy infrastructure
cd terraform
terraform init
terraform apply

# 2. Build and push image
cd ..
./scripts/build-and-push.sh

# 3. Access application
ALB_URL=$(cd terraform && terraform output -raw alb_url)
curl ${ALB_URL}/actuator/health
```

### Update Application

```bash
# 1. Build and push new image
./scripts/build-and-push.sh

# 2. Force new deployment
aws ecs update-service \
  --cluster spring-playground-dev-cluster \
  --service spring-playground-dev-service \
  --force-new-deployment \
  --region us-east-1

# 3. Monitor deployment
aws ecs describe-services \
  --cluster spring-playground-dev-cluster \
  --services spring-playground-dev-service \
  --region us-east-1
```

## Monitoring

### CloudWatch Logs

View ECS task logs:

```bash
aws logs tail /ecs/spring-playground-dev --follow --region us-east-1
```

### ECS Service Status

```bash
aws ecs describe-services \
  --cluster spring-playground-dev-cluster \
  --services spring-playground-dev-service \
  --region us-east-1
```

### List Running Tasks

```bash
aws ecs list-tasks \
  --cluster spring-playground-dev-cluster \
  --service-name spring-playground-dev-service \
  --region us-east-1
```

## Auto Scaling (Optional)

Enable auto-scaling by setting `enable_auto_scaling = true`:

```hcl
enable_auto_scaling = true
min_capacity = 2
max_capacity = 6
```

This configures:
- CPU-based scaling (target: 70%)
- Memory-based scaling (target: 80%)
- Scale out cooldown: 60 seconds
- Scale in cooldown: 300 seconds

## Cost Optimization

### Development

```hcl
desired_count = 1
cpu = 256
memory = 512
enable_auto_scaling = false
```

### Production

```hcl
desired_count = 3
cpu = 1024
memory = 2048
enable_auto_scaling = true
max_capacity = 10
```

## Cleanup

Destroy all resources:

```bash
cd terraform
terraform destroy
```

**Note**: ECR images must be deleted manually before destroying:

```bash
aws ecr batch-delete-image \
  --repository-name spring-playground-dev \
  --image-ids imageTag=latest \
  --region us-east-1
```

## Troubleshooting

### Tasks Not Starting

Check ECS service events:

```bash
aws ecs describe-services \
  --cluster spring-playground-dev-cluster \
  --services spring-playground-dev-service \
  --region us-east-1 \
  --query 'services[0].events[0:5]'
```

### Health Check Failures

Verify health check endpoint:

```bash
# Get task private IP
TASK_ARN=$(aws ecs list-tasks --cluster spring-playground-dev-cluster --service-name spring-playground-dev-service --region us-east-1 --query 'taskArns[0]' --output text)

# Check task details
aws ecs describe-tasks --cluster spring-playground-dev-cluster --tasks $TASK_ARN --region us-east-1
```

### Container Logs

View specific task logs:

```bash
aws logs get-log-events \
  --log-group-name /ecs/spring-playground-dev \
  --log-stream-name ecs/spring-playground-container/<task-id> \
  --region us-east-1
```

### Image Pull Errors

Verify ECR authentication and image exists:

```bash
aws ecr describe-images \
  --repository-name spring-playground-dev \
  --region us-east-1
```

## Security Considerations

1. **Private Subnets**: ECS tasks run in private subnets with no direct internet access
2. **Security Groups**: Restrictive rules (ALB can only access ECS tasks on port 8080)
3. **IAM Roles**: Least privilege permissions
4. **ECR Scanning**: Image scanning enabled on push
5. **Encryption**: ECR repositories use AES256 encryption

## Network Architecture

```
Internet
   |
   v
Internet Gateway
   |
   v
Public Subnets (2 AZs)
   |
   +-- Application Load Balancer
   |
   v
Private Subnets (2 AZs)
   |
   +-- ECS Fargate Tasks (3 instances)
   |
   v
NAT Gateway (for outbound traffic)
```

## File Structure

```
terraform/
├── main.tf              # Provider and backend configuration
├── variables.tf         # Input variables
├── outputs.tf           # Output values
├── vpc.tf              # VPC, subnets, routing
├── security_groups.tf  # Security groups
├── alb.tf              # Application Load Balancer
├── iam.tf              # IAM roles and policies
├── ecr.tf              # ECR repository
├── ecs.tf              # ECS cluster, task definition, service
└── terraform.tfvars.example  # Example variables
```

## Additional Resources

- [AWS ECS Fargate Documentation](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/AWS_Fargate.html)
- [Terraform AWS Provider](https://registry.terraform.io/providers/hashicorp/aws/latest/docs)
- [Spring Boot Buildpacks](https://spring.io/guides/gs/spring-boot-docker/)
- [Cloud Native Buildpacks](https://buildpacks.io/)
