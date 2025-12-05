#!/bin/bash

# Build and push Spring Boot application to AWS ECR using Cloud Native Buildpacks
# Usage: ./scripts/build-and-push.sh [aws-region] [ecr-repository-name]

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Default values
AWS_REGION=${1:-"us-east-1"}
ECR_REPO_NAME=${2:-"spring-playground-dev"}

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Building and Pushing to AWS ECR${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo -e "AWS Region: ${YELLOW}${AWS_REGION}${NC}"
echo -e "ECR Repository: ${YELLOW}${ECR_REPO_NAME}${NC}"
echo ""

# Get AWS account ID
echo -e "${GREEN}[1/6] Getting AWS account ID...${NC}"
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
if [ -z "$AWS_ACCOUNT_ID" ]; then
    echo -e "${RED}Error: Failed to get AWS account ID. Please check AWS credentials.${NC}"
    exit 1
fi
echo -e "Account ID: ${YELLOW}${AWS_ACCOUNT_ID}${NC}"

# ECR repository URL
ECR_REPO_URL="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/${ECR_REPO_NAME}"
echo -e "ECR URL: ${YELLOW}${ECR_REPO_URL}${NC}"
echo ""

# Authenticate Docker to ECR
echo -e "${GREEN}[2/6] Authenticating Docker to ECR...${NC}"
aws ecr get-login-password --region ${AWS_REGION} | docker login --username AWS --password-stdin ${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com
echo ""

# Build image with Cloud Native Buildpacks
echo -e "${GREEN}[3/6] Building image with Cloud Native Buildpacks...${NC}"
echo -e "${YELLOW}This may take several minutes on first build...${NC}"
./mvnw spring-boot:build-image \
    -Dspring-boot.build-image.imageName=${ECR_REPO_URL}:latest \
    -Dspring-boot.build-image.publish=false
echo ""

# Tag image with git commit hash
echo -e "${GREEN}[4/6] Tagging image...${NC}"
GIT_COMMIT=$(git rev-parse --short HEAD 2>/dev/null || echo "local")
docker tag ${ECR_REPO_URL}:latest ${ECR_REPO_URL}:${GIT_COMMIT}
echo -e "Tagged as: ${YELLOW}${ECR_REPO_URL}:${GIT_COMMIT}${NC}"
echo ""

# Push images to ECR
echo -e "${GREEN}[5/6] Pushing images to ECR...${NC}"
docker push ${ECR_REPO_URL}:latest
docker push ${ECR_REPO_URL}:${GIT_COMMIT}
echo ""

# Update ECS service (optional)
echo -e "${GREEN}[6/6] Deployment info${NC}"
echo -e "${YELLOW}Image pushed successfully!${NC}"
echo ""
echo -e "To update ECS service, run:"
echo -e "${YELLOW}aws ecs update-service --cluster spring-playground-dev-cluster --service spring-playground-dev-service --force-new-deployment --region ${AWS_REGION}${NC}"
echo ""
echo -e "Or update the task definition with new image:"
echo -e "${YELLOW}${ECR_REPO_URL}:${GIT_COMMIT}${NC}"
echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Build and Push Completed!${NC}"
echo -e "${GREEN}========================================${NC}"
