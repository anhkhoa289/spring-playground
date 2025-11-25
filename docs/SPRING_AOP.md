# Spring AOP (Aspect-Oriented Programming)

## Tổng quan

**Aspect-Oriented Programming (AOP)** là một programming paradigm bổ sung cho Object-Oriented Programming (OOP), cho phép tách biệt **cross-cutting concerns** ra khỏi business logic chính.

## Cross-cutting Concerns là gì?

**Cross-cutting concerns** là các logic xuất hiện xuyên suốt nhiều phần của ứng dụng, không thuộc về business logic cụ thể:

- 🔐 Security & Authorization
- 📝 Logging & Auditing
- ⚡ Performance Monitoring
- 🔄 Transaction Management
- 🛡️ Error Handling
- 🔁 Retry Logic
- ✅ **Idempotency** (như trong project này)
- 📊 Metrics Collection

### Vấn đề khi không dùng AOP

```java
@PostMapping
public ResponseEntity<User> createUser(@RequestBody User user) {
    // Logging
    log.info("Creating user: {}", user.getUsername());

    // Security check
    if (!hasPermission()) {
        throw new AccessDeniedException();
    }

    // Idempotency check
    String key = request.getHeader("X-Idempotency-Key");
    if (idempotencyService.exists(key)) {
        return idempotencyService.getCachedResponse(key);
    }

    // Performance monitoring
    long startTime = System.currentTimeMillis();

    try {
        // 🎯 Business logic chỉ có 2 dòng này!
        User savedUser = userRepository.save(user);
        return ResponseEntity.ok(savedUser);
    } finally {
        // More monitoring
        long duration = System.currentTimeMillis() - startTime;
        metrics.record(duration);

        // More logging
        log.info("User created in {}ms", duration);
    }
}
```

**Vấn đề:**
- Business logic bị "chôn vùi" trong technical code
- Code bị duplicate ở nhiều methods
- Khó maintain và test
- Vi phạm Single Responsibility Principle

## Các khái niệm cốt lõi trong AOP

### 1. Aspect

**Aspect** là một module chứa cross-cutting logic.

```java
@Aspect          // ← Đánh dấu đây là một aspect
@Component       // ← Spring component để auto-detect
public class LoggingAspect {
    // Cross-cutting logic here
}
```

### 2. Join Point

**Join Point** là một điểm trong execution flow nơi aspect có thể chạy.

Các loại join points:
- Method execution (phổ biến nhất)
- Method call
- Object initialization
- Field access
- Exception handling

### 3. Pointcut

**Pointcut** là expression định nghĩa join points nào sẽ được apply advice.

```java
// Pointcut: Tất cả methods có @Idempotent annotation
@Around("@annotation(com.khoa.spring.playground.annotation.Idempotent)")

// Pointcut: Tất cả methods trong UserController
@Around("execution(* com.khoa.spring.playground.controller.UserController.*(..))")

// Pointcut: Tất cả methods bắt đầu với "create"
@Around("execution(* create*(..))")

// Pointcut: Tất cả public methods trong service package
@Around("execution(public * com.khoa.spring.playground.service.*.*(..))")
```

### 4. Advice

**Advice** là code được execute tại join point.

#### @Before - Execute trước method

```java
@Aspect
@Component
public class SecurityAspect {

    @Before("@annotation(org.springframework.web.bind.annotation.PostMapping)")
    public void checkSecurity(JoinPoint joinPoint) {
        log.info("Security check before executing: {}", joinPoint.getSignature().getName());
        // Validate permissions
        if (!SecurityContextHolder.getContext().getAuthentication().isAuthenticated()) {
            throw new AccessDeniedException("Not authenticated");
        }
    }
}
```

#### @After - Execute sau method (dù success hay exception)

```java
@Aspect
@Component
public class CleanupAspect {

    @After("execution(* com.khoa.spring.playground.service.*.*(..))")
    public void cleanup(JoinPoint joinPoint) {
        log.info("Cleanup after method: {}", joinPoint.getSignature().getName());
        // Release resources
    }
}
```

