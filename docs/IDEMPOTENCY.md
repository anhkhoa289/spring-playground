# Idempotency Implementation với Hazelcast

## Tổng quan

Project này đã implement idempotency pattern sử dụng Hazelcast distributed cache để đảm bảo các API operations chỉ được thực thi một lần duy nhất cho mỗi idempotency key.

## Kiến trúc

### Components

1. **@Idempotent Annotation** (`annotation/Idempotent.java`)
   - Đánh dấu methods cần idempotency protection
   - Configurable TTL và request body validation

2. **IdempotencyAspect** (`aspect/IdempotencyAspect.java`)
   - AOP aspect intercepts @Idempotent methods
   - Kiểm tra X-Idempotency-Key header
   - Return cached response nếu key đã tồn tại

3. **IdempotencyService** (`service/IdempotencyService.java`)
   - Quản lý Hazelcast cache operations
   - Generate và validate request hash
   - Store/retrieve cached responses

4. **IdempotencyRequest DTO** (`dto/IdempotencyRequest.java`)
   - Lưu trữ thông tin request đã xử lý
   - Bao gồm response, status code, timestamp

5. **HazelcastConfig** (`config/HazelcastConfig.java`)
   - Cấu hình `idempotency-cache` với TTL 24 giờ
   - Max idle time: 1 giờ

## Cách hoạt động

```
Request với X-Idempotency-Key
    ↓
IdempotencyAspect intercept
    ↓
Kiểm tra key trong Hazelcast cache
    ↓
┌─────────────────┴─────────────────┐
│                                   │
Key exists                    Key không tồn tại
↓                                   ↓
Validate request hash          Execute method
↓                                   ↓
Return cached response         Lưu response vào cache
                                    ↓
                               Return response
```

## APIs được bảo vệ

### User APIs

#### POST /api/users
```bash
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -H "X-Idempotency-Key: user-create-001" \
  -d '{
    "username": "john_doe",
    "email": "john@example.com"
  }'
```

#### PUT /api/users/{id}
```bash
curl -X PUT http://localhost:8080/api/users/1 \
  -H "Content-Type: application/json" \
  -H "X-Idempotency-Key: user-update-001" \
  -d '{
    "username": "john_updated",
    "email": "john.new@example.com"
  }'
```

### Post APIs

#### POST /api/posts
```bash
curl -X POST http://localhost:8080/api/posts \
  -H "Content-Type: application/json" \
  -H "X-Idempotency-Key: post-create-001" \
  -d '{
    "title": "My First Post",
    "content": "This is the content",
    "userId": 1
  }'
```

#### PUT /api/posts/{id}
```bash
curl -X PUT http://localhost:8080/api/posts/1 \
  -H "Content-Type: application/json" \
  -H "X-Idempotency-Key: post-update-001" \
  -d '{
    "title": "Updated Post Title",
    "content": "Updated content",
    "userId": 1
  }'
```

## Tính năng

### 1. Automatic Caching
- Response tự động được cache với idempotency key
- TTL mặc định: 24 giờ
- Có thể customize TTL cho từng endpoint

### 2. Request Body Validation
- Mặc định bật: Cùng key với body khác nhau → 409 CONFLICT
- Có thể tắt validation nếu cần

### 3. Distributed Cache
- Sử dụng Hazelcast distributed cache
- Work với multiple instances
- Auto eviction sau TTL/idle time

### 4. Transparent to Clients
- Client chỉ cần gửi `X-Idempotency-Key` header
- Không cần thay đổi business logic

## Test Cases

### Test 1: Basic Idempotency
```bash
# Request 1 - Tạo user mới
curl -X POST http://localhost:8080/api/users \
  -H "X-Idempotency-Key: test-001" \
  -H "Content-Type: application/json" \
  -d '{"username": "test", "email": "test@example.com"}'
# Response: 201 Created, user được tạo

# Request 2 - Cùng idempotency key
curl -X POST http://localhost:8080/api/users \
  -H "X-Idempotency-Key: test-001" \
  -H "Content-Type: application/json" \
  -d '{"username": "test", "email": "test@example.com"}'
# Response: 201 Created, return cached response, KHÔNG tạo user mới
```

### Test 2: Request Hash Validation
```bash
# Request 1
curl -X POST http://localhost:8080/api/users \
  -H "X-Idempotency-Key: test-002" \
  -H "Content-Type: application/json" \
  -d '{"username": "alice", "email": "alice@example.com"}'
# Response: 201 Created

# Request 2 - Cùng key nhưng khác body
curl -X POST http://localhost:8080/api/users \
  -H "X-Idempotency-Key: test-002" \
  -H "Content-Type: application/json" \
  -d '{"username": "bob", "email": "bob@example.com"}'
# Response: 409 CONFLICT - Key already used with different body
```

