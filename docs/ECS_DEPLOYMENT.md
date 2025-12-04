# AWS ECS Deployment Guide

This guide explains how to deploy the Spring Playground application on AWS ECS (Elastic Container Service) with Hazelcast clustering.

## Prerequisites

1. **AWS Account** with appropriate permissions
2. **ECS Cluster** created
3. **RDS PostgreSQL** instance (or use Amazon Aurora)
4. **ECR Repository** for Docker images
5. **IAM Role** for ECS Task with required permissions

## IAM Permissions Required

The ECS Task Role needs the following permissions for Hazelcast discovery:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "ec2:DescribeInstances",
        "ec2:DescribeNetworkInterfaces",
        "ec2:DescribeTags",
        "ecs:ListTasks",
        "ecs:DescribeTasks",
        "ecs:DescribeContainerInstances"
      ],
      "Resource": "*"
    }
  ]
}
```

## Environment Variables

Configure these environment variables in your ECS Task Definition:

### Required Variables

```bash
# Spring Profile
SPRING_PROFILES_ACTIVE=ecs

# Database Configuration
SPRING_DATASOURCE_URL=jdbc:postgresql://your-rds-endpoint:5432/playground
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=your-secure-password

# Cache Configuration
SPRING_CACHE_TYPE=hazelcast

# AWS Configuration
AWS_REGION=us-east-1
```

### Optional Hazelcast Variables

```bash
# Hazelcast Cluster Configuration
HAZELCAST_TAG_KEY=hazelcast-cluster
HAZELCAST_TAG_VALUE=spring-playground

# Security Group for member filtering (optional)
HAZELCAST_SECURITY_GROUP=sg-xxxxxxxxx
```

## Task Definition Configuration

### Port Mappings

Ensure both ports are exposed in your task definition:

```json
{
  "portMappings": [
    {
      "containerPort": 8080,
      "hostPort": 8080,
      "protocol": "tcp"
    },
    {
      "containerPort": 5701,
      "hostPort": 5701,
      "protocol": "tcp",
      "name": "hazelcast"
    }
  ]
}
```

### Health Check

```json
{
  "healthCheck": {
    "command": [
      "CMD-SHELL",
      "curl -f http://localhost:8080/actuator/health || exit 1"
    ],
    "interval": 30,
    "timeout": 5,
    "retries": 3,
    "startPeriod": 60
  }
}
```

### Resource Requirements

Recommended resource allocation:

```json
{
  "cpu": "512",
  "memory": "1024",
  "requiresCompatibilities": ["FARGATE"]
}
```

## Security Group Configuration

Your ECS tasks need a security group that allows:

1. **Inbound Rules:**
   - Port 8080 (HTTP) - from ALB/NLB
   - Port 5701 (Hazelcast) - from same security group (for clustering)

2. **Outbound Rules:**
   - All traffic (for database, AWS API calls)

Example Security Group Rules:

```bash
# Inbound
Type: HTTP
Protocol: TCP
Port: 8080
Source: sg-alb-xxxxxxxxx (Load Balancer SG)

Type: Custom TCP
Protocol: TCP
Port: 5701
Source: sg-ecs-xxxxxxxxx (Same ECS Task SG)

# Outbound
Type: All traffic
Protocol: All
Port Range: All
Destination: 0.0.0.0/0
```

## ECS Service Configuration

### Service Discovery (Optional but Recommended)

Enable AWS Cloud Map for service discovery:

```bash
aws servicediscovery create-service \
  --name spring-playground \
  --dns-config 'NamespaceId="ns-xxxxxxxxx",DnsRecords=[{Type="A",TTL="10"}]' \
  --health-check-custom-config FailureThreshold=1
```

### Desired Count

For Hazelcast clustering, run at least 2 tasks:

```json
{
  "desiredCount": 2,
  "deploymentConfiguration": {
    "maximumPercent": 200,
    "minimumHealthyPercent": 100
  }
}
```

## Tagging for Hazelcast Discovery

Tag your ECS tasks for Hazelcast member discovery:

```bash
# In your ECS Task Definition
"tags": [
  {
    "key": "hazelcast-cluster",
    "value": "spring-playground"
  }
]
```

These tags must match the values in `hazelcast-ecs.yml`:
- `HAZELCAST_TAG_KEY=hazelcast-cluster`
- `HAZELCAST_TAG_VALUE=spring-playground`

## Deployment Steps

### 1. Build and Push Docker Image

```bash
# Build the application
mvn clean package -DskipTests

# Build Docker image
docker build -t spring-playground:latest .

