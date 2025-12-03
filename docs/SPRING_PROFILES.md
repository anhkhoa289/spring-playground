# Spring Profiles Guide

This document explains the Spring Profile configuration for the Spring Playground application.

## Overview

The application uses a **simple profile system** based on deployment platform:

- **default** - Local development (no profile needed)
- **docker** - Docker Compose deployment
- **k8s** - Kubernetes deployment
- **ecs** - AWS ECS deployment

## Profile Structure

### Main Configuration

**All common configuration is in `application.yml`**. Profile-specific files only contain minimal overrides.

```
src/main/resources/
├── application.yml              # Main configuration (all common settings)
├── application-docker.yml       # Docker overrides (empty)
├── application-k8s.yml          # Kubernetes overrides (empty)
├── application-ecs.yml          # ECS overrides (debug logging for AWS)
├── hazelcast.yml                # Default Hazelcast (local)
├── hazelcast-docker.yml         # Docker Compose Hazelcast
├── hazelcast-k8s.yml            # Kubernetes Hazelcast
└── hazelcast-ecs.yml            # AWS ECS Hazelcast
```

## Profiles

### default (Local Development)

**Files:** `application.yml`, `hazelcast.yml`

**Usage:**
```bash
# No profile needed - this is default
mvn spring-boot:run

# Or run directly
java -jar target/playground-0.0.1-SNAPSHOT.jar
```

**Hazelcast:** Embedded instance (no clustering)

**Best for:**
- Local development
- Testing features
- Quick prototyping

---

### docker (Docker Compose)

**Files:** `application.yml`, `application-docker.yml`, `hazelcast-docker.yml`

**Usage:**
```bash
# Set in docker-compose.yml
environment:
  SPRING_PROFILES_ACTIVE: docker

# Or via command line
docker-compose up -d
docker exec -it app java -jar app.jar --spring.profiles.active=docker
```

**Hazelcast:** TCP-IP discovery via service name `app`

**Best for:**
- Multi-container local testing
- Integration testing with PostgreSQL
- Simulating production environment locally

---

### k8s (Kubernetes)

**Files:** `application.yml`, `application-k8s.yml`, `hazelcast-k8s.yml`

**Usage:**
```bash
# Automatically set via Helm
helm install spring-playground ./helm/spring-playground

# Or manually
kubectl set env deployment/spring-playground SPRING_PROFILES_ACTIVE=k8s
```

**Hazelcast:** Kubernetes pod discovery via service name

**Required Environment Variables:**
```yaml
SPRING_PROFILES_ACTIVE: k8s
HAZELCAST_KUBERNETES_NAMESPACE: default
HAZELCAST_KUBERNETES_SERVICE_NAME: spring-playground
HAZELCAST_KUBERNETES_SERVICE_LABEL: spring-playground
```

**Best for:**
- Production Kubernetes deployments
- Auto-scaling environments
- Multi-replica deployments

---

### ecs (AWS ECS)

**Files:** `application.yml`, `application-ecs.yml`, `hazelcast-ecs.yml`

**Usage:**
```bash
# Set in ECS Task Definition
Environment:
  - Name: SPRING_PROFILES_ACTIVE
    Value: ecs
  - Name: AWS_REGION
    Value: us-east-1
```

**Hazelcast:** AWS EC2/ECS discovery via tags

**Required Environment Variables:**
```yaml
SPRING_PROFILES_ACTIVE: ecs
AWS_REGION: us-east-1
HAZELCAST_TAG_KEY: hazelcast-cluster
HAZELCAST_TAG_VALUE: spring-playground
```

**Best for:**
- AWS ECS/Fargate deployments
- AWS-native infrastructure
- IAM role-based authentication

---

## Hazelcast Configuration by Profile

| Profile | Discovery Method | Configuration File |
|---------|-----------------|-------------------|
| default | Embedded (no clustering) | `hazelcast.yml` |
| docker  | TCP-IP (service name: `app`) | `hazelcast-docker.yml` |
| k8s     | Kubernetes pod discovery | `hazelcast-k8s.yml` |
| ecs     | AWS EC2/ECS tag discovery | `hazelcast-ecs.yml` |

