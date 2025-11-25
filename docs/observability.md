# Spring Boot Observability với Alloy và Grafana

Hệ thống monitoring và observability cho Spring Boot application sử dụng:
- **Spring Boot Actuator**: Expose metrics và health endpoints
- **Micrometer**: Metrics collection và export
- **Grafana Alloy**: Telemetry collector (thay thế cho Prometheus Agent)
- **Grafana Mimir**: Time-series database để lưu trữ metrics
- **Grafana**: Visualization và dashboards

## Kiến trúc

```
Spring Boot App (port 8080)
    ↓ (expose metrics at /actuator/prometheus)
Grafana Alloy (port 12345)
    ↓ (scrape metrics every 15s)
Grafana Mimir (port 9009)
    ↓ (store time-series data)
Grafana (port 3000)
    ↓ (visualize metrics)
```

## Cấu trúc Dự án

```
spring-playground/
├── docker-compose.yml              # Docker Compose chính (bao gồm cả observability)
├── config/
│   ├── alloy/
│   │   └── config.alloy           # Cấu hình Alloy collector
│   ├── grafana/
│   │   ├── dashboards/
│   │   │   └── spring-boot-dashboard.json
│   │   └── provisioning/
│   │       ├── datasources/
│   │       └── dashboards/
│   └── mimir/
│       └── mimir.yaml             # Cấu hình Mimir storage
└── docs/
    └── observability.md           # Tài liệu này
```

## Cài đặt và Chạy

### 1. Build Spring Boot Application

```bash
mvn clean install
```

### 2. Chạy Spring Boot Application

```bash
mvn spring-boot:run
```

Hoặc:

```bash
java -jar target/playground-0.0.1-SNAPSHOT.jar
```

### 3. Khởi động Observability Stack

```bash
docker-compose up -d
```

Hoặc chỉ khởi động observability services:

```bash
docker-compose up -d alloy mimir grafana
```

### 4. Kiểm tra Services

- **Spring Boot Actuator**: http://localhost:8080/actuator
- **Prometheus Metrics**: http://localhost:8080/actuator/prometheus
- **Health Check**: http://localhost:8080/actuator/health
- **Grafana Alloy UI**: http://localhost:12345
- **Grafana**: http://localhost:3000 (admin/admin)

## Cấu hình Chi tiết

### Spring Boot Actuator Endpoints

Application expose các endpoints sau:
- `/actuator/health` - Health check với chi tiết
- `/actuator/prometheus` - Prometheus metrics
- `/actuator/metrics` - Metrics overview
- `/actuator/env` - Environment properties
- `/actuator/loggers` - Log levels configuration

### Grafana Alloy

Alloy scrape metrics từ Spring Boot application mỗi 15 giây và forward đến Mimir.

Cấu hình tại: `config/alloy/config.alloy`

### Grafana Mimir

Mimir lưu trữ metrics trong chế độ single-process mode (phù hợp cho development).

Cấu hình tại: `config/mimir/mimir.yaml`

### Grafana Dashboard

Dashboard mặc định hiển thị:
1. **HTTP Request Rate** - Số lượng request/giây
2. **HTTP Request Duration (p95)** - Response time percentile 95
3. **JVM Heap Memory** - Memory usage của JVM heap
4. **JVM Non-Heap Memory** - Memory usage của JVM non-heap
5. **CPU Usage** - System và Process CPU usage
6. **JVM Threads** - Số lượng threads đang chạy
7. **Database Connection Pool** - HikariCP connection statistics
8. **JVM GC Pause Duration** - Garbage collection pause time
9. **Cache Statistics** - Hazelcast cache hits/misses

## Metrics Quan trọng

### JVM Metrics
- `jvm_memory_used_bytes` - Memory usage
- `jvm_threads_live_threads` - Active threads
- `jvm_gc_pause_seconds` - GC pause duration

### HTTP Metrics
- `http_server_requests_seconds_count` - Request count
- `http_server_requests_seconds_sum` - Total request duration
- `http_server_requests_seconds_bucket` - Request duration histogram

### Database Metrics
- `hikaricp_connections_active` - Active DB connections
- `hikaricp_connections_idle` - Idle DB connections
- `hikaricp_connections` - Total connections

### Cache Metrics (Hazelcast)
- `cache_gets_total` - Cache hits/misses
- `cache_size` - Cache size

### System Metrics
- `system_cpu_usage` - System CPU usage
- `process_cpu_usage` - Process CPU usage

## Custom Metrics

Để thêm custom metrics, sử dụng Micrometer:

```java
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;

@Component
public class MyService {
    private final Counter myCounter;

    public MyService(MeterRegistry registry) {
        this.myCounter = Counter.builder("my.custom.metric")
            .description("My custom metric")
            .tag("type", "example")
            .register(registry);
    }

    public void doSomething() {
        myCounter.increment();
        // your logic here
    }
}
```

## Tracing với Zipkin (Tùy chọn)

Application đã được cấu hình để support distributed tracing với Zipkin.

Để enable tracing, chạy Zipkin:

```bash
docker run -d -p 9411:9411 openzipkin/zipkin
```

Zipkin UI: http://localhost:9411

## Troubleshooting

### Alloy không scrape được metrics

1. Kiểm tra Spring Boot app đang chạy:
```bash
curl http://localhost:8080/actuator/prometheus
```

2. Kiểm tra Alloy logs:
```bash
docker-compose logs alloy
```

3. Kiểm tra Alloy UI: http://localhost:12345

### Grafana không hiển thị data

1. Kiểm tra Mimir đang chạy:
```bash
docker-compose ps mimir
```

2. Kiểm tra Mimir có nhận được metrics:
```bash
curl http://localhost:9009/prometheus/api/v1/query?query=up
```

3. Kiểm tra datasource trong Grafana:
   - Vào Configuration → Data Sources
   - Test connection đến Mimir

### Xóa tất cả data và restart

```bash
docker-compose down -v
docker-compose up -d alloy mimir grafana
```

## Dừng Services

Dừng tất cả services:
```bash
docker-compose down
```

Dừng chỉ observability services:
```bash
docker-compose stop alloy mimir grafana
```

Để xóa volumes (data sẽ bị mất):
```bash
docker-compose down -v
```

## Tham khảo

- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [Micrometer](https://micrometer.io/docs)
- [Grafana Alloy](https://grafana.com/docs/alloy/latest/)
- [Grafana Mimir](https://grafana.com/docs/mimir/latest/)
- [Grafana](https://grafana.com/docs/grafana/latest/)
