# ============================================
# Common Development Targets
# ============================================

.PHONY: help install run run-ecs build-image clean

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
	@echo "$(YELLOW)ECR Management:$(NC)"
	@echo "  ecr-login      - Authenticate Docker to AWS ECR"
	@echo "  ecr-create     - Create ECR repository if it doesn't exist"
	@echo "  ecr-build      - Build Docker image for ECR with buildpacks"
	@echo "  ecr-push       - Push Docker image to ECR"
	@echo "  ecr-build-push - Build and push image to ECR"
	@echo "  ecr-images     - List images in ECR repository"
	@echo ""
	@echo "$(YELLOW)ECS Setup & Deployment:$(NC)"
	@echo "  ecs-setup          - Complete ECS setup (cluster + roles + task def + service)"
	@echo "  ecs-create-cluster - Create ECS cluster"
	@echo "  ecs-create-roles   - Create IAM roles for ECS"
	@echo "  ecs-create-task-def - Create ECS task definition"
	@echo "  ecs-create-service - Create ECS service"
	@echo "  ecs-deploy         - Full deployment (build, push, create/update service)"
	@echo "  ecs-update         - Update ECS service with new deployment"
	@echo "  ecs-destroy        - Destroy ECS resources"
	@echo ""
	@echo "$(YELLOW)ECS Management:$(NC)"
	@echo "  ecs-status     - Check ECS service status"
	@echo "  ecs-tasks      - List running ECS tasks"
	@echo "  ecs-scale      - Scale ECS service (usage: make ecs-scale COUNT=5)"
	@echo "  ecs-shell      - Get shell access to running ECS task"
	@echo "  ecs-logs       - Tail CloudWatch logs from ECS"
	@echo ""
	@echo "$(YELLOW)Utilities:$(NC)"
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

# Common AWS credential check
check-aws:
	@echo "$(GREEN)Checking AWS credentials...$(NC)"
	@if [ -z "$(AWS_ACCOUNT_ID)" ]; then \
		echo "$(RED)Error: AWS credentials not configured$(NC)"; \
		exit 1; \
	fi
	@echo "$(GREEN)✓ AWS Account ID: $(AWS_ACCOUNT_ID)$(NC)"

# Show deployment information
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
	@echo "  Task Family:      $(TASK_FAMILY)"
	@echo "  Desired Count:    $(DESIRED_COUNT)"
	@echo "  CPU:              $(CPU)"
	@echo "  Memory:           $(MEMORY)"
	@echo ""
	@echo "$(YELLOW)ECR Configuration:$(NC)"
	@echo "  Repository:       $(ECR_REPO_NAME)"
	@echo "  URL:              $(ECR_REPO_URL)"
	@echo ""
	@echo "$(YELLOW)Image:$(NC)"
	@echo "  Tag:              $(IMAGE_TAG)"
	@echo "  Commit:           $(GIT_COMMIT)"
	@echo ""
	@echo "$(YELLOW)Task IPs:$(NC)"
	@TASK_ARNS=$$(aws ecs list-tasks --cluster $(ECS_CLUSTER) --service-name $(ECS_SERVICE) --region $(AWS_REGION) --query 'taskArns[]' --output text 2>/dev/null); \
	if [ -n "$$TASK_ARNS" ]; then \
		aws ecs describe-tasks --cluster $(ECS_CLUSTER) --tasks $$TASK_ARNS --region $(AWS_REGION) \
			--query 'tasks[].attachments[0].details[?name==`networkInterfaceId`].value' --output text 2>/dev/null | \
		xargs -I {} aws ec2 describe-network-interfaces --network-interface-ids {} --region $(AWS_REGION) \
			--query 'NetworkInterfaces[].Association.PublicIp' --output text 2>/dev/null | \
		while read ip; do \
			echo "  http://$$ip:$(CONTAINER_PORT)/actuator/health"; \
		done || echo "  $(YELLOW)Not available yet$(NC)"; \
	else \
		echo "  $(YELLOW)No tasks running$(NC)"; \
	fi
	@echo ""