## Configuration Precedence

Spring Boot loads configurations in this order (later overrides earlier):

```
1. application.yml (base configuration)
   ↓
2. hazelcast-{profile}.yml (Hazelcast for profile)
   ↓
3. application-{profile}.yml (profile overrides)
   ↓
4. Environment Variables (runtime config)
   ↓
5. Command-line arguments (--spring.profiles.active=xxx)
```

## Examples

### Local Development

```bash
# Default - no profile
mvn spring-boot:run

# Logs show:
# Active Spring profiles: []
# Loading Hazelcast configuration from: hazelcast.yml
```

### Docker Compose

```bash
# docker-compose.yml
services:
  app:
    environment:
      SPRING_PROFILES_ACTIVE: docker

# Start
docker-compose up -d

# Logs show:
# Active Spring profiles: [docker]
# Loading Hazelcast configuration from: hazelcast-docker.yml
```

### Kubernetes

```bash
# Deploy
helm install spring-playground ./helm/spring-playground

# Check logs
kubectl logs -l app.kubernetes.io/name=spring-playground | grep profile

# Output:
# Active Spring profiles: [k8s]
# Loading Hazelcast configuration from: hazelcast-k8s.yml
# Members {size:2, ver:2} [...]
```

### AWS ECS

```bash
# Task Definition
{
  "environment": [
    {"name": "SPRING_PROFILES_ACTIVE", "value": "ecs"},
    {"name": "AWS_REGION", "value": "us-east-1"}
  ]
}

# Logs show:
# Active Spring profiles: [ecs]
# Loading Hazelcast configuration from: hazelcast-ecs.yml
```

## Troubleshooting

### Check Active Profile

```bash
# Via actuator
curl http://localhost:8080/actuator/env | jq '.activeProfiles'

# Via logs
grep "Active Spring profiles" application.log
```

### Check Hazelcast Configuration

```bash
# Via logs
grep "Loading Hazelcast configuration" application.log

# Expected outputs:
# - Local: "Loading Hazelcast configuration from: hazelcast.yml"
# - Docker: "Loading Hazelcast configuration from: hazelcast-docker.yml"
# - K8s: "Loading Hazelcast configuration from: hazelcast-k8s.yml"
# - ECS: "Loading Hazelcast configuration from: hazelcast-ecs.yml"
```

### Verify Clustering

```bash
# Check Hazelcast members
curl http://localhost:8080/actuator/hazelcast

# Or via logs
grep "Members {size:" application.log

# Expected:
# Members {size:2, ver:2} [
#   Member [10.0.1.100]:5701 - this
#   Member [10.0.1.101]:5701
# ]
```

## Best Practices

1. **Keep profile-specific files minimal**
   - Put common config in `application.yml`
   - Only override what's different in profile files

2. **Use explicit profiles in non-local environments**
   - ✅ `SPRING_PROFILES_ACTIVE=k8s`
   - ❌ Relying on defaults in production

3. **Test profile locally before deployment**
   ```bash
   mvn spring-boot:run -Dspring.profiles.active=docker
   ```

4. **Use environment variables for sensitive data**
   - Database passwords
   - AWS credentials
   - API keys

5. **Document profile requirements**
   - Required environment variables
   - Infrastructure dependencies
   - Permissions needed (IAM, RBAC)

## Summary

| Profile | Use Case | Hazelcast | Command |
|---------|----------|-----------|---------|
| default | Local dev | Embedded | `mvn spring-boot:run` |
| docker | Docker Compose | TCP-IP | `SPRING_PROFILES_ACTIVE=docker` |
| k8s | Kubernetes | Pod discovery | `helm install ...` |
| ecs | AWS ECS | AWS discovery | Set in Task Definition |

## References

- [Spring Boot Profiles](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.profiles)
- [Hazelcast Configuration](../src/main/resources/hazelcast*.yml)
- [Kubernetes Deployment](./KUBERNETES.md)
- [AWS ECS Deployment](./ECS_DEPLOYMENT.md)