#### @AfterReturning - Execute sau khi method return thành công

```java
@Aspect
@Component
public class AuditAspect {

    @AfterReturning(
        pointcut = "@annotation(org.springframework.web.bind.annotation.PostMapping)",
        returning = "result"
    )
    public void audit(JoinPoint joinPoint, Object result) {
        log.info("Method {} returned: {}",
            joinPoint.getSignature().getName(), result);
        // Log to audit trail
    }
}
```

#### @AfterThrowing - Execute khi method throw exception

```java
@Aspect
@Component
public class ErrorHandlingAspect {

    @AfterThrowing(
        pointcut = "execution(* com.khoa.spring.playground.controller.*.*(..))",
        throwing = "ex"
    )
    public void handleError(JoinPoint joinPoint, Exception ex) {
        log.error("Method {} threw exception: {}",
            joinPoint.getSignature().getName(), ex.getMessage());
        // Send alert, record metrics
    }
}
```

#### @Around - Bao quanh method execution (mạnh nhất)

```java
@Aspect
@Component
public class PerformanceAspect {

    @Around("@annotation(org.springframework.web.bind.annotation.PostMapping)")
    public Object measurePerformance(ProceedingJoinPoint joinPoint) throws Throwable {
        // BEFORE: Execute trước method
        long startTime = System.currentTimeMillis();

        try {
            // Execute actual method
            Object result = joinPoint.proceed();

            // AFTER: Execute sau method thành công
            long duration = System.currentTimeMillis() - startTime;
            log.info("Method {} took {}ms",
                joinPoint.getSignature().getName(), duration);

            return result;
        } catch (Exception ex) {
            // EXCEPTION: Handle exception
            log.error("Method {} failed after {}ms",
                joinPoint.getSignature().getName(),
                System.currentTimeMillis() - startTime);
            throw ex;
        }
    }
}
```

### 5. ProceedingJoinPoint

Được sử dụng với `@Around` advice, cho phép:
- Kiểm soát việc execute method gốc
- Truy cập method arguments
- Thay đổi return value
- Ngăn chặn method execution

```java
@Around("@annotation(Cacheable)")
public Object handleCaching(ProceedingJoinPoint joinPoint) throws Throwable {
    String methodName = joinPoint.getSignature().getName();
    Object[] args = joinPoint.getArgs();

    // Tạo cache key
    String cacheKey = methodName + Arrays.toString(args);

    // Check cache
    if (cache.containsKey(cacheKey)) {
        return cache.get(cacheKey); // Skip method execution!
    }

    // Execute method
    Object result = joinPoint.proceed();

    // Store in cache
    cache.put(cacheKey, result);

    return result;
}
```

## Implementation trong Project: Idempotency

### 1. Custom Annotation

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {
    long ttl() default 86400L;                    // 24 hours
    boolean validateRequestBody() default true;    // Request validation
}
```

### 2. Aspect Implementation

File: `src/main/java/com/khoa/spring/playground/aspect/IdempotencyAspect.java`

```java
@Aspect
@Component
@Slf4j
public class IdempotencyAspect {

    private static final String IDEMPOTENCY_KEY_HEADER = "X-Idempotency-Key";

    private final IdempotencyService idempotencyService;

