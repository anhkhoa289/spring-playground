# ============================================
# AWS ECR Targets
# ============================================

.PHONY: ecr-login ecr-create ecr-build ecr-push ecr-build-push ecr-images

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

ecr-images: check-aws
	@echo "$(GREEN)ECR Images:$(NC)"
	@aws ecr describe-images \
		--repository-name $(ECR_REPO_NAME) \
		--region $(AWS_REGION) \
		--query 'sort_by(imageDetails,&imagePushedAt)[*].[imageTags[0],imagePushedAt,imageSizeInBytes]' \
		--output table 2>/dev/null || echo "$(YELLOW)No images found$(NC)"
