# AWS ECS Deployment - Quick Start Guide

Hướng dẫn nhanh để deploy Spring Boot application lên AWS ECS Fargate sử dụng Makefile commands.

## 📋 Prerequisites

1. **AWS CLI** configured với credentials
2. **Docker** running
3. **Terraform** >= 1.0
4. **Maven** (hoặc dùng `./mvnw`)

## 🚀 Full Deployment Workflow

### Bước 1: Deploy Infrastructure với Terraform

```bash
# Initialize Terraform
make terraform-init

# Review infrastructure plan
make terraform-plan

# Deploy infrastructure (VPC, ALB, ECS, ECR)
make terraform-apply
```

Terraform sẽ tạo:
- VPC với public/private subnets
- Application Load Balancer
- ECS Fargate cluster
- ECR repository
- IAM roles
- Security groups
- CloudWatch logs

### Bước 2: Build và Push Image

```bash
# Full build and push (sử dụng Cloud Native Buildpacks)
make ecr-build-push
```

Hoặc chia nhỏ từng bước:

```bash
# Chỉ build image
make ecr-build

# Chỉ push image
make ecr-push
```

**Image sẽ được tag:**
- `latest` - tag mới nhất
- `<git-commit-hash>` - tag theo commit

### Bước 3: Deploy lên ECS

```bash
# Force new deployment to ECS
make ecr-update
```

Hoặc full deployment (build + push + update):

```bash
make ecr-deploy
```

## 🔄 Update Application

Khi có code mới, chỉ cần:

```bash
# Build, push, và deploy trong 1 lệnh
make ecr-deploy
```

## 📊 Monitoring & Management

### Check Status

```bash
# Xem deployment info
make info

# Check ECS service status
make ecs-status

# List running tasks
make ecs-tasks

# Xem ALB URL
make alb-url

# Test health check
make health-check
```

### View Logs

```bash
# Tail CloudWatch logs (real-time)
make ecr-logs
```

### Scale Service

```bash
# Scale to 5 instances
make ecs-scale COUNT=5

# Scale to 1 instance (save cost)
make ecs-scale COUNT=1

# Back to 3 instances (default)
make ecs-scale COUNT=3
```

### Shell Access

```bash
# Get shell access to running container
make ecs-shell
```

## 📦 ECR Management

### List Images

```bash
# Xem tất cả images trong ECR
make ecr-images
```

### Create ECR Repository (if needed)

```bash
make ecr-create
```

## 🎯 Common Use Cases

### Case 1: First Time Deployment

```bash
# 1. Deploy infrastructure
make terraform-init
make terraform-apply

# 2. Build and deploy application
make ecr-deploy

# 3. Check deployment
make info
make health-check
```

### Case 2: Code Update

```bash
# Build, push, deploy
make ecr-deploy

# Monitor logs
make ecr-logs
```

### Case 3: Quick Build Test

```bash
# Build image locally (không push ECR)
make build-image

# Hoặc build cho ECR (không push)
make ecr-build
```

### Case 4: Infrastructure Update

```bash
# Update Terraform configuration
cd terraform
# Edit *.tf files

# Apply changes
make terraform-plan
make terraform-apply
```

### Case 5: Rollback

```bash
# List available images
make ecr-images

# Update task definition với image cũ hơn
# Sau đó force deployment
make ecr-update
```

## 🛠️ Configuration

### Environment Variables

```bash
# AWS Configuration
export AWS_REGION=us-east-1
export ENVIRONMENT=dev

# Custom ECR repo name
export ECR_REPO_NAME=my-custom-repo

# Custom image tag
export IMAGE_TAG=v1.0.0

# Run deployment
make ecr-deploy
```

### Default Values

| Variable | Default | Description |
|----------|---------|-------------|
| `AWS_REGION` | `us-east-1` | AWS region |
| `ENVIRONMENT` | `dev` | Environment name |
| `PROJECT_NAME` | `playground` | Project name |
| `ECR_REPO_NAME` | `playground-dev` | ECR repository |
| `IMAGE_TAG` | `latest` | Docker image tag |
| `ECS_CLUSTER` | `playground-dev-cluster` | ECS cluster name |
| `ECS_SERVICE` | `playground-dev-service` | ECS service name |

## 🔧 Terraform Variables

Customize trong `terraform/terraform.tfvars`:

```hcl
# AWS Region
aws_region = "us-east-1"

# ECS Configuration
desired_count = 3
cpu           = 512    # 0.5 vCPU
memory        = 1024   # 1 GB

# Auto-scaling (optional)
enable_auto_scaling = true
min_capacity        = 2
max_capacity        = 6

# Networking
vpc_cidr           = "10.0.0.0/16"
availability_zones = ["us-east-1a", "us-east-1b"]
```

## 🌐 Access Application

### Get URLs

```bash
# Get ALB URL
ALB_URL=$(make alb-url)
echo $ALB_URL

# Test application
curl $ALB_URL/actuator/health

# View in browser
open $ALB_URL/actuator/health
```

### Endpoints