    @Around("@annotation(com.khoa.spring.playground.annotation.Idempotent)")
    public Object handleIdempotency(ProceedingJoinPoint joinPoint) throws Throwable {
        // 1. Get HTTP request context
        ServletRequestAttributes attributes =
            (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        HttpServletRequest request = attributes.getRequest();
        String idempotencyKey = request.getHeader(IDEMPOTENCY_KEY_HEADER);

        // 2. No key → proceed normally
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            return joinPoint.proceed();
        }

        // 3. Check if request already processed
        Optional<IdempotencyRequest> cachedRequest =
            idempotencyService.get(idempotencyKey);

        if (cachedRequest.isPresent()) {
            // 4. Return cached response
            IdempotencyRequest cached = cachedRequest.get();
            return ResponseEntity
                .status(cached.getStatusCode())
                .body(cached.getResponse());
        }

        // 5. Execute method
        Object result = joinPoint.proceed();

        // 6. Cache response
        if (result instanceof ResponseEntity) {
            ResponseEntity<?> responseEntity = (ResponseEntity<?>) result;
            idempotencyService.store(
                idempotencyKey,
                responseEntity.getBody(),
                responseEntity.getStatusCode().value(),
                generateHash(joinPoint.getArgs()),
                getTTL(joinPoint)
            );
        }

        return result;
    }
}
```

### 3. Usage

```java
@RestController
@RequestMapping("/api/users")
public class UserController {

    @PostMapping
    @Idempotent  // ← Chỉ cần thêm annotation này!
    public ResponseEntity<User> createUser(@RequestBody User user) {
        // Pure business logic - no idempotency code!
        User savedUser = userRepository.save(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedUser);
    }

    @PutMapping("/{id}")
    @Idempotent(ttl = 3600L)  // Custom TTL: 1 hour
    public ResponseEntity<User> updateUser(@PathVariable Long id, @RequestBody User user) {
        // Clean business logic
        return userRepository.findById(id)
            .map(existing -> {
                existing.setUsername(user.getUsername());
                return ResponseEntity.ok(userRepository.save(existing));
            })
            .orElse(ResponseEntity.notFound().build());
    }
}
```

## Execution Flow với AOP

### Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                      Client Request                          │
└───────────────────────┬─────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────────┐
│                  Spring AOP Proxy                            │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  1. Detect @Idempotent annotation                     │  │
│  │  2. Invoke IdempotencyAspect                          │  │
│  └───────────────────────────────────────────────────────┘  │
└───────────────────────┬─────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────────┐
│              IdempotencyAspect.handleIdempotency()           │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  3. Extract X-Idempotency-Key header                  │  │
│  │  4. Check Hazelcast cache                             │  │
│  │     ├─ If exists → Return cached response             │  │
│  │     └─ If not exists → Continue                       │  │
│  └───────────────────────────────────────────────────────┘  │
└───────────────────────┬─────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────────┐
│              joinPoint.proceed()                             │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  5. Execute actual controller method                  │  │
│  │  6. Business logic runs                               │  │
│  │  7. Return result                                     │  │
│  └───────────────────────────────────────────────────────┘  │
└───────────────────────┬─────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────────┐
│          Back to IdempotencyAspect                           │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  8. Cache response in Hazelcast                       │  │
│  │  9. Return response to client                         │  │
│  └───────────────────────────────────────────────────────┘  │
└───────────────────────┬─────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────────┐
│                    Client Response                           │
└─────────────────────────────────────────────────────────────┘
```

## Pointcut Expressions - Chi tiết

### Execution Pointcut

```java
// Cú pháp
execution(modifiers? return-type declaring-type? method-name(params) throws?)

// Ví dụ
@Around("execution(public * com.khoa.spring.playground.service.*.*(..))")
//       │         │      │  │                                  │ │ │
//       │         │      │  │                                  │ │ └─ Any parameters
//       │         │      │  │                                  │ └─── Any method
//       │         │      │  │                                  └───── Any class
//       │         │      │  └────────────────────────────────────── Package
//       │         │      └───────────────────────────────────────── Declaring type
//       │         └──────────────────────────────────────────────── Return type
//       └────────────────────────────────────────────────────────── Modifier

// Cụ thể hơn
@Around("execution(public User com.khoa..UserService.create*(User))")
// → Public methods returning User, in UserService, method starts with "create", takes User param
```

### Annotation Pointcut

```java
// Any method with @Idempotent
@Around("@annotation(com.khoa.spring.playground.annotation.Idempotent)")

// Any method in class with @RestController
@Around("@within(org.springframework.web.bind.annotation.RestController)")

// Any method with parameter annotated with @RequestBody
@Around("@args(org.springframework.web.bind.annotation.RequestBody)")
```

### Within Pointcut

```java
// All methods in UserController
@Around("within(com.khoa.spring.playground.controller.UserController)")

// All methods in controller package
@Around("within(com.khoa.spring.playground.controller..*)")
```

### Bean Pointcut

```java
// Methods in Spring bean named "userService"
@Around("bean(userService)")

// All beans ending with "Controller"
@Around("bean(*Controller)")
```

### Combining Pointcuts

```java
// AND operator
@Around("@annotation(Idempotent) && execution(public * *(..))")

// OR operator
@Around("@annotation(Idempotent) || @annotation(Cacheable)")

// NOT operator
@Around("execution(* com.khoa.service.*.*(..)) && !bean(userService)")
```

## Ưu điểm của AOP

### 1. Separation of Concerns
Business logic và technical concerns được tách biệt hoàn toàn.

### 2. Code Reusability
Viết một lần, apply cho nhiều methods.

### 3. Maintainability
Thay đổi logic ở một nơi, affect tất cả nơi sử dụng.

### 4. Non-invasive
Không cần modify existing code để thêm functionality.

### 5. Declarative Programming
Sử dụng annotations thay vì imperative code.

## So sánh: Trước và Sau AOP

### ❌ Trước (Không dùng AOP)

```java
@PostMapping
public ResponseEntity<User> createUser(@RequestBody User user) {
    // Idempotency logic - duplicate everywhere!
    String key = request.getHeader("X-Idempotency-Key");
    if (key != null) {
        Optional<IdempotencyRequest> cached = idempotencyService.get(key);
        if (cached.isPresent()) {
            return ResponseEntity
                .status(cached.get().getStatusCode())
                .body(cached.get().getResponse());
        }
    }

    // Business logic
    User savedUser = userRepository.save(user);

    // Cache response
    if (key != null) {
        idempotencyService.store(key, savedUser, 201, hash, 86400);
    }

    return ResponseEntity.status(HttpStatus.CREATED).body(savedUser);
}

@PostMapping("/posts")
public ResponseEntity<Post> createPost(@RequestBody Post post) {
    // Same idempotency logic copied here!
    String key = request.getHeader("X-Idempotency-Key");
    // ... duplicate code ...

    Post savedPost = postRepository.save(post);

    // ... duplicate code ...
    return ResponseEntity.ok(savedPost);
}
```

**Vấn đề:**
- 20-30 dòng code cho idempotency
- Duplicate ở mỗi endpoint
- Khó maintain: Thay đổi logic → sửa 10+ files

### ✅ Sau (Dùng AOP)

```java
@PostMapping
@Idempotent  // ← 1 annotation!
public ResponseEntity<User> createUser(@RequestBody User user) {
    User savedUser = userRepository.save(user);
    return ResponseEntity.status(HttpStatus.CREATED).body(savedUser);
}

@PostMapping("/posts")
@Idempotent  // ← Same annotation!
public ResponseEntity<Post> createPost(@RequestBody Post post) {
    Post savedPost = postRepository.save(post);
    return ResponseEntity.ok(savedPost);
}
```

**Lợi ích:**
- 3-4 dòng code business logic
- No duplication
- Maintainability: Thay đổi logic → sửa 1 file (IdempotencyAspect)

## Use Cases thực tế

### 1. Logging

```java
@Aspect
@Component
public class LoggingAspect {

    @Around("@within(org.springframework.web.bind.annotation.RestController)")
    public Object logMethodExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        log.info("→ Executing: {} with args: {}",
            joinPoint.getSignature(), Arrays.toString(joinPoint.getArgs()));

        Object result = joinPoint.proceed();

        log.info("← Completed: {} with result: {}",
            joinPoint.getSignature(), result);

        return result;
    }
}
```

### 2. Transaction Management (Built-in)

```java
@Service
public class UserService {

    @Transactional  // ← Spring AOP handles transaction!
    public void transferMoney(Long fromId, Long toId, BigDecimal amount) {
        // All DB operations in one transaction
        accountRepository.debit(fromId, amount);
        accountRepository.credit(toId, amount);
        // Auto commit or rollback
    }
}
```

