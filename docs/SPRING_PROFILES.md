# Spring Profiles Guide

This document explains the Spring Profile configuration strategy for the Spring Playground application.

## Profile Architecture

The application uses a **two-dimensional profile system**:

1. **Platform Profiles** - Hazelcast discovery mechanism
   - `default` - Local development (TCP-IP)
   - `k8s` - Kubernetes (Pod discovery)
   - `ecs` - AWS ECS (AWS API discovery)

2. **Environment Profiles** - Application configuration
   - `dev` - Development
   - `staging` - Staging/Pre-production
   - `prod` - Production

### Profile Combinations

Profiles can be combined using comma separation:

```bash
SPRING_PROFILES_ACTIVE=k8s,prod    # Kubernetes + Production
SPRING_PROFILES_ACTIVE=ecs,staging # AWS ECS + Staging
SPRING_PROFILES_ACTIVE=dev         # Local development
```

## Platform Profiles

### Default (Local Development)

**Config Files:** `application.yml`, `hazelcast.yml`

**Features:**
- TCP-IP member discovery
- Connects to external Hazelcast (docker-compose)
- `ddl-auto: update` - Auto-update schema
- Verbose logging (DEBUG)
- All actuator endpoints exposed

**Usage:**
```bash
# No profile needed - this is default
mvn spring-boot:run

# Or explicitly
mvn spring-boot:run -Dspring.profiles.active=dev
```

### k8s (Kubernetes)

**Config Files:** `hazelcast-k8s.yml`

**Features:**
- Kubernetes pod discovery
- Service-based member discovery
- Requires RBAC permissions
- Private IP networking

**Usage:**
```bash
# Via Helm (automatically sets k8s profile)
helm install spring-playground ./helm/spring-playground

# With environment override
helm install spring-playground ./helm/spring-playground \
  --set application.environment=staging
```

**Required Environment Variables:**
```yaml
SPRING_PROFILES_ACTIVE: k8s,prod
HAZELCAST_KUBERNETES_NAMESPACE: default
HAZELCAST_KUBERNETES_SERVICE_NAME: spring-playground
HAZELCAST_KUBERNETES_SERVICE_LABEL: spring-playground
```

### ecs (AWS ECS)

**Config Files:** `hazelcast-ecs.yml`, `application-ecs.yml`

**Features:**
- AWS EC2/ECS instance discovery
- IAM role-based authentication
- Tag-based filtering
- Works with Fargate and EC2

**Usage:**
```bash
# Set in ECS Task Definition
SPRING_PROFILES_ACTIVE=ecs,prod
AWS_REGION=us-east-1
HAZELCAST_TAG_KEY=hazelcast-cluster
HAZELCAST_TAG_VALUE=spring-playground
```

## Environment Profiles

### dev (Development)

**Config File:** `application-dev.yml`

**Characteristics:**
```yaml
JPA:
  ddl-auto: create-drop  # Recreate schema on restart
  show-sql: true         # Show all SQL queries

Logging:
  level: DEBUG           # Verbose logging
  show-parameters: true  # Show SQL parameters

Actuator:
  expose: "*"           # All endpoints exposed

Error:
  include-stacktrace: always
  include-message: always
```

**Best For:**
- Local development
- Testing schema changes
- Debugging issues
- Rapid iteration

**Not Suitable For:**
- Shared environments
- Production
- Performance testing

### staging (Staging/Pre-production)

**Config File:** `application-staging.yml`

**Characteristics:**
```yaml
JPA:
  ddl-auto: validate     # Only validate schema
  show-sql: false

Logging:
  level: INFO            # Standard logging

Actuator:
  expose: health,metrics # Limited endpoints
  show-details: when-authorized

Error:
  include-stacktrace: on_param  # Only with ?trace=true
```

**Best For:**
- Integration testing
- UAT (User Acceptance Testing)
- Performance testing
- Production-like environment

**Features:**
- Auto-scaling enabled
- Moderate resource allocation
- Production-like configuration
- Some debugging capabilities

### prod (Production)

**Config File:** `application-prod.yml`

**Characteristics:**
```yaml
JPA:
  ddl-auto: validate     # Never modify schema
  show-sql: false
  batch-processing: enabled

Logging:
  level: WARN            # Minimal logging
  performance-optimized: true

Actuator:
  expose: health,prometheus  # Minimal endpoints
  show-details: never        # Never expose internals

Error:
  include-stacktrace: never  # Security hardened
  include-message: never
```

**Best For:**
- Production deployments
- Customer-facing applications
- Performance-critical workloads

**Features:**
- Security hardened
- Performance optimized
- High availability (3+ replicas)
- Auto-scaling enabled
- Pod disruption budget
- Multi-zone deployment

## Usage Examples

### Local Development

```bash
# Default profile (dev)
mvn spring-boot:run

# Explicit dev profile
mvn spring-boot:run -Dspring.profiles.active=dev

# With docker-compose
docker-compose up -d
mvn spring-boot:run
```

### Kubernetes Development

