include scripts/requests.mk

.PHONY: help install run run-ecs build-image sonar-start sonar-stop sonar-token sonar-scan clean \
        ecr-login ecr-create ecr-build ecr-push ecr-build-push ecr-deploy ecr-update ecr-status ecr-logs ecr-images \
        terraform-init terraform-plan terraform-apply terraform-output terraform-destroy \
        ecs-status ecs-tasks ecs-scale ecs-shell alb-url health-check info

# Variables
SONAR_HOST_URL := http://localhost:9000
SONAR_LOGIN := admin
SONAR_PASSWORD := admin
SONAR_TOKEN_NAME := spring-playground-token
PROJECT_KEY := com.khoa.spring:playground
PROJECT_NAME := playground
PROJECT_VERSION := 0.0.1-SNAPSHOT
IMAGE_NAME := spring-playground:latest

# AWS/ECS Variables
AWS_REGION ?= us-east-1
ENVIRONMENT ?= dev
ECR_REPO_NAME ?= $(PROJECT_NAME)-$(ENVIRONMENT)
AWS_ACCOUNT_ID := $(shell aws sts get-caller-identity --query Account --output text 2>/dev/null)
ECR_REPO_URL := $(AWS_ACCOUNT_ID).dkr.ecr.$(AWS_REGION).amazonaws.com/$(ECR_REPO_NAME)
GIT_COMMIT := $(shell git rev-parse --short HEAD 2>/dev/null || echo "local")
IMAGE_TAG ?= latest
ECS_CLUSTER := $(PROJECT_NAME)-$(ENVIRONMENT)-cluster
ECS_SERVICE := $(PROJECT_NAME)-$(ENVIRONMENT)-service