### 3. Rate Limiting

```java
@Aspect
@Component
public class RateLimitAspect {

    @Before("@annotation(RateLimited)")
    public void checkRateLimit(JoinPoint joinPoint) {
        String userId = getCurrentUserId();
        if (rateLimiter.isLimitExceeded(userId)) {
            throw new RateLimitExceededException("Too many requests");
        }
        rateLimiter.increment(userId);
    }
}
```

### 4. Retry Logic

```java
@Aspect
@Component
public class RetryAspect {

    @Around("@annotation(Retry)")
    public Object retry(ProceedingJoinPoint joinPoint) throws Throwable {
        int maxAttempts = 3;
        int attempt = 0;

        while (attempt < maxAttempts) {
            try {
                return joinPoint.proceed();
            } catch (Exception ex) {
                attempt++;
                if (attempt >= maxAttempts) {
                    throw ex;
                }
                Thread.sleep(1000 * attempt); // Exponential backoff
            }
        }
        throw new RuntimeException("Should not reach here");
    }
}
```

## Performance Considerations

### AOP có chậm không?

**Overhead:**
- Proxy creation: One-time cost khi application starts
- Method interception: ~0.01-0.1ms per call (negligible)

**Best Practices:**
- ✅ Sử dụng cho cross-cutting concerns có value
- ✅ Avoid overly broad pointcuts
- ❌ Không dùng AOP cho hot-path, performance-critical code

### Measuring Impact

```java
@Aspect
@Component
public class PerformanceMonitoringAspect {

    @Around("@annotation(Monitored)")
    public Object monitor(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.nanoTime();

        try {
            return joinPoint.proceed();
        } finally {
            long duration = System.nanoTime() - start;
            log.info("Method {} took {}μs",
                joinPoint.getSignature().getName(),
                duration / 1000);
        }
    }
}
```

## Debugging AOP

### Enable AOP Logging

```properties
# application.properties
logging.level.org.springframework.aop=DEBUG
logging.level.org.aspectj=DEBUG
```

### Common Issues

#### 1. Aspect không chạy

**Nguyên nhân:**
- Thiếu `@EnableAspectJAutoProxy` (Spring Boot tự enable)
- Pointcut expression sai
- Method không phải public
- Self-invocation (gọi method trong cùng class)

**Giải pháp:**
```java
// Verify pointcut
@Around("@annotation(com.khoa.spring.playground.annotation.Idempotent)")
public Object test(ProceedingJoinPoint joinPoint) throws Throwable {
    System.out.println("ASPECT TRIGGERED!"); // Debug print
    return joinPoint.proceed();
}
```

#### 2. Self-invocation không work

```java
@Service
public class UserService {

    @Transactional
    public void method1() {
        method2(); // ← Self-invocation, @Transactional không apply!
    }

    @Transactional
    public void method2() {
        // ...
    }
}
```

**Giải pháp:** Inject self hoặc tách sang service khác

## Dependencies

```xml
<!-- Spring Boot AOP Starter -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

Spring Boot tự động:
- Enable AspectJ auto-proxying
- Scan và register @Aspect beans
- Tạo proxies cho beans có matching pointcuts

## Tổng kết

Spring AOP là công cụ mạnh mẽ để:
- ✅ Tách biệt cross-cutting concerns
- ✅ Giảm code duplication
- ✅ Improve maintainability
- ✅ Keep business logic clean
- ✅ Declarative programming

**Trong project này:**
- Idempotency implementation hoàn toàn transparent
- Controllers chỉ chứa business logic
- Chỉ cần thêm `@Idempotent` annotation
- Dễ dàng extend cho các endpoints mới

**Best practices:**
- Sử dụng cho cross-cutting concerns có thật
- Tránh over-engineering với AOP
- Document rõ ràng behavior của aspects
- Test aspects riêng biệt với unit tests
