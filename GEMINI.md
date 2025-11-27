# Project Overview

This is a Spring Boot project that serves as a playground for various Spring technologies. It provides a RESTful API for user and post management, leveraging Spring MVC, Spring Data JPA, and a PostgreSQL database. The project also includes Hazelcast for distributed caching, and a comprehensive observability stack with Prometheus, Grafana, and Zipkin.

## Building and Running

### Prerequisites

*   Java 21 or higher
*   Maven 3.6+
*   Docker and Docker Compose

### Running the Application

1.  **Start services:**
    ```bash
    docker-compose up -d
    ```
    This will start PostgreSQL and Hazelcast.

2.  **Run the Spring Boot application:**
    ```bash
    mvn spring-boot:run
    ```

The application will be available at `http://localhost:8080`.

### Running Tests

To run the test suite, use the following command:

```bash
mvn test
```

## Development Conventions

*   **Code Formatting:** The project uses `spring-javaformat` for code formatting. To format the code, run:
    ```bash
    mvn spring-javaformat:apply
    ```
*   **API:** The project follows RESTful API design principles.
*   **Caching:** Caching is implemented using Hazelcast. The `@Cacheable` and `@CacheEvict` annotations are used to cache and evict data from the cache.
*   **Observability:** The project is configured with a comprehensive observability stack, including:
    *   **Metrics:** Prometheus is used for collecting metrics.
    *   **Tracing:** Zipkin is used for distributed tracing.
    *   **Dashboards:** Grafana dashboards are available for visualizing metrics.
