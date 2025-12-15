# ============================================
# AWS ECS Targets
# ============================================

.PHONY: ecs-setup ecs-create-cluster ecs-create-roles ecs-create-task-def ecs-create-service \
        ecs-deploy ecs-update ecs-status ecs-tasks ecs-scale ecs-shell ecs-logs ecs-destroy

# ============================================
# ECS Infrastructure Setup
# ============================================

ecs-create-cluster: check-aws
	@echo "$(GREEN)Creating ECS cluster...$(NC)"
	@aws ecs describe-clusters --clusters $(ECS_CLUSTER) --region $(AWS_REGION) --query 'clusters[0].status' --output text 2>/dev/null | grep -q ACTIVE || \
		aws ecs create-cluster \
			--cluster-name $(ECS_CLUSTER) \
			--region $(AWS_REGION) \
			--capacity-providers FARGATE \
			--default-capacity-provider-strategy capacityProvider=FARGATE,weight=1 > /dev/null
	@echo "$(GREEN)✓ ECS cluster ready: $(ECS_CLUSTER)$(NC)"

ecs-create-roles: check-aws
	@echo "$(GREEN)Creating IAM roles for ECS...$(NC)"
	@# Create task execution role
	@aws iam get-role --role-name $(TASK_EXECUTION_ROLE) > /dev/null 2>&1 || \
		(aws iam create-role \
			--role-name $(TASK_EXECUTION_ROLE) \
			--assume-role-policy-document '{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"Service":"ecs-tasks.amazonaws.com"},"Action":"sts:AssumeRole"}]}' > /dev/null && \
		aws iam attach-role-policy \
			--role-name $(TASK_EXECUTION_ROLE) \
			--policy-arn arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy > /dev/null)
	@# Create task role
	@aws iam get-role --role-name $(TASK_ROLE) > /dev/null 2>&1 || \
		(aws iam create-role \
			--role-name $(TASK_ROLE) \
			--assume-role-policy-document '{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"Service":"ecs-tasks.amazonaws.com"},"Action":"sts:AssumeRole"}]}' > /dev/null && \
		aws iam put-role-policy \
			--role-name $(TASK_ROLE) \
			--policy-name ecs-task-policy \
			--policy-document '{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Action":["logs:*","ec2:DescribeInstances","ecs:*"],"Resource":"*"}]}' > /dev/null)
	@echo "$(GREEN)✓ IAM roles ready$(NC)"

ecs-create-task-def: check-aws
	@echo "$(GREEN)Creating ECS task definition...$(NC)"
	@echo '{ \
		"family": "$(TASK_FAMILY)", \
		"networkMode": "awsvpc", \
		"requiresCompatibilities": ["FARGATE"], \
		"cpu": "$(CPU)", \
		"memory": "$(MEMORY)", \
		"executionRoleArn": "arn:aws:iam::$(AWS_ACCOUNT_ID):role/$(TASK_EXECUTION_ROLE)", \
		"taskRoleArn": "arn:aws:iam::$(AWS_ACCOUNT_ID):role/$(TASK_ROLE)", \
		"containerDefinitions": [ \
			{ \
				"name": "$(PROJECT_NAME)-container", \
				"image": "$(ECR_REPO_URL):$(IMAGE_TAG)", \
				"essential": true, \
				"portMappings": [{"containerPort": $(CONTAINER_PORT), "protocol": "tcp"}], \
				"environment": [ \
					{"name": "SPRING_PROFILES_ACTIVE", "value": "ecs"}, \
					{"name": "SERVER_PORT", "value": "$(CONTAINER_PORT)"} \
				], \
				"logConfiguration": { \
					"logDriver": "awslogs", \
					"options": { \
						"awslogs-group": "/ecs/$(PROJECT_NAME)-$(ENVIRONMENT)", \
						"awslogs-region": "$(AWS_REGION)", \
						"awslogs-stream-prefix": "ecs", \
						"awslogs-create-group": "true" \
					} \
				}, \
				"healthCheck": { \
					"command": ["CMD-SHELL", "wget --no-verbose --tries=1 --spider http://localhost:$(CONTAINER_PORT)/actuator/health || exit 1"], \
					"interval": 30, \
					"timeout": 5, \
					"retries": 3, \
					"startPeriod": 60 \
				} \
			} \
		] \
	}' > /tmp/task-def.json
	@aws ecs register-task-definition \
		--cli-input-json file:///tmp/task-def.json \
		--region $(AWS_REGION) > /dev/null
	@rm /tmp/task-def.json
	@echo "$(GREEN)✓ Task definition registered: $(TASK_FAMILY)$(NC)"

