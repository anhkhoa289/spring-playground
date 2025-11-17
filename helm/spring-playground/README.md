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

### Option 1: Install from OCI Registry (Recommended)

The Helm chart is automatically published to GitHub Container Registry (GHCR) as an OCI artifact when a tag matching `v*.*.*-chart` is pushed.

```bash
# Add Bitnami repository (for PostgreSQL dependency)
helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo update

# Install the chart from OCI registry
helm install spring-playground oci://ghcr.io/anhkhoa289/spring-playground/chart --version 1.0.0

# Install with custom values
helm install spring-playground oci://ghcr.io/anhkhoa289/spring-playground/chart --version 1.0.0 -f custom-values.yaml

# Install in a specific namespace
helm install spring-playground oci://ghcr.io/anhkhoa289/spring-playground/chart --version 1.0.0 -n spring-playground --create-namespace
```

**Note:** If the repository is private, you need to login first:
```bash
echo $GITHUB_TOKEN | helm registry login ghcr.io -u USERNAME --password-stdin
```

### Option 2: Install from Local Source

```bash
# Add Bitnami repository (for PostgreSQL dependency)
helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo update

# Install with default values
helm install spring-playground ./helm/spring-playground

# Install with custom values
helm install spring-playground ./helm/spring-playground -f custom-values.yaml

# Install in a specific namespace
helm install spring-playground ./helm/spring-playground -n spring-playground --create-namespace
```

## Upgrading

From OCI registry:
```bash
helm upgrade spring-playground oci://ghcr.io/anhkhoa289/spring-playground/chart --version 1.1.0
```

From local source:
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

## Releasing New Chart Versions

To publish a new version of the Helm chart to the OCI registry:

1. Update the chart version in `Chart.yaml` if needed (optional, as CI/CD will update it)
2. Create and push a git tag with the format `v*.*.*-chart`:
   ```bash
   git tag v1.0.0-chart
   git push origin v1.0.0-chart
   ```
3. GitHub Actions will automatically:
   - Update the chart version in `Chart.yaml`
   - Package the Helm chart
   - Push it to `oci://ghcr.io/anhkhoa289/spring-playground/chart`

## Notes

- The default configuration includes Spring Boot Actuator health endpoints for liveness and readiness probes. Make sure Spring Boot Actuator is enabled in your application.
- PostgreSQL is deployed as a StatefulSet with persistent storage by default.
- Chart releases use tags with `-chart` suffix (e.g., `v1.0.0-chart`) to avoid conflicts with application releases
- Maven package releases use tags without suffix (e.g., `v1.0.0`)
- For production use, consider:
  - Changing default passwords
  - Enabling ingress with TLS
  - Adjusting resource limits based on your application needs
  - Using external database for better persistence
  - Enabling pod disruption budgets
  - Configuring network policies
