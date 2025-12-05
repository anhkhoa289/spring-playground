# ============================================
# Spring Playground Makefile
# ============================================

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
AWS_REGION ?= ap-southeast-1
ENVIRONMENT ?= ecs
ECR_REPO_NAME ?= $(PROJECT_NAME)
AWS_ACCOUNT_ID := $(shell aws sts get-caller-identity --query Account --output text 2>/dev/null)
ECR_REPO_URL := $(AWS_ACCOUNT_ID).dkr.ecr.$(AWS_REGION).amazonaws.com/$(ECR_REPO_NAME)
GIT_COMMIT := $(shell git rev-parse --short HEAD 2>/dev/null || echo "local")
IMAGE_TAG ?= latest
ECS_CLUSTER := $(PROJECT_NAME)-$(ENVIRONMENT)-cluster
ECS_SERVICE := $(PROJECT_NAME)-$(ENVIRONMENT)-service
TASK_FAMILY := $(PROJECT_NAME)-task
TASK_EXECUTION_ROLE := $(PROJECT_NAME)-ecs-execution-role
TASK_ROLE := $(PROJECT_NAME)-ecs-task-role
CPU ?= 512
MEMORY ?= 1024
DESIRED_COUNT ?= 3
CONTAINER_PORT ?= 8080

# Colors
GREEN := \033[0;32m
YELLOW := \033[1;33m
RED := \033[0;31m
NC := \033[0m

# Include modular makefiles
include scripts/requests.mk
include scripts/common.mk
include scripts/sonar.mk
include scripts/aws-ecr.mk
include scripts/aws-ecs.mk
