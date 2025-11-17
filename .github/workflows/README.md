# CI/CD Workflows

This directory contains GitHub Actions workflows for automated builds and deployments.

## Workflows

### 1. Maven Package Deployment (`maven-publish.yml`)

**Trigger:** Git tags matching `v*.*.*` (but NOT `v*.*.*-chart`)

**Purpose:** Builds and publishes the Spring Boot application to GitHub Packages.

**Process:**
1. Extracts version from tag (e.g., `v1.0.0` → `1.0.0`)
2. Updates `pom.xml` with the version
3. Builds the application with Maven
4. Publishes to GitHub Packages Maven registry

**Usage:**
```bash
git tag v1.0.0
git push origin v1.0.0
```

### 2. Helm Chart OCI Deployment (`helm-chart-publish.yml`)

**Trigger:** Git tags matching `v*.*.*-chart`

**Purpose:** Packages and publishes the Helm chart to GitHub Container Registry as an OCI artifact.

**Process:**
1. Extracts version from tag (e.g., `v1.0.0-chart` → `1.0.0`)
2. Updates `Chart.yaml` with the version
3. Packages the Helm chart
4. Pushes to `ghcr.io/anhkhoa289/spring-playground`

**Usage:**
```bash
git tag v1.0.0-chart
git push origin v1.0.0-chart
```

## Tag Naming Convention

- **Application releases:** `v*.*.*` (e.g., `v1.0.0`, `v1.2.3`)
  - Triggers Maven package deployment
  - Does NOT trigger Helm chart deployment

- **Chart releases:** `v*.*.*-chart` (e.g., `v1.0.0-chart`, `v1.2.3-chart`)
  - Triggers Helm chart deployment
  - Does NOT trigger Maven package deployment

## Examples

### Release both application and chart

```bash
# Release application version 1.0.0
git tag v1.0.0
git push origin v1.0.0

# Release Helm chart version 1.0.0
git tag v1.0.0-chart
git push origin v1.0.0-chart
```

### Release only the chart

```bash
# Update only the Helm chart configuration
git tag v1.0.1-chart
git push origin v1.0.1-chart
```

### Release only the application

```bash
# Update only the application code
git tag v1.0.1
git push origin v1.0.1
```

## Permissions

Both workflows require:
- `contents: read` - To checkout the repository
- `packages: write` - To publish to GitHub Packages/Container Registry

These permissions are automatically provided by the `GITHUB_TOKEN` secret.

## Accessing Published Artifacts

### Maven Package
```xml
<dependency>
    <groupId>com.khoa.spring</groupId>
    <artifactId>playground</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Helm Chart
```bash
helm install spring-playground oci://ghcr.io/anhkhoa289/spring-playground --version 1.0.0
```
