# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Spring Boot 3.5.7 playground application using PostgreSQL, Hazelcast distributed caching, and async processing. The application demonstrates RESTful API design with caching strategies, async user deletion workflows, and observability integration (Prometheus, Grafana, Zipkin).

**Tech Stack:** Java 21, Spring Boot 3.5.7, Spring Data JPA, PostgreSQL 16, Hazelcast 5.4.0, Maven

## Build & Run Commands

### Prerequisites
Start Docker services before running the application:
```bash
docker-compose up -d
```

This starts:
- PostgreSQL on `localhost:5432`
- Hazelcast on `localhost:5701`
- SonarQube on `localhost:9000`
- Grafana on `localhost:3000`
- Grafana Mimir on `localhost:9009`
- Grafana Alloy on `localhost:12345`

### Build & Run
```bash
# Run application
mvn spring-boot:run

# Or build and run JAR
mvn clean package
java -jar target/playground-0.0.1-SNAPSHOT.jar
```

Application runs at `http://localhost:8080`

### Testing
```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=UserControllerTest

# Run specific test method
mvn test -Dtest=UserControllerTest#testGetUserById

# Run with coverage report
mvn clean verify
# Coverage report at: target/site/jacoco/index.html
```

### Code Quality
```bash
# Format code
mvn spring-javaformat:apply

# Run SonarQube analysis (requires SonarQube running and SONAR_TOKEN set)
mvn clean verify sonar:sonar
```

## Architecture & Design Patterns

### Package Structure
- `entity/` - JPA entities with `BaseEntity` providing common fields (`id`, `createdAt`, `updatedAt`)
- `repository/` - Spring Data JPA repositories
- `controller/` - REST controllers with `@RestController` and `/api/*` paths
- `service/` - Business logic layer (e.g., `UserDeletionService`)
- `config/` - Spring configuration classes
- `dto/` - Data transfer objects for API responses

### Database CASCADE vs JPA Cascade
**Important:** This project intentionally uses **database-level CASCADE DELETE** instead of JPA cascade operations for performance.

Entity relationships (User → Posts, Favorites, Resources) are marked with `@OneToMany(mappedBy = "user")` WITHOUT `cascade = CascadeType.ALL`. The CASCADE is handled at the database level via foreign key constraints.

**Why:** Database CASCADE is 10-100x faster for bulk deletes compared to JPA's approach of loading all entities into memory and deleting one-by-one.

When working with deletion logic:
- Use `userRepository.deleteById()` to trigger database CASCADE
- Do NOT add JPA cascade annotations unless explicitly required
- Clear caches manually after CASCADE deletes (see `UserDeletionService`)

### Async Processing Pattern
The `UserDeletionService` demonstrates async job processing with progress tracking:

1. **Job Creation** - `scheduleDelete()` creates a `DeleteJob` record with PENDING status
2. **Async Execution** - `@Async("deleteUserExecutor")` runs deletion in background thread pool
3. **Progress Tracking** - Job status updated to IN_PROGRESS → COMPLETED/FAILED
4. **Status API** - `GET /api/users/delete-jobs/{jobId}` for progress monitoring

Thread pool configuration in `AsyncConfig`:
- Core pool: 2 threads
- Max pool: 5 threads
- Queue capacity: 100 jobs
- Bean name: `deleteUserExecutor`

### Caching Strategy
Uses Hazelcast for distributed caching:

- `@Cacheable(value = "users", key = "#id")` - Cache read operations
- `@CacheEvict(value = "users", allEntries = true)` - Clear cache on writes
- Manual cache clearing in deletion service (see `UserDeletionService:133-144`)

Cache names: `users`, `posts`, `favorites`, `resources`

**Note:** After database CASCADE deletes, caches must be cleared manually since JPA doesn't know about deleted child entities.

### Configuration Management
Uses environment variables with defaults in `application.yml`:
- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` - Database connection
- `JPA_DDL_AUTO` - Hibernate schema mode (default: `update`)
- `CACHE_TYPE` - Cache provider (default: `hazelcast`)
- `SERVER_PORT` - Application port (default: `8080`)
- `.env` file support via `spring.config.import: optional:file:.env[.properties]`

### Test Configuration
Tests use H2 in-memory database (see `pom.xml` - H2 dependency with `test` scope).

**Async Testing:** When testing `@Async` methods, be aware that in unit tests they may execute synchronously. Use `CompletableFuture.get()` or `Thread.sleep()` to wait for completion if needed (see `UserDeletionServiceTest`).

## Common Development Patterns

### Adding New Entities
1. Extend `BaseEntity` for automatic `createdAt`/`updatedAt` timestamps
2. Use `@JsonManagedReference` on parent side of relationships
3. Use `@JsonBackReference` on child side to prevent circular serialization
4. Consider database CASCADE for delete operations (add to migration scripts)
5. Define cache eviction strategy in controllers

### Adding New REST Endpoints
1. Use `@RestController` with `@RequestMapping("/api/<resource>")`
2. Follow pattern: `ResponseEntity<Type>` return types
3. Add `@Cacheable` for read operations, `@CacheEvict` for mutations
4. Use constructor injection with `@RequiredArgsConstructor` (Lombok)
5. Return appropriate HTTP status codes (201 for creation, 204 for deletion, etc.)

### Testing Guidelines
- Use `@SpringBootTest` for integration tests
- Use `@WebMvcTest` for controller-only tests
- Mock repositories with `@MockBean`
- H2 is auto-configured for tests (no PostgreSQL needed)
- JaCoCo excludes Mockito-generated classes (see `pom.xml:145-149`)

## Observability

### Actuator Endpoints
Available at `/actuator/*`:
- `/actuator/health` - Health checks with details
- `/actuator/prometheus` - Prometheus metrics
- `/actuator/metrics` - Application metrics
- `/actuator/env` - Environment properties
- `/actuator/loggers` - Log level management

### Distributed Tracing
- Zipkin integration configured (endpoint: `http://localhost:9411/api/v2/spans`)
- Trace IDs included in log patterns: `%X{traceId:-},%X{spanId:-}`
- Sampling probability: 1.0 (100% - adjust for production)

### Metrics & Dashboards
- Prometheus scrapes `/actuator/prometheus`
- Grafana Mimir stores metrics (port 9009)
- Grafana dashboards at `http://localhost:3000` (admin/admin)
- Alloy telemetry collector at port 12345

## Maven Wrapper
Use `./mvnw` instead of `mvn` if Maven is not installed globally. The wrapper is checked into the repository.

## CI/CD
GitHub Actions workflows in `.github/workflows/`:
- `maven-test.yml` - Run tests on push/PR
- `maven-publish.yml` - Publish to GitHub Packages
- `helm-chart-publish.yml` - Package and publish Helm charts

## SonarQube Integration
Configuration in `sonar-project.properties`:
- Organization: `anhkhoa289`
- Project: `anhkhoa289_spring-playground`
- Coverage reports from JaCoCo at `target/site/jacoco/jacoco.xml`