# Tag for ECR
docker tag spring-playground:latest \
  123456789012.dkr.ecr.us-east-1.amazonaws.com/spring-playground:latest

# Login to ECR
aws ecr get-login-password --region us-east-1 | \
  docker login --username AWS --password-stdin \
  123456789012.dkr.ecr.us-east-1.amazonaws.com

# Push to ECR
docker push 123456789012.dkr.ecr.us-east-1.amazonaws.com/spring-playground:latest
```

### 2. Create Task Definition

```bash
aws ecs register-task-definition \
  --cli-input-json file://task-definition.json
```

### 3. Create ECS Service

```bash
aws ecs create-service \
  --cluster spring-playground-cluster \
  --service-name spring-playground \
  --task-definition spring-playground:1 \
  --desired-count 2 \
  --launch-type FARGATE \
  --network-configuration "awsvpcConfiguration={subnets=[subnet-xxx,subnet-yyy],securityGroups=[sg-xxxxxxxxx],assignPublicIp=ENABLED}" \
  --load-balancers "targetGroupArn=arn:aws:elasticloadbalancing:...,containerName=spring-playground,containerPort=8080"
```

## Verify Hazelcast Clustering

### 1. Check Application Logs

```bash
aws logs tail /ecs/spring-playground --follow

# Look for:
# Loading Hazelcast configuration from: hazelcast-ecs.yml
# Members {size:2, ver:2} [
#   Member [10.0.1.100]:5701 - this
#   Member [10.0.1.101]:5701
# ]
```

### 2. Test Idempotency Across Instances

```bash
# First request - creates cache on instance 1
curl -X GET "http://your-alb-dns/api/test/random?requestId=test-123"
# {"randomNumber": 456}

# Second request - should hit instance 2 but return cached value
curl -X GET "http://your-alb-dns/api/test/random?requestId=test-123"
# {"randomNumber": 456}  ← Same number!
```

### 3. Check Actuator Endpoints

```bash
# Health check
curl http://your-alb-dns/actuator/health

# Hazelcast cluster info
curl http://your-alb-dns/actuator/hazelcast
```

## Troubleshooting

### Issue: Tasks can't discover each other

**Solution:**
1. Check IAM role has `ec2:DescribeInstances` permission
2. Verify security group allows port 5701 between tasks
3. Check tags are correctly set on ECS tasks
4. Enable DEBUG logging: `com.hazelcast.aws: DEBUG`

### Issue: Hazelcast members keep leaving/joining

**Solution:**
1. Increase health check `startPeriod` to 90 seconds
2. Check network connectivity between tasks
3. Verify task resources (CPU/Memory) are sufficient
4. Check CloudWatch logs for errors

### Issue: Can't connect to RDS

**Solution:**
1. Ensure ECS tasks are in same VPC as RDS
2. Check RDS security group allows connections from ECS SG
3. Verify connection string is correct
4. Check IAM database authentication if enabled

## Cost Optimization

1. **Use Fargate Spot** for non-production environments
2. **Right-size resources**: Start with 512 CPU / 1024 MB RAM
3. **Enable Auto Scaling**: Scale based on CPU/Memory metrics
4. **Use Aurora Serverless** for database to reduce costs

## Monitoring

### CloudWatch Metrics

Important metrics to monitor:
- `CPUUtilization`
- `MemoryUtilization`
- `TargetResponseTime` (ALB)
- Custom metrics via Micrometer/Prometheus

### CloudWatch Logs

Enable log streaming:

```json
{
  "logConfiguration": {
    "logDriver": "awslogs",
    "options": {
      "awslogs-group": "/ecs/spring-playground",
      "awslogs-region": "us-east-1",
      "awslogs-stream-prefix": "ecs"
    }
  }
}
```

## Production Checklist

- [ ] IAM roles configured with least privilege
- [ ] Secrets stored in AWS Secrets Manager (not env vars)
- [ ] Multi-AZ deployment for high availability
- [ ] Auto Scaling configured
- [ ] CloudWatch alarms set up
- [ ] Backup strategy for RDS
- [ ] SSL/TLS certificates configured on ALB
- [ ] WAF enabled on ALB
- [ ] VPC Flow Logs enabled
- [ ] Container insights enabled

## References

- [AWS ECS Documentation](https://docs.aws.amazon.com/ecs/)
- [Hazelcast AWS Plugin](https://github.com/hazelcast/hazelcast-aws)
- [Spring Boot on AWS](https://spring.io/guides/gs/spring-boot-on-aws/)