ecs-create-service: check-aws
	@echo "$(GREEN)Creating ECS service...$(NC)"
	@# Get default VPC
	@VPC_ID=$$(aws ec2 describe-vpcs --filters "Name=isDefault,Values=true" --query 'Vpcs[0].VpcId' --output text --region $(AWS_REGION)); \
	if [ -z "$$VPC_ID" ] || [ "$$VPC_ID" = "None" ]; then \
		echo "$(RED)Error: No default VPC found. Please create a VPC first.$(NC)"; \
		exit 1; \
	fi; \
	SUBNETS=$$(aws ec2 describe-subnets --filters "Name=vpc-id,Values=$$VPC_ID" --query 'Subnets[*].SubnetId' --output text --region $(AWS_REGION) | tr '\t' ','); \
	SG_ID=$$(aws ec2 describe-security-groups --filters "Name=vpc-id,Values=$$VPC_ID" "Name=group-name,Values=$(PROJECT_NAME)-ecs-sg" --query 'SecurityGroups[0].GroupId' --output text --region $(AWS_REGION) 2>/dev/null); \
	if [ -z "$$SG_ID" ] || [ "$$SG_ID" = "None" ]; then \
		echo "$(YELLOW)Creating security group...$(NC)"; \
		SG_ID=$$(aws ec2 create-security-group \
			--group-name $(PROJECT_NAME)-ecs-sg \
			--description "Security group for $(PROJECT_NAME) ECS tasks" \
			--vpc-id $$VPC_ID \
			--region $(AWS_REGION) \
			--query 'GroupId' --output text); \
		aws ec2 authorize-security-group-ingress \
			--group-id $$SG_ID \
			--protocol tcp \
			--port $(CONTAINER_PORT) \
			--cidr 0.0.0.0/0 \
			--region $(AWS_REGION) > /dev/null 2>&1 || true; \
		aws ec2 authorize-security-group-egress \
			--group-id $$SG_ID \
			--protocol -1 \
			--cidr 0.0.0.0/0 \
			--region $(AWS_REGION) > /dev/null 2>&1 || true; \
	fi; \
	aws ecs describe-services --cluster $(ECS_CLUSTER) --services $(ECS_SERVICE) --region $(AWS_REGION) --query 'services[0].status' --output text 2>/dev/null | grep -q ACTIVE || \
	aws ecs create-service \
		--cluster $(ECS_CLUSTER) \
		--service-name $(ECS_SERVICE) \
		--task-definition $(TASK_FAMILY) \
		--desired-count $(DESIRED_COUNT) \
		--launch-type FARGATE \
		--network-configuration "awsvpcConfiguration={subnets=[$$SUBNETS],securityGroups=[$$SG_ID],assignPublicIp=ENABLED}" \
		--region $(AWS_REGION) > /dev/null
	@echo "$(GREEN)✓ ECS service created: $(ECS_SERVICE)$(NC)"

ecs-setup: ecr-create ecs-create-cluster ecs-create-roles ecs-create-task-def ecs-create-service
	@echo "$(GREEN)========================================$(NC)"
	@echo "$(GREEN)ECS Setup Complete!$(NC)"
	@echo "$(GREEN)========================================$(NC)"
	@echo "$(YELLOW)Now run: make ecr-build-push && make ecs-update$(NC)"