- **Health Check**: `http://<alb-url>/actuator/health`
- **Metrics**: `http://<alb-url>/actuator/prometheus`
- **Info**: `http://<alb-url>/actuator/info`

## 🧹 Cleanup

### Destroy Everything

```bash
# Delete all infrastructure
make terraform-destroy
```

**Note**: Phải xóa ECR images trước khi destroy:

```bash
# List images
make ecr-images

# Xóa repository qua AWS Console hoặc CLI
aws ecr delete-repository \
  --repository-name playground-dev \
  --force \
  --region us-east-1

# Then destroy infrastructure
make terraform-destroy
```

## 💡 Tips & Tricks

### 1. View All Available Commands

```bash
make help
```

### 2. Check AWS Credentials

```bash
make check-aws
```

### 3. Build Performance

First build sẽ mất 5-10 phút (download buildpacks, dependencies).
Subsequent builds sẽ nhanh hơn nhờ layer caching.

### 4. Cost Optimization

**Development:**
```bash
# Reduce to 1 instance
make ecs-scale COUNT=1

# Use smaller CPU/Memory
# Edit terraform/terraform.tfvars:
# cpu = 256
# memory = 512
```

**Production:**
```bash
# Enable auto-scaling
# Edit terraform/terraform.tfvars:
# enable_auto_scaling = true
# min_capacity = 2
# max_capacity = 10
```

### 5. Debugging

```bash
# Check service events
make ecs-status

# Check task details
make ecs-tasks

# View logs
make ecr-logs

# Shell access
make ecs-shell
```

### 6. Multi-Region Deployment

```bash
# Deploy to different region
AWS_REGION=ap-southeast-1 make terraform-apply
AWS_REGION=ap-southeast-1 make ecr-deploy
```

## 📚 Makefile Commands Reference

### ECR/ECS Deployment

| Command | Description |
|---------|-------------|
| `make ecr-login` | Authenticate Docker to ECR |
| `make ecr-create` | Create ECR repository |
| `make ecr-build` | Build image with buildpacks |
| `make ecr-push` | Push image to ECR |
| `make ecr-build-push` | Build and push |
| `make ecr-deploy` | Full deployment |
| `make ecr-update` | Force new ECS deployment |
| `make ecr-images` | List ECR images |

### ECS Management

| Command | Description |
|---------|-------------|
| `make ecs-status` | Check service status |
| `make ecs-tasks` | List running tasks |
| `make ecs-scale COUNT=X` | Scale service |
| `make ecs-shell` | Shell access to container |
| `make ecr-logs` | Tail CloudWatch logs |

### Terraform

| Command | Description |
|---------|-------------|
| `make terraform-init` | Initialize Terraform |
| `make terraform-plan` | Preview changes |
| `make terraform-apply` | Apply infrastructure |
| `make terraform-output` | Show outputs |
| `make terraform-destroy` | Destroy infrastructure |

### Utilities

| Command | Description |
|---------|-------------|
| `make info` | Show deployment info |
| `make alb-url` | Get ALB URL |
| `make health-check` | Test health endpoint |

### Local Development

| Command | Description |
|---------|-------------|
| `make install` | Build project |
| `make run` | Run locally |
| `make run-ecs` | Run with ECS profile |
| `make build-image` | Build local image |

## 🔐 Security Best Practices

1. **Never commit AWS credentials**
2. **Use IAM roles** for ECS tasks
3. **Enable ECR image scanning** (already configured)
4. **Use private subnets** for ECS tasks (already configured)
5. **Restrict security groups** (already configured)
6. **Enable CloudWatch logs** (already configured)

## 📞 Troubleshooting

### Problem: Build fails with "Docker not running"

**Solution:**
```bash
# Start Docker
open -a Docker  # macOS
sudo systemctl start docker  # Linux
```

### Problem: "AWS credentials not configured"

**Solution:**
```bash
# Configure AWS CLI
aws configure

# Or use environment variables
export AWS_ACCESS_KEY_ID=xxx
export AWS_SECRET_ACCESS_KEY=xxx
export AWS_REGION=us-east-1
```

### Problem: ECS tasks failing to start

**Solution:**
```bash
# Check events
make ecs-status

# Check logs
make ecr-logs

# Verify image exists
make ecr-images

# Check task definition
aws ecs describe-task-definition \
  --task-definition playground-dev
```

### Problem: Health check failing

**Solution:**
```bash
# Check if app is listening on correct port
make ecs-shell
# Inside container:
wget -O- localhost:8080/actuator/health

# Check security groups
# Verify ALB can reach ECS tasks on port 8080
```

## 🎓 Learning Resources

- [Terraform AWS Provider](https://registry.terraform.io/providers/hashicorp/aws/latest/docs)
- [AWS ECS Documentation](https://docs.aws.amazon.com/ecs/)
- [Cloud Native Buildpacks](https://buildpacks.io/)
- [Spring Boot Docker](https://spring.io/guides/topicals/spring-boot-docker/)

---

**Happy Deploying! 🚀**