### Test 3: Without Idempotency Key
```bash
# Request không có X-Idempotency-Key header
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{"username": "charlie", "email": "charlie@example.com"}'
# Response: 201 Created, không cache
# Mỗi request sẽ tạo user mới
```

## Custom Configuration

### Thay đổi TTL cho endpoint cụ thể

```java
@PostMapping("/special")
@Idempotent(ttl = 3600L) // 1 hour instead of 24 hours
public ResponseEntity<Entity> createSpecial(@RequestBody Entity entity) {
    // ...
}
```

### Tắt request body validation

```java
@PutMapping("/{id}")
@Idempotent(validateRequestBody = false) // Allow same key with different body
public ResponseEntity<Entity> update(@PathVariable Long id, @RequestBody Entity entity) {
    // ...
}
```

## Best Practices

### 1. Idempotency Key Generation

Client nên generate unique key cho mỗi operation:
```javascript
// UUID v4
const idempotencyKey = crypto.randomUUID();

// Hoặc combine với metadata
const idempotencyKey = `${userId}-${operationType}-${timestamp}`;
```

### 2. Retry Logic

```javascript
async function createUserWithRetry(userData) {
  const idempotencyKey = crypto.randomUUID();
  const maxRetries = 3;

  for (let attempt = 0; attempt < maxRetries; attempt++) {
    try {
      const response = await fetch('/api/users', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-Idempotency-Key': idempotencyKey
        },
        body: JSON.stringify(userData)
      });

      if (response.ok) {
        return await response.json();
      }

      if (response.status === 409) {
        // Conflict - different body with same key
        throw new Error('Idempotency key conflict');
      }

    } catch (error) {
      if (attempt === maxRetries - 1) throw error;
      await sleep(1000 * Math.pow(2, attempt)); // Exponential backoff
    }
  }
}
```

### 3. Key Management

- **Lưu trữ key**: Lưu idempotency key sau khi gửi request để có thể retry
- **Không reuse**: Mỗi operation mới cần key mới
- **Namespace**: Prefix key theo service/operation type
- **Expiration**: Client có thể sync TTL với server (24h)

## Monitoring

### Log Messages

IdempotencyService và IdempotencyAspect log các events:
- `INFO`: Request processed với idempotency key
- `INFO`: Cached response returned
- `WARN`: Request hash mismatch detected
- `ERROR`: Hash generation failed

### Hazelcast Metrics

Monitor Hazelcast cache:
```java
@Autowired
private IdempotencyService idempotencyService;

public int getCacheSize() {
    return idempotencyService.getCacheSize();
}
```

## Troubleshooting

### Issue 1: "Request hash mismatch"

**Nguyên nhân**: Cùng idempotency key nhưng request body khác

**Giải pháp**:
- Client sử dụng key mới cho mỗi operation
- Hoặc tắt validation: `@Idempotent(validateRequestBody = false)`

### Issue 2: Cache không clear

**Nguyên nhân**: TTL chưa expire

**Giải pháp**:
- Đợi 24h (hoặc 1h idle)
- Hoặc manual clear (dev/testing only):
```java
idempotencyService.remove(idempotencyKey);
```

### Issue 3: Hazelcast connection issues

**Nguyên nhân**: Hazelcast cluster không accessible

**Giải pháp**:
- Kiểm tra Hazelcast logs
- Verify network config trong `HazelcastConfig.java`
- Ensure port 5701 không bị block

## Dependencies

```xml
<!-- Spring AOP -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>

<!-- Hazelcast -->
<dependency>
    <groupId>com.hazelcast</groupId>
    <artifactId>hazelcast</artifactId>
    <version>5.4.0</version>
</dependency>

<dependency>
    <groupId>com.hazelcast</groupId>
    <artifactId>hazelcast-spring</artifactId>
    <version>5.4.0</version>
</dependency>
```

## Tổng kết

Implementation này cung cấp:
- ✅ Distributed idempotency với Hazelcast
- ✅ Request body validation
- ✅ Configurable TTL
- ✅ Transparent integration với AOP
- ✅ Support cho multi-instance deployment
- ✅ Comprehensive logging
- ✅ Easy to use với annotation

Chỉ cần thêm `@Idempotent` annotation và client gửi `X-Idempotency-Key` header!
