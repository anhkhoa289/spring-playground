# Spring Playground

Dự án Spring Boot 3 với Spring MVC, Spring Data JPA, PostgreSQL và Hazelcast.

## Công nghệ sử dụng

- **Spring Boot 3.2.0**
- **Java 17**
- **Spring MVC** - REST API
- **Spring Data JPA** - ORM
- **PostgreSQL 16** - Database
- **Hazelcast 5.4.0** - Distributed caching
- **Lombok** - Giảm boilerplate code
- **Maven** - Build tool

## Cấu trúc dự án

```
spring-playground/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/example/springplayground/
│   │   │       ├── config/          # Hazelcast configuration
│   │   │       ├── controller/      # REST Controllers
│   │   │       ├── entity/          # JPA Entities
│   │   │       ├── repository/      # Spring Data Repositories
│   │   │       └── SpringPlaygroundApplication.java
│   │   └── resources/
│   │       └── application.yml      # Application configuration
│   └── test/
├── docker-compose.yml               # Docker services
├── pom.xml                          # Maven dependencies
└── README.md
```

## Yêu cầu hệ thống

- Java 17 hoặc cao hơn
- Maven 3.6+
- Docker và Docker Compose

## Hướng dẫn chạy

### 1. Khởi động các services với Docker Compose

```bash
docker-compose up -d
```

Services sẽ được khởi động:
- PostgreSQL: `localhost:5432`
- Hazelcast: `localhost:5701`

### 2. Chạy ứng dụng Spring Boot

```bash
mvn spring-boot:run
```

Hoặc build và chạy:

```bash
mvn clean package
java -jar target/spring-playground-0.0.1-SNAPSHOT.jar
```

Ứng dụng sẽ chạy tại: `http://localhost:8080`

## API Endpoints

### User Management

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/users` | Lấy danh sách tất cả users |
| GET | `/api/users/{id}` | Lấy user theo ID (có cache) |
| GET | `/api/users/username/{username}` | Lấy user theo username (có cache) |
| POST | `/api/users` | Tạo user mới |
| PUT | `/api/users/{id}` | Cập nhật user |
| DELETE | `/api/users/{id}` | Xóa user |

### Ví dụ Request

**Tạo user mới:**
```bash
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{
    "username": "john_doe",
    "email": "john@example.com"
  }'
```

**Lấy user theo ID:**
```bash
curl http://localhost:8080/api/users/1
```

**Lấy tất cả users:**
```bash
curl http://localhost:8080/api/users
```

## Cấu hình

### Database (application.yml)

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/springdb
    username: postgres
    password: postgres
```

### Hazelcast Caching

- Cache được enable với annotation `@EnableCaching`
- Sử dụng Hazelcast làm cache provider
- Các method có annotation `@Cacheable` sẽ được cache tự động
- Cache được clear khi có update/delete với `@CacheEvict`

## Kiểm tra services

### PostgreSQL
```bash
docker exec -it spring-postgres psql -U postgres -d springdb
```

### Hazelcast Health Check
```bash
curl http://localhost:5701/hazelcast/health
```

## Dừng services

```bash
docker-compose down
```

Xóa cả volumes (dữ liệu sẽ mất):
```bash
docker-compose down -v
```

## Development

### Build project
```bash
mvn clean install
```

### Run tests
```bash
mvn test
```

### Format code
```bash
mvn spring-javaformat:apply
```

## License

MIT License