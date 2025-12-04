# Makefile for testing API endpoints
# Usage: make -f requests.mk <target>

BASE_URL := http://localhost:8080

# Variables for storing tokens
USER_TOKEN ?=
MANAGER_TOKEN ?=

###############################################################################
# User Authentication (User Keycloak)
###############################################################################

.PHONY: user-register
user-register:
	@echo "Registering new user..."
	@curl -X POST $(BASE_URL)/api/auth/register \
		-H "Content-Type: application/json" \
		-d '{"email":"user@example.com","password":"Password123!","firstName":"Test","lastName":"User"}' \
		-w "\nHTTP Status: %{http_code}\n"

.PHONY: user-login
user-login:
	@echo "User login..."
	@curl -X POST $(BASE_URL)/api/auth/login \
		-H "Content-Type: application/json" \
		-d '{"email":"user@example.com","password":"Password123!"}' \
		-w "\nHTTP Status: %{http_code}\n"

.PHONY: user-me
user-me:
	@echo "Get current user info..."
	@if [ -z "$(USER_TOKEN)" ]; then \
		echo "Error: USER_TOKEN not set. Use: make -f requests.mk user-me USER_TOKEN=<token>"; \
		exit 1; \
	fi
	@curl -X GET $(BASE_URL)/api/auth/me \
		-H "Authorization: Bearer $(USER_TOKEN)" \
		-w "\nHTTP Status: %{http_code}\n"

###############################################################################
# Manager Authentication (Manager Keycloak)
###############################################################################

.PHONY: manager-register
manager-register:
	@echo "Registering new manager..."
	@curl -X POST $(BASE_URL)/api/manager-auth/register \
		-H "Content-Type: application/json" \
		-d '{"email":"manager@example.com","password":"Password123!","firstName":"Test","lastName":"Manager"}' \
		-w "\nHTTP Status: %{http_code}\n"

.PHONY: manager-login
manager-login:
	@echo "Manager login..."
	@curl -X POST $(BASE_URL)/api/manager-auth/login \
		-H "Content-Type: application/json" \
		-d '{"email":"manager@example.com","password":"Password123!"}' \
		-w "\nHTTP Status: %{http_code}\n"

.PHONY: manager-me
manager-me:
	@echo "Get current manager info..."
	@if [ -z "$(MANAGER_TOKEN)" ]; then \
		echo "Error: MANAGER_TOKEN not set. Use: make -f requests.mk manager-me MANAGER_TOKEN=<token>"; \
		exit 1; \
	fi
	@curl -X GET $(BASE_URL)/api/manager-auth/me \
		-H "Authorization: Bearer $(MANAGER_TOKEN)" \
		-w "\nHTTP Status: %{http_code}\n"

###############################################################################
# Role-Based Access Control Testing
###############################################################################

.PHONY: test-public
test-public:
	@echo "Testing public endpoint (no authentication required)..."
	@curl -X GET $(BASE_URL)/api/role-test/public \
		-w "\nHTTP Status: %{http_code}\n"

.PHONY: test-authenticated
test-authenticated:
	@echo "Testing authenticated endpoint..."
	@if [ -z "$(USER_TOKEN)" ]; then \
		echo "Error: USER_TOKEN not set. Use: make -f requests.mk test-authenticated USER_TOKEN=<token>"; \
		exit 1; \
	fi
	@curl -X GET $(BASE_URL)/api/role-test/authenticated \
		-H "Authorization: Bearer $(USER_TOKEN)" \
		-w "\nHTTP Status: %{http_code}\n"

.PHONY: test-user-only
test-user-only:
	@echo "Testing user-only endpoint (requires ROLE_USER)..."
	@if [ -z "$(USER_TOKEN)" ]; then \
		echo "Error: USER_TOKEN not set. Use: make -f requests.mk test-user-only USER_TOKEN=<token>"; \
		exit 1; \
	fi
	@curl -X GET $(BASE_URL)/api/role-test/user-only \
		-H "Authorization: Bearer $(USER_TOKEN)" \
		-w "\nHTTP Status: %{http_code}\n"

.PHONY: test-manager-only
test-manager-only:
	@echo "Testing manager-only endpoint (requires ROLE_MANAGER)..."
	@if [ -z "$(MANAGER_TOKEN)" ]; then \
		echo "Error: MANAGER_TOKEN not set. Use: make -f requests.mk test-manager-only MANAGER_TOKEN=<token>"; \
		exit 1; \
	fi
	@curl -X GET $(BASE_URL)/api/role-test/manager-only \
		-H "Authorization: Bearer $(MANAGER_TOKEN)" \
		-w "\nHTTP Status: %{http_code}\n"

.PHONY: test-user-or-manager
test-user-or-manager:
	@echo "Testing user-or-manager endpoint (requires ROLE_USER or ROLE_MANAGER)..."
	@if [ -z "$(USER_TOKEN)" ]; then \
		echo "Error: USER_TOKEN not set. Use: make -f requests.mk test-user-or-manager USER_TOKEN=<token>"; \
		exit 1; \
	fi
	@curl -X GET $(BASE_URL)/api/role-test/user-or-manager \
		-H "Authorization: Bearer $(USER_TOKEN)" \
		-w "\nHTTP Status: %{http_code}\n"

.PHONY: test-admin
test-admin:
	@echo "Testing admin endpoint (requires both ROLE_USER and ROLE_MANAGER)..."
	@if [ -z "$(USER_TOKEN)" ]; then \
		echo "Error: USER_TOKEN not set. Use: make -f requests.mk test-admin USER_TOKEN=<token>"; \
		exit 1; \
	fi
	@curl -X GET $(BASE_URL)/api/role-test/admin \
		-H "Authorization: Bearer $(USER_TOKEN)" \
		-w "\nHTTP Status: %{http_code}\n"

.PHONY: test-my-roles
test-my-roles:
	@echo "Getting current user roles..."
	@if [ -z "$(USER_TOKEN)" ]; then \
		echo "Error: USER_TOKEN not set. Use: make -f requests.mk test-my-roles USER_TOKEN=<token>"; \
		exit 1; \
	fi
	@curl -X GET $(BASE_URL)/api/role-test/my-roles \
		-H "Authorization: Bearer $(USER_TOKEN)" \
		-w "\nHTTP Status: %{http_code}\n"

###############################################################################
# Testing with Manager Token on User Endpoints (Should Fail)
###############################################################################

.PHONY: test-manager-on-user-endpoint
test-manager-on-user-endpoint:
	@echo "Testing manager token on user-only endpoint (should return 403)..."
	@if [ -z "$(MANAGER_TOKEN)" ]; then \
		echo "Error: MANAGER_TOKEN not set. Use: make -f requests.mk test-manager-on-user-endpoint MANAGER_TOKEN=<token>"; \
		exit 1; \
	fi
	@curl -X GET $(BASE_URL)/api/role-test/user-only \
		-H "Authorization: Bearer $(MANAGER_TOKEN)" \
		-w "\nHTTP Status: %{http_code}\n"

.PHONY: test-user-on-manager-endpoint
test-user-on-manager-endpoint:
	@echo "Testing user token on manager-only endpoint (should return 403)..."
	@if [ -z "$(USER_TOKEN)" ]; then \
		echo "Error: USER_TOKEN not set. Use: make -f requests.mk test-user-on-manager-endpoint USER_TOKEN=<token>"; \
		exit 1; \
	fi
	@curl -X GET $(BASE_URL)/api/role-test/manager-only \
		-H "Authorization: Bearer $(USER_TOKEN)" \
		-w "\nHTTP Status: %{http_code}\n"

###############################################################################
# Complete Test Flows
###############################################################################

.PHONY: test-user-flow
test-user-flow: user-register user-login
	@echo "\n=== User Flow Complete ==="
	@echo "Next steps:"
	@echo "1. Copy the access_token from the login response"
	@echo "2. Run: make -f requests.mk test-user-only USER_TOKEN=<your-token>"
	@echo "3. Run: make -f requests.mk test-my-roles USER_TOKEN=<your-token>"

.PHONY: test-manager-flow
test-manager-flow: manager-register manager-login
	@echo "\n=== Manager Flow Complete ==="
	@echo "Next steps:"
	@echo "1. Copy the access_token from the login response"
	@echo "2. Run: make -f requests.mk test-manager-only MANAGER_TOKEN=<your-token>"
	@echo "3. Run: make -f requests.mk test-my-roles USER_TOKEN=<your-token>"

.PHONY: help
help:
	@echo "Available targets:"
	@echo ""
	@echo "User Authentication:"
	@echo "  user-register              - Register a new user"
	@echo "  user-login                 - Login as user and get token"
	@echo "  user-me                    - Get current user info (requires USER_TOKEN)"
	@echo ""
	@echo "Manager Authentication:"
	@echo "  manager-register           - Register a new manager"
	@echo "  manager-login              - Login as manager and get token"
	@echo "  manager-me                 - Get current manager info (requires MANAGER_TOKEN)"
	@echo ""
	@echo "Role Testing:"
	@echo "  test-public                - Test public endpoint (no auth)"
	@echo "  test-authenticated         - Test authenticated endpoint (requires USER_TOKEN)"
	@echo "  test-user-only             - Test ROLE_USER endpoint (requires USER_TOKEN)"
	@echo "  test-manager-only          - Test ROLE_MANAGER endpoint (requires MANAGER_TOKEN)"
	@echo "  test-user-or-manager       - Test ROLE_USER or ROLE_MANAGER endpoint"
	@echo "  test-admin                 - Test endpoint requiring both roles"
	@echo "  test-my-roles              - Show current user's roles"
	@echo ""
	@echo "Cross-Testing:"
	@echo "  test-manager-on-user-endpoint  - Manager token on user endpoint (should fail)"
	@echo "  test-user-on-manager-endpoint  - User token on manager endpoint (should fail)"
	@echo ""
	@echo "Complete Flows:"
	@echo "  test-user-flow             - Complete user registration and login"
	@echo "  test-manager-flow          - Complete manager registration and login"
	@echo ""
	@echo "Usage with tokens:"
	@echo "  make -f requests.mk test-user-only USER_TOKEN=<your-token>"
	@echo "  make -f requests.mk test-manager-only MANAGER_TOKEN=<your-token>"