# Colors
GREEN := \033[0;32m
YELLOW := \033[1;33m
RED := \033[0;31m
NC := \033[0m

help:
	@echo "$(GREEN)========================================$(NC)"
	@echo "$(GREEN)Spring Playground - Available Commands$(NC)"
	@echo "$(GREEN)========================================$(NC)"
	@echo ""
	@echo "$(YELLOW)Local Development:$(NC)"
	@echo "  install        - Clean and install project without running tests"
	@echo "  run            - Run the Spring Boot application"
	@echo "  run-ecs        - Run the Spring Boot application with ECS profile"
	@echo "  build-image    - Build Docker image using Spring Boot buildpacks"
	@echo ""
	@echo "$(YELLOW)AWS ECR & ECS Deployment:$(NC)"
	@echo "  ecr-login      - Authenticate Docker to AWS ECR"
	@echo "  ecr-create     - Create ECR repository if it doesn't exist"
	@echo "  ecr-build      - Build Docker image for ECR with buildpacks"
	@echo "  ecr-push       - Push Docker image to ECR"
	@echo "  ecr-build-push - Build and push image to ECR"
	@echo "  ecr-deploy     - Full deployment (build, push, update ECS)"
	@echo "  ecr-update     - Update ECS service with new deployment"
	@echo "  ecr-images     - List images in ECR repository"
	@echo ""
	@echo "$(YELLOW)ECS Management:$(NC)"
	@echo "  ecs-status     - Check ECS service status"
	@echo "  ecs-tasks      - List running ECS tasks"
	@echo "  ecs-scale      - Scale ECS service (usage: make ecs-scale COUNT=5)"
	@echo "  ecs-shell      - Get shell access to running ECS task"
	@echo "  ecr-logs       - Tail CloudWatch logs from ECS"
	@echo ""
	@echo "$(YELLOW)Terraform:$(NC)"
	@echo "  terraform-init    - Initialize Terraform"
	@echo "  terraform-plan    - Run Terraform plan"
	@echo "  terraform-apply   - Apply Terraform configuration"
	@echo "  terraform-output  - Show Terraform outputs"
	@echo "  terraform-destroy - Destroy Terraform infrastructure"
	@echo ""
	@echo "$(YELLOW)Utilities:$(NC)"
	@echo "  alb-url        - Get Application Load Balancer URL"
	@echo "  health-check   - Check application health via ALB"
	@echo "  info           - Show deployment information"
	@echo ""
	@echo "$(YELLOW)SonarQube:$(NC)"
	@echo "  sonar-start    - Start SonarQube container"
	@echo "  sonar-stop     - Stop SonarQube container"
	@echo "  sonar-token    - Generate and display SonarQube token"
	@echo "  sonar-scan     - Run SonarQube scan locally using Docker"
	@echo "  clean          - Remove SonarQube volumes and data"
	@echo ""
	@echo "$(YELLOW)Current Configuration:$(NC)"
	@echo "  AWS_REGION     = $(AWS_REGION)"
	@echo "  ENVIRONMENT    = $(ENVIRONMENT)"
	@echo "  ECR_REPO_NAME  = $(ECR_REPO_NAME)"
	@echo "  AWS_ACCOUNT_ID = $(AWS_ACCOUNT_ID)"
	@echo "  IMAGE_TAG      = $(IMAGE_TAG)"
	@echo ""

# Clean and install project without running tests
install:
	@echo "Cleaning and installing project without tests..."
	./mvnw clean install -DskipTests
	@echo "Build completed successfully!"

# Run the Spring Boot application
run:
	@echo "Starting Spring Boot application..."
	./mvnw spring-boot:run

# Run the Spring Boot application with ECS profile
run-ecs:
	@echo "Starting Spring Boot application with ECS profile..."
	./mvnw spring-boot:run -Dspring-boot.run.profiles=ecs



# Build Docker image using Spring Boot buildpacks
build-image:
	@echo "Building Docker image: $(IMAGE_NAME)"
	@echo "Note: Using Java 21 runtime (configured in pom.xml)"
	@echo "Tip: Ensure Docker is running before building"
	./mvnw spring-boot:build-image \
		-Dspring-boot.build-image.imageName=$(IMAGE_NAME)
	@echo ""
	@echo "Image built successfully: $(IMAGE_NAME)"

# Start SonarQube service
sonar-start:
	@echo "Starting SonarQube..."
	docker-compose up -d sonarqube
	@echo "Waiting for SonarQube to be ready..."
	@echo "SonarQube will be available at $(SONAR_HOST_URL)"
	@echo "Default credentials: admin/admin (you'll be prompted to change on first login)"

# Stop SonarQube service
sonar-stop:
	@echo "Stopping SonarQube..."
	docker-compose stop sonarqube

# Generate SonarQube token
sonar-token:
	@echo "Generating SonarQube token..."
	@echo "NOTE: You need to change the default password first by logging in to $(SONAR_HOST_URL)"
	@echo ""
	@TOKEN=$$(curl -s -u $(SONAR_LOGIN):$(SONAR_PASSWORD) \
		-X POST "$(SONAR_HOST_URL)/api/user_tokens/generate?name=$(SONAR_TOKEN_NAME)" \
		| grep -o '"token":"[^"]*"' | cut -d'"' -f4); \
	if [ -z "$$TOKEN" ]; then \
		echo "Failed to generate token. Please ensure:"; \
		echo "1. SonarQube is running (make sonar-start)"; \
		echo "2. You have changed the default password"; \
		echo "3. Your credentials are correct"; \
	else \
		echo "Token generated successfully:"; \
		echo "$$TOKEN"; \
		echo ""; \
		echo "Save this token - you won't be able to see it again!"; \
		echo "Add it to your environment:"; \
		echo "  export SONAR_TOKEN=$$TOKEN"; \
	fi

# Run SonarQube scan using sonar-scanner Docker image
sonar-scan:
	@echo "Running SonarQube scan..."
	@if [ -z "$$SONAR_TOKEN" ]; then \
		echo "Error: SONAR_TOKEN environment variable is not set"; \
		echo "Please run: export SONAR_TOKEN=<your-token>"; \
		echo "Or generate a new token with: make sonar-token"; \
		exit 1; \
	fi
	@echo "Building Maven project..."
	./mvnw clean verify
	@echo "Running SonarQube analysis..."
	docker run --rm \
		--network spring-playground_spring-network \
		-v "$$(pwd):/usr/src" \
		-e SONAR_HOST_URL=$(SONAR_HOST_URL) \
		-e SONAR_TOKEN=$$SONAR_TOKEN \
		sonarsource/sonar-scanner-cli:latest \
		-Dsonar.projectKey=$(PROJECT_KEY) \
		-Dsonar.projectName=$(PROJECT_NAME) \
		-Dsonar.projectVersion=$(PROJECT_VERSION) \
		-Dsonar.sources=src/main/java \
		-Dsonar.java.binaries=target/classes \
		-Dsonar.junit.reportPaths=target/surefire-reports \
		-Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml
	@echo ""
	@echo "Analysis complete! View results at: $(SONAR_HOST_URL)/dashboard?id=$(PROJECT_KEY)"

# Clean SonarQube data
clean:
	@echo "Stopping and removing SonarQube containers and volumes..."
	docker-compose down -v sonarqube
	@echo "SonarQube data cleaned"

# ============================================
# AWS ECR & ECS Deployment Targets
# ============================================

check-aws:
	@echo "$(GREEN)Checking AWS credentials...$(NC)"
	@if [ -z "$(AWS_ACCOUNT_ID)" ]; then \
		echo "$(RED)Error: AWS credentials not configured$(NC)"; \
		exit 1; \
	fi
	@echo "$(GREEN)✓ AWS Account ID: $(AWS_ACCOUNT_ID)$(NC)"

ecr-login: check-aws
	@echo "$(GREEN)Authenticating Docker to ECR...$(NC)"
	@aws ecr get-login-password --region $(AWS_REGION) | docker login --username AWS --password-stdin $(AWS_ACCOUNT_ID).dkr.ecr.$(AWS_REGION).amazonaws.com
	@echo "$(GREEN)✓ Docker authenticated to ECR$(NC)"

ecr-create: check-aws
	@echo "$(GREEN)Creating ECR repository...$(NC)"
	@aws ecr describe-repositories --repository-names $(ECR_REPO_NAME) --region $(AWS_REGION) > /dev/null 2>&1 || \
		aws ecr create-repository \
			--repository-name $(ECR_REPO_NAME) \
			--region $(AWS_REGION) \
			--image-scanning-configuration scanOnPush=true \
			--encryption-configuration encryptionType=AES256
	@echo "$(GREEN)✓ ECR repository ready: $(ECR_REPO_NAME)$(NC)"

ecr-build: ecr-login
	@echo "$(GREEN)========================================$(NC)"
	@echo "$(GREEN)Building image with Buildpacks for ECR$(NC)"
	@echo "$(GREEN)========================================$(NC)"
	@echo "$(YELLOW)Image: $(ECR_REPO_URL):$(IMAGE_TAG)$(NC)"
	@echo "$(YELLOW)Commit: $(GIT_COMMIT)$(NC)"
	@echo ""
	./mvnw spring-boot:build-image \
		-Dspring-boot.build-image.imageName=$(ECR_REPO_URL):$(IMAGE_TAG) \
		-Dspring-boot.build-image.publish=false
	@docker tag $(ECR_REPO_URL):$(IMAGE_TAG) $(ECR_REPO_URL):$(GIT_COMMIT)
	@echo ""
	@echo "$(GREEN)✓ Build complete$(NC)"
	@echo "$(GREEN)  Tagged: $(ECR_REPO_URL):$(IMAGE_TAG)$(NC)"
	@echo "$(GREEN)  Tagged: $(ECR_REPO_URL):$(GIT_COMMIT)$(NC)"

ecr-push: ecr-login
	@echo "$(GREEN)Pushing images to ECR...$(NC)"
	@docker push $(ECR_REPO_URL):$(IMAGE_TAG)
	@docker push $(ECR_REPO_URL):$(GIT_COMMIT)
	@echo "$(GREEN)✓ Push complete$(NC)"
	@echo "$(GREEN)  Pushed: $(ECR_REPO_URL):$(IMAGE_TAG)$(NC)"
	@echo "$(GREEN)  Pushed: $(ECR_REPO_URL):$(GIT_COMMIT)$(NC)"

ecr-build-push: ecr-build ecr-push
	@echo "$(GREEN)========================================$(NC)"
	@echo "$(GREEN)Build and Push Complete!$(NC)"
	@echo "$(GREEN)========================================$(NC)"

ecr-update: check-aws
	@echo "$(GREEN)Updating ECS service...$(NC)"
	@aws ecs update-service \
		--cluster $(ECS_CLUSTER) \
		--service $(ECS_SERVICE) \
		--force-new-deployment \
		--region $(AWS_REGION) > /dev/null
	@echo "$(GREEN)✓ ECS service deployment started$(NC)"
	@echo "$(YELLOW)Monitor deployment: make ecr-logs$(NC)"

ecr-deploy: ecr-build-push ecr-update
	@echo "$(GREEN)========================================$(NC)"
	@echo "$(GREEN)Full Deployment Complete!$(NC)"
	@echo "$(GREEN)========================================$(NC)"
	@echo "$(YELLOW)Check status: make ecs-status$(NC)"
	@echo "$(YELLOW)View logs: make ecr-logs$(NC)"

ecr-images: check-aws
	@echo "$(GREEN)ECR Images:$(NC)"
	@aws ecr describe-images \
		--repository-name $(ECR_REPO_NAME) \
		--region $(AWS_REGION) \
		--query 'sort_by(imageDetails,&imagePushedAt)[*].[imageTags[0],imagePushedAt,imageSizeInBytes]' \
		--output table 2>/dev/null || echo "$(YELLOW)No images found$(NC)"

# ============================================
# ECS Management Targets
# ============================================

ecs-status: check-aws
	@echo "$(GREEN)ECS Service Status:$(NC)"
	@aws ecs describe-services \
		--cluster $(ECS_CLUSTER) \
		--services $(ECS_SERVICE) \
		--region $(AWS_REGION) \
		--query 'services[0].{Status:status,Running:runningCount,Desired:desiredCount,Pending:pendingCount}' \
		--output table

ecs-tasks: check-aws
	@echo "$(GREEN)Running ECS Tasks:$(NC)"
	@aws ecs list-tasks \
		--cluster $(ECS_CLUSTER) \
		--service-name $(ECS_SERVICE) \
		--region $(AWS_REGION) \
		--output table

ecs-scale: check-aws
	@if [ -z "$(COUNT)" ]; then \
		echo "$(RED)Error: COUNT not specified$(NC)"; \
		echo "Usage: make ecs-scale COUNT=5"; \
		exit 1; \
	fi
	@echo "$(GREEN)Scaling ECS service to $(COUNT) tasks...$(NC)"
	@aws ecs update-service \
		--cluster $(ECS_CLUSTER) \
		--service $(ECS_SERVICE) \
		--desired-count $(COUNT) \
		--region $(AWS_REGION) > /dev/null
	@echo "$(GREEN)✓ Service scaled to $(COUNT) tasks$(NC)"

ecs-shell: check-aws
	@echo "$(GREEN)Getting shell access to ECS task...$(NC)"
	@TASK_ARN=$$(aws ecs list-tasks \
		--cluster $(ECS_CLUSTER) \
		--service-name $(ECS_SERVICE) \
		--region $(AWS_REGION) \
		--query 'taskArns[0]' \
		--output text); \
	if [ -n "$$TASK_ARN" ] && [ "$$TASK_ARN" != "None" ]; then \
		aws ecs execute-command \
			--cluster $(ECS_CLUSTER) \
			--task $$TASK_ARN \
			--container $(PROJECT_NAME)-container \
			--interactive \
			--command "/bin/sh" \
			--region $(AWS_REGION); \
	else \
		echo "$(RED)No running tasks found$(NC)"; \
	fi

ecr-logs: check-aws
	@echo "$(GREEN)Tailing CloudWatch logs...$(NC)"
	@aws logs tail /ecs/$(PROJECT_NAME)-$(ENVIRONMENT) --follow --region $(AWS_REGION)

# ============================================
# Terraform Targets
# ============================================

terraform-init:
	@echo "$(GREEN)Initializing Terraform...$(NC)"
	@cd terraform && terraform init
	@echo "$(GREEN)✓ Terraform initialized$(NC)"

terraform-plan:
	@echo "$(GREEN)Running Terraform plan...$(NC)"
	@cd terraform && terraform plan

terraform-apply:
	@echo "$(GREEN)Applying Terraform configuration...$(NC)"
	@cd terraform && terraform apply
	@echo "$(GREEN)✓ Infrastructure deployed$(NC)"

terraform-output:
	@echo "$(GREEN)Terraform Outputs:$(NC)"
	@cd terraform && terraform output

terraform-destroy:
	@echo "$(RED)WARNING: This will destroy all infrastructure!$(NC)"
	@read -p "Are you sure? [y/N] " -n 1 -r; \
	echo; \
	if [[ $$REPLY =~ ^[Yy]$$ ]]; then \
		cd terraform && terraform destroy; \
	fi

# ============================================
# Utility Targets
# ============================================

alb-url:
	@cd terraform && terraform output -raw alb_url 2>/dev/null || echo "$(YELLOW)Run 'make terraform-apply' first$(NC)"

health-check:
	@echo "$(GREEN)Checking application health...$(NC)"
	@ALB_URL=$$(cd terraform && terraform output -raw alb_url 2>/dev/null); \
	if [ -n "$$ALB_URL" ]; then \
		curl -s $$ALB_URL/actuator/health | jq . || curl -s $$ALB_URL/actuator/health; \
	else \
		echo "$(RED)ALB not deployed. Run 'make terraform-apply' first$(NC)"; \
	fi

info:
	@echo "$(GREEN)========================================$(NC)"
	@echo "$(GREEN)Deployment Information$(NC)"
	@echo "$(GREEN)========================================$(NC)"
	@echo ""
	@echo "$(YELLOW)AWS Configuration:$(NC)"
	@echo "  Region:           $(AWS_REGION)"
	@echo "  Account ID:       $(AWS_ACCOUNT_ID)"
	@echo ""
	@echo "$(YELLOW)ECS Configuration:$(NC)"
	@echo "  Cluster:          $(ECS_CLUSTER)"
	@echo "  Service:          $(ECS_SERVICE)"
	@echo ""
	@echo "$(YELLOW)ECR Configuration:$(NC)"
	@echo "  Repository:       $(ECR_REPO_NAME)"
	@echo "  URL:              $(ECR_REPO_URL)"
	@echo ""
	@echo "$(YELLOW)Image:$(NC)"
	@echo "  Tag:              $(IMAGE_TAG)"
	@echo "  Commit:           $(GIT_COMMIT)"
	@echo ""
	@echo "$(YELLOW)Application:$(NC)"
	@ALB_URL=$$(cd terraform && terraform output -raw alb_url 2>/dev/null); \
	if [ -n "$$ALB_URL" ]; then \
		echo "  URL:              $$ALB_URL"; \
		echo "  Health:           $$ALB_URL/actuator/health"; \
	else \
		echo "  $(RED)Not deployed yet$(NC)"; \
	fi
	@echo ""
