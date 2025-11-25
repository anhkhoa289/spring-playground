# Spring Boot Monitoring Stack

Full observability stack cho Spring Boot application với Grafana, Prometheus, Loki, và Tempo.

## 🏗️ Kiến trúc

```
Spring Boot App
    ├── Metrics (Micrometer) → Prometheus → Grafana
    ├── Logs (Logback) → Loki → Grafana
    └── Traces (Micrometer Tracing) → Tempo → Grafana
```

## 📦 Components

### 1. **Prometheus** (Port 9090)
- Thu thập metrics từ Spring Boot Actuator
- Scrape interval: 5 giây
- Endpoint: `/actuator/prometheus`

### 2. **Grafana** (Port 3000)
- Dashboard visualization
- Login: `admin` / `admin`
- Tự động kết nối với Prometheus, Loki, và Tempo

### 3. **Loki** (Port 3100)
- Log aggregation system
- Retention: 7 ngày
- Thu thập logs từ Logback appender

### 4. **Tempo** (Port 3200, 9411)
- Distributed tracing backend
- Hỗ trợ Zipkin protocol (port 9411)
- Thu thập traces từ Micrometer Tracing

## 🚀 Cách sử dụng

### 1. Khởi động monitoring stack

```bash
docker-compose up -d prometheus loki tempo grafana
```

### 2. Khởi động Spring Boot application

```bash
mvn clean install
mvn spring-boot:run
```

### 3. Truy cập các services

- **Grafana**: http://localhost:3000 (admin/admin)
- **Prometheus**: http://localhost:9090
- **Loki**: http://localhost:3100
- **Tempo**: http://localhost:3200

### 4. Kiểm tra Spring Boot Actuator endpoints

```bash
# Health check
curl http://localhost:8080/actuator/health

# Prometheus metrics
curl http://localhost:8080/actuator/prometheus

# Tất cả endpoints
curl http://localhost:8080/actuator
```

## 📊 Grafana Dashboards

### Import recommended dashboards:

1. **Spring Boot Dashboard**
   - Dashboard ID: `12900`
   - URL: https://grafana.com/grafana/dashboards/12900

2. **JVM Micrometer**
   - Dashboard ID: `4701`
   - URL: https://grafana.com/grafana/dashboards/4701

3. **Loki Dashboard**
   - Dashboard ID: `13639`
   - URL: https://grafana.com/grafana/dashboards/13639

### Cách import:
1. Vào Grafana → Dashboards → Import
2. Nhập Dashboard ID
3. Chọn Prometheus datasource
4. Click "Import"

## 🔍 Observability Features

### Metrics (Prometheus + Micrometer)
- JVM metrics (heap, threads, GC)
- HTTP request metrics (rate, duration, errors)
- Database connection pool metrics
- Cache metrics (Hazelcast)
- Custom application metrics

### Logs (Loki + Logback)
- Structured logging
- Automatic labels: application, host, level
- Correlation với traces qua trace ID
- Full-text search trong Grafana

### Traces (Tempo + Micrometer Tracing)
- Distributed tracing
- Request flow visualization
- Performance bottleneck identification
- Link từ logs → traces → metrics

## ⚙️ Configuration Files

```
monitoring/
├── prometheus/
│   └── prometheus.yml          # Prometheus scrape config
├── loki/
│   └── loki-config.yml        # Loki storage config
├── tempo/
│   └── tempo-config.yml       # Tempo tracing config
└── grafana/
    └── provisioning/
        └── datasources/
            └── datasources.yml # Auto-configure datasources
```

## 📝 Spring Boot Configuration

### application.yml
```yaml
management:
  endpoints:
    web:
      exposure:
        include: "*"
  metrics:
    tags:
      application: ${spring.application.name}
  tracing:
    sampling:
      probability: 1.0
```

### logback-spring.xml
- Console appender cho local development
- Loki appender cho centralized logging
- Structured logging với trace ID correlation

## 🎯 Use Cases

### 1. Debug performance issues
- Check traces trong Tempo để xem request flow
- Xác định slow operations
- Correlate với metrics trong Prometheus

### 2. Monitor application health
- CPU, memory, thread usage
- Request rate và error rate
- Database connection pool health

### 3. Troubleshoot errors
- Search logs trong Loki
- Click vào trace ID để xem distributed trace
- Xem related metrics

## 🔧 Troubleshooting

### Spring Boot không connect được monitoring stack

1. Kiểm tra các services đã chạy:
```bash
docker-compose ps
```

2. Kiểm tra logs:
```bash
docker-compose logs -f prometheus
docker-compose logs -f loki
docker-compose logs -f tempo
```

3. Verify endpoints:
```bash
# Prometheus health
curl http://localhost:9090/-/healthy

# Loki health
curl http://localhost:3100/ready

# Tempo health
curl http://localhost:3200/ready
```

### Không thấy metrics trong Grafana

1. Kiểm tra Prometheus targets: http://localhost:9090/targets
2. Spring Boot app phải expose metrics: http://localhost:8080/actuator/prometheus
3. Verify Prometheus config có đúng target không

### Logs không xuất hiện trong Loki

1. Kiểm tra Loki logs: `docker-compose logs loki`
2. Verify logback-spring.xml có đúng Loki URL không
3. Test Loki endpoint:
```bash
curl http://localhost:3100/ready
```

## 📚 Resources

- [Spring Boot Actuator Docs](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [Micrometer Docs](https://micrometer.io/docs)
- [Grafana Docs](https://grafana.com/docs/)
- [Prometheus Docs](https://prometheus.io/docs/)
- [Loki Docs](https://grafana.com/docs/loki/latest/)
- [Tempo Docs](https://grafana.com/docs/tempo/latest/)
