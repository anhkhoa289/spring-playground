# Spring Playground Helm Chart

Helm chart for deploying the Spring Boot Playground application to Kubernetes.

## Prerequisites

- Kubernetes 1.19+
- Helm 3.0+
- PostgreSQL (provided as a dependency)

## Features

- Spring Boot application deployment
- PostgreSQL database (Bitnami chart)
- Hazelcast caching support
- Horizontal Pod Autoscaling (optional)
- Ingress support (optional)
- Configurable resources and replicas
- Health checks (liveness and readiness probes)

## Installation

### Add Bitnami repository (for PostgreSQL dependency)

```bash
helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo update
```

### Install the chart

```bash
# Install with default values
helm install spring-playground ./helm/spring-playground

# Install with custom values
helm install spring-playground ./helm/spring-playground -f custom-values.yaml

# Install in a specific namespace
helm install spring-playground ./helm/spring-playground -n spring-playground --create-namespace
```

## Upgrading

```bash
helm upgrade spring-playground ./helm/spring-playground
```

## Uninstalling

```bash
helm uninstall spring-playground
```

## Configuration

The following table lists the configurable parameters of the Spring Playground chart and their default values.

| Parameter | Description | Default |
|-----------|-------------|---------|
| `replicaCount` | Number of replicas | `1` |
| `image.repository` | Image repository | `spring-playground` |
| `image.tag` | Image tag | `0.0.1-SNAPSHOT` |
| `image.pullPolicy` | Image pull policy | `IfNotPresent` |
| `service.type` | Service type | `ClusterIP` |
| `service.port` | Service port | `8080` |
| `ingress.enabled` | Enable ingress | `false` |
| `resources.limits.cpu` | CPU limit | `1000m` |
| `resources.limits.memory` | Memory limit | `1Gi` |
| `resources.requests.cpu` | CPU request | `500m` |
| `resources.requests.memory` | Memory request | `512Mi` |
| `postgresql.enabled` | Enable PostgreSQL | `true` |
| `postgresql.auth.database` | PostgreSQL database name | `springdb` |
| `postgresql.auth.username` | PostgreSQL username | `postgres` |
| `postgresql.auth.password` | PostgreSQL password | `postgres` |
| `autoscaling.enabled` | Enable HPA | `false` |

## Examples

### Enable Ingress

```yaml
# custom-values.yaml
ingress:
  enabled: true
  className: nginx
  hosts:
    - host: spring-playground.example.com
      paths:
        - path: /
          pathType: Prefix
```

### Enable Autoscaling

```yaml
# custom-values.yaml
autoscaling:
  enabled: true
  minReplicas: 2
  maxReplicas: 10
  targetCPUUtilizationPercentage: 70
```

### Use External PostgreSQL

```yaml
# custom-values.yaml
postgresql:
  enabled: false

env:
  - name: SPRING_DATASOURCE_URL
    value: "jdbc:postgresql://external-postgres:5432/springdb"
  - name: SPRING_DATASOURCE_USERNAME
    value: "myuser"
  - name: SPRING_DATASOURCE_PASSWORD
    valueFrom:
      secretKeyRef:
        name: external-postgres-secret
        key: password
```

## Testing

Test the chart template rendering:

```bash
helm template spring-playground ./helm/spring-playground
```

Validate the chart:

```bash
helm lint ./helm/spring-playground
```

## Notes

- The default configuration includes Spring Boot Actuator health endpoints for liveness and readiness probes. Make sure Spring Boot Actuator is enabled in your application.
- PostgreSQL is deployed as a StatefulSet with persistent storage by default.
- For production use, consider:
  - Changing default passwords
  - Enabling ingress with TLS
  - Adjusting resource limits based on your application needs
  - Using external database for better persistence
  - Enabling pod disruption budgets
  - Configuring network policies