# ============================================
# ECS Deployment
# ============================================

ecs-update: check-aws
	@echo "$(GREEN)Updating ECS service...$(NC)"
	@# Re-register task definition with latest image
	@$(MAKE) ecs-create-task-def
	@# Update service
	@aws ecs update-service \
		--cluster $(ECS_CLUSTER) \
		--service $(ECS_SERVICE) \
		--task-definition $(TASK_FAMILY) \
		--force-new-deployment \
		--region $(AWS_REGION) > /dev/null
	@echo "$(GREEN)✓ ECS service deployment started$(NC)"
	@echo "$(YELLOW)Monitor deployment: make ecs-logs$(NC)"

ecs-deploy: ecr-build-push ecs-update
	@echo "$(GREEN)========================================$(NC)"
	@echo "$(GREEN)Full Deployment Complete!$(NC)"
	@echo "$(GREEN)========================================$(NC)"
	@echo "$(YELLOW)Check status: make ecs-status$(NC)"
	@echo "$(YELLOW)View logs: make ecs-logs$(NC)"

# ============================================
# ECS Management
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

ecs-logs: check-aws
	@echo "$(GREEN)Tailing CloudWatch logs...$(NC)"
	@aws logs tail /ecs/$(PROJECT_NAME)-$(ENVIRONMENT) --follow --region $(AWS_REGION)

# ============================================
# ECS Cleanup
# ============================================

ecs-destroy: check-aws
	@echo "$(RED)WARNING: This will destroy ECS resources!$(NC)"
	@read -p "Are you sure? [y/N] " -n 1 -r; \
	echo; \
	if [[ $$REPLY =~ ^[Yy]$$ ]]; then \
		echo "$(YELLOW)Deleting ECS service...$(NC)"; \
		aws ecs update-service --cluster $(ECS_CLUSTER) --service $(ECS_SERVICE) --desired-count 0 --region $(AWS_REGION) > /dev/null 2>&1 || true; \
		aws ecs delete-service --cluster $(ECS_CLUSTER) --service $(ECS_SERVICE) --force --region $(AWS_REGION) > /dev/null 2>&1 || true; \
		echo "$(YELLOW)Deleting ECS cluster...$(NC)"; \
		aws ecs delete-cluster --cluster $(ECS_CLUSTER) --region $(AWS_REGION) > /dev/null 2>&1 || true; \
		echo "$(YELLOW)Deregistering task definitions...$(NC)"; \
		for rev in $$(aws ecs list-task-definitions --family-prefix $(TASK_FAMILY) --region $(AWS_REGION) --query 'taskDefinitionArns[]' --output text); do \
			aws ecs deregister-task-definition --task-definition $$rev --region $(AWS_REGION) > /dev/null 2>&1 || true; \
		done; \
		echo "$(YELLOW)Deleting security group...$(NC)"; \
		VPC_ID=$$(aws ec2 describe-vpcs --filters "Name=isDefault,Values=true" --query 'Vpcs[0].VpcId' --output text --region $(AWS_REGION)); \
		SG_ID=$$(aws ec2 describe-security-groups --filters "Name=vpc-id,Values=$$VPC_ID" "Name=group-name,Values=$(PROJECT_NAME)-ecs-sg" --query 'SecurityGroups[0].GroupId' --output text --region $(AWS_REGION) 2>/dev/null); \
		if [ -n "$$SG_ID" ] && [ "$$SG_ID" != "None" ]; then \
			sleep 30; \
			aws ec2 delete-security-group --group-id $$SG_ID --region $(AWS_REGION) > /dev/null 2>&1 || true; \
		fi; \
		echo "$(GREEN)✓ ECS resources destroyed$(NC)"; \
	fi
