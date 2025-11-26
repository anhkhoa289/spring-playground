.PHONY: help install run sonar-start sonar-stop sonar-token sonar-scan clean

# Variables
SONAR_HOST_URL := http://localhost:9000
SONAR_LOGIN := admin
SONAR_PASSWORD := admin
SONAR_TOKEN_NAME := spring-playground-token
PROJECT_KEY := com.khoa.spring:playground
PROJECT_NAME := playground
PROJECT_VERSION := 0.0.1-SNAPSHOT

help:
	@echo "Available targets:"
	@echo "  install        - Clean and install project without running tests"
	@echo "  run            - Run the Spring Boot application"
	@echo "  sonar-start    - Start SonarQube container"
	@echo "  sonar-stop     - Stop SonarQube container"
	@echo "  sonar-token    - Generate and display SonarQube token"
	@echo "  sonar-scan     - Run SonarQube scan locally using Docker"
	@echo "  clean          - Remove SonarQube volumes and data"

# Clean and install project without running tests
install:
	@echo "Cleaning and installing project without tests..."
	./mvnw clean install -DskipTests
	@echo "Build completed successfully!"

# Run the Spring Boot application
run:
	@echo "Starting Spring Boot application..."
	./mvnw spring-boot:run

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