```bash
# Deploy to dev environment
helm install spring-playground ./helm/spring-playground \
  -f helm/spring-playground/values-dev.yaml

# Result: SPRING_PROFILES_ACTIVE=k8s,dev
```

### Kubernetes Staging

```bash
# Deploy to staging environment
helm install spring-playground ./helm/spring-playground \
  -f helm/spring-playground/values-staging.yaml

# Result: SPRING_PROFILES_ACTIVE=k8s,staging
```

### Kubernetes Production

```bash
# Deploy to production environment
helm install spring-playground ./helm/spring-playground \
  -f helm/spring-playground/values-prod.yaml

# Result: SPRING_PROFILES_ACTIVE=k8s,prod
```

### AWS ECS Production

```bash
# Set in ECS Task Definition
Environment:
  - Name: SPRING_PROFILES_ACTIVE
    Value: ecs,prod
  - Name: AWS_REGION
    Value: us-east-1

# Result: Uses hazelcast-ecs.yml + application-ecs.yml + application-prod.yml
```

## Configuration Precedence

Spring Boot loads configurations in this order (later overrides earlier):

1. `application.yml` (default)
2. Platform profile (e.g., `hazelcast-k8s.yml`)
3. Environment profile (e.g., `application-prod.yml`)
4. Environment variables
5. Command-line arguments

**Example:** `SPRING_PROFILES_ACTIVE=k8s,prod`

```
application.yml              # Base config
  ↓ overridden by
hazelcast-k8s.yml           # Kubernetes Hazelcast config
  ↓ overridden by
application-prod.yml        # Production config
  ↓ overridden by
Environment Variables       # Runtime config
```

## Profile Selection Guide

| Environment | Platform | Profiles | Use Case |
|------------|----------|----------|----------|
| Local Dev | Docker Compose | `dev` or none | Development on laptop |
| Dev K8s | Kubernetes | `k8s,dev` | Shared dev cluster |
| Staging K8s | Kubernetes | `k8s,staging` | Pre-production testing |
| Prod K8s | Kubernetes | `k8s,prod` | Production Kubernetes |
| Prod ECS | AWS ECS | `ecs,prod` | Production AWS ECS |

## Creating Custom Profiles

To add a new profile:

1. **Create profile file:**
   ```bash
   src/main/resources/application-{profile}.yml
   ```

2. **Add to Helm values:**
   ```yaml
   # values-{profile}.yaml
   application:
     environment: {profile}
   ```

3. **Update documentation**

**Example - QA Profile:**

```yaml
# application-qa.yml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: true  # QA needs SQL visibility
logging:
  level:
    com.khoa.spring.playground: DEBUG  # QA needs debug logs
```

## Environment Variables

### Platform Variables

**Kubernetes:**
```bash
HAZELCAST_KUBERNETES_NAMESPACE=default
HAZELCAST_KUBERNETES_SERVICE_NAME=spring-playground
HAZELCAST_KUBERNETES_SERVICE_LABEL=spring-playground
```

**AWS ECS:**
```bash
AWS_REGION=us-east-1
HAZELCAST_TAG_KEY=hazelcast-cluster
HAZELCAST_TAG_VALUE=spring-playground
HAZELCAST_SECURITY_GROUP=sg-xxx
```

### Common Variables (All Environments)

```bash
SPRING_PROFILES_ACTIVE=k8s,prod
SPRING_DATASOURCE_URL=jdbc:postgresql://...
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=***
SPRING_CACHE_TYPE=hazelcast
SERVER_PORT=8080
```

## Troubleshooting

### Issue: Wrong profile loaded

**Check active profiles:**
```bash
curl http://localhost:8080/actuator/env | jq '.activeProfiles'
```

**Check logs:**
```bash
# Look for: "Active Spring profiles: [k8s, prod]"
kubectl logs -l app=spring-playground | grep "Active Spring profiles"
```

### Issue: Hazelcast not clustering

**Verify profile loads correct Hazelcast config:**
```bash
# Should see: "Loading Hazelcast configuration from: hazelcast-k8s.yml"
kubectl logs -l app=spring-playground | grep "Loading Hazelcast"
```

### Issue: Wrong configuration applied

**Check configuration sources:**
```bash
curl http://localhost:8080/actuator/configprops
```

## Best Practices

1. **Always specify environment profile** in production
   - ✅ `k8s,prod`
   - ❌ `k8s` (falls back to default)

2. **Use values files for Helm deployments**
   - ✅ `helm install -f values-prod.yaml`
   - ❌ Manual `--set` overrides

3. **Never use dev profile in production**
   - Exposes sensitive information
   - Performance overhead
   - Security risks

4. **Validate schema separately**
   - Use Flyway/Liquibase for migrations
   - Keep `ddl-auto: validate` in staging/prod

5. **Test profile combinations**
   - Verify `k8s,staging` works before `k8s,prod`
   - Test locally with combined profiles

## References

- [Spring Boot Profiles](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.profiles)
- [Kubernetes Deployment Guide](./KUBERNETES.md)
- [AWS ECS Deployment Guide](./ECS_DEPLOYMENT.md)
- [Hazelcast Configuration](../src/main/resources/hazelcast*.yml)
