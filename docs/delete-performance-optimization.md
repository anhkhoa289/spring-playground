# Delete Performance Optimization - User với 1 triệu Posts

## Tổng Quan

Document này phân tích và so sánh các phương pháp tối ưu performance khi delete một User có lượng dữ liệu liên quan lớn (1 triệu posts).

### Vấn Đề Hiện Tại

**File:** `src/main/java/com/khoa/spring/playground/controller/UserController.java:64-72`

```java
@DeleteMapping("/{id}")
@CacheEvict(value = {"users", "posts"}, allEntries = true)
public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
    if (userRepository.existsById(id)) {
        userRepository.deleteById(id);  // ⚠️ Performance bottleneck
        return ResponseEntity.noContent().build();
    }
    return ResponseEntity.notFound().build();
}
```

**Cấu hình cascade hiện tại** (`src/main/java/com/khoa/spring/playground/entity/User.java`):
```java
@OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
@JsonManagedReference
private List<Post> posts = new ArrayList<>();
```

### Vấn Đề Performance

Với 1 triệu posts, implementation hiện tại gặp các vấn đề:

1. **N+1 Query Problem:**
   - `existsById()`: 1 SELECT query
   - `deleteById()`: 1 SELECT query (duplicate)
   - Load toàn bộ 1 triệu posts vào memory: 1 SELECT query
   - Delete posts: 1 triệu DELETE queries hoặc 1 bulk DELETE
   - Delete user: 1 DELETE query

2. **Memory Overflow:**
   - JPA load toàn bộ 1 triệu posts vào JVM heap
   - Risk: `OutOfMemoryError`

3. **Long Transaction:**
   - Operation time: 2-10 phút
   - Database locks kéo dài
   - Request timeout

4. **Inefficient Cache Eviction:**
   - Clear toàn bộ cache users và posts
   - Impact tất cả users khác

**Ước tính thời gian:**
- Trường hợp tốt: 30 giây - 2 phút
- Trường hợp xấu: 5-10 phút hoặc timeout/OOM

---

## Bảng So Sánh Các Giải Pháp

| Tiêu chí | JPA Cascade (Current) | Database ON DELETE CASCADE | Async Background Job | Hybrid (Async + DB CASCADE) |
|----------|----------------------|---------------------------|----------------------|----------------------------|
| **API Response Time** | 120-600s | 10-30s | < 1s | < 1s |
| **Actual Delete Time** | 120-600s | 10-30s | 10-30s | 10-30s |
| **Database Load** | Very High | High (short burst) | Low (distributed) | High (short burst) |
| **Application Memory** | Very High | Low | Low | Low |
| **User Experience** | ❌ Poor | ⚠️ Medium | ✅ Excellent | ✅ Excellent |
| **Complexity** | Low | Low | High | Medium |
| **Rollback** | Easy | Easy | Hard | Medium |
| **Progress Tracking** | ❌ No | ❌ No | ✅ Yes | ✅ Yes |
| **Scalability** | ❌ Poor | ⚠️ Medium | ✅ Good | ✅ Good |

---

## Giải Pháp 1: Database-level CASCADE DELETE

### Mô Tả

Chuyển logic cascade delete từ JPA sang database level bằng `ON DELETE CASCADE` constraint.

### Implementation

#### Bước 1: Database Migration

```sql
-- Migration script
ALTER TABLE posts
DROP CONSTRAINT IF EXISTS posts_user_id_fkey;

ALTER TABLE posts
ADD CONSTRAINT posts_user_id_fkey
FOREIGN KEY (user_id)
REFERENCES users(id)
ON DELETE CASCADE;

-- Add index for performance
CREATE INDEX IF NOT EXISTS idx_posts_user_id ON posts(user_id);
```

#### Bước 2: Update Entity

```java
// User.java - Remove JPA cascade
@OneToMany(mappedBy = "user")  // No cascade here
@JsonManagedReference
private List<Post> posts = new ArrayList<>();
```

#### Bước 3: Simple Delete

```java
@DeleteMapping("/{id}")
@Transactional
@CacheEvict(value = "users", key = "#id")  // Selective eviction
public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
    if (!userRepository.existsById(id)) {
        return ResponseEntity.notFound().build();
    }

    userRepository.deleteById(id);  // DB auto-deletes posts
    cacheManager.getCache("posts").clear();  // Clear posts cache after

    return ResponseEntity.noContent().build();
}
```

### Ưu Điểm

✅ **Performance cao nhất**
- Database engine tối ưu cho bulk delete
- Không load data vào JVM memory
- Thời gian: ~10-30 giây cho 1 triệu posts

✅ **Code đơn giản**
- Ít code changes
- Không cần infrastructure mới
- Dễ maintain

✅ **Transaction safety**
- Atomic operation (all or nothing)
- Dễ rollback nếu fail
- Database locks đảm bảo consistency

✅ **Referential integrity**
- Database đảm bảo data consistency
- Không lo orphaned records

### Nhược Điểm

❌ **Vẫn blocking API**
- User chờ 10-30 giây
- Request có thể timeout
- Server thread bị block

❌ **Database locks**
- Lock tables `users` và `posts`
- Ảnh hưởng concurrent requests
- Deadlock risk

❌ **Không tracking được**
- User không biết tiến trình
- Không thể cancel mid-way

❌ **Tight coupling với database**
- Migration phức tạp nếu đổi DB
- Khó tích hợp business logic (audit log, notifications)

### Khi Nào Dùng

Sử dụng Database CASCADE khi:
- ✅ Delete không thường xuyên (< 10 lần/ngày)
- ✅ Admin tool (không phải user-facing)
- ✅ Team nhỏ, muốn solution đơn giản
- ✅ Chấp nhận được blocking 10-30 giây
- ✅ Không cần tracking progress

**Use case:** Admin dashboard xóa spam users

---

## Giải Pháp 2: Async Background Job

### Mô Tả

Delete operation chạy trong background thread pool, API response ngay lập tức với job ID để tracking.

### Implementation

#### Bước 1: Configuration

```java
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "deleteUserExecutor")
    public Executor deleteUserExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("user-delete-");
        executor.initialize();
        return executor;
    }
}
```

#### Bước 2: Job Status Entity

```java
@Entity
@Table(name = "delete_jobs")
@Data
public class DeleteJob {

    @Id
    private String id;  // UUID

    @Enumerated(EnumType.STRING)
    private DeleteJobStatus status;  // PENDING, IN_PROGRESS, COMPLETED, FAILED

    private Long userId;
    private Long totalRecords;
    private Long processedRecords;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private String errorMessage;

    public int getProgress() {
        if (totalRecords == 0) return 0;
        return (int) ((processedRecords * 100) / totalRecords);
    }
}

public enum DeleteJobStatus {
    PENDING, IN_PROGRESS, COMPLETED, FAILED
}
```

#### Bước 3: Delete Service

```java
@Service
@Slf4j
public class UserDeletionService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private DeleteJobRepository deleteJobRepository;

    @Autowired
    private CacheManager cacheManager;

    /**
     * Schedule user deletion and return job ID immediately
     */
    public String scheduleDelete(Long userId) {
        // Count total posts
        long totalPosts = postRepository.countByUserId(userId);

        // Create job tracking
        DeleteJob job = new DeleteJob();
        job.setId(UUID.randomUUID().toString());
        job.setUserId(userId);
        job.setStatus(DeleteJobStatus.PENDING);
        job.setTotalRecords(totalPosts);
        job.setProcessedRecords(0L);
        job.setCreatedAt(LocalDateTime.now());
        deleteJobRepository.save(job);

        // Execute async
        deleteUserAsync(job.getId());

        return job.getId();
    }

    /**
     * Async delete with batch processing
     */
    @Async("deleteUserExecutor")
    @Transactional
    public CompletableFuture<Void> deleteUserAsync(String jobId) {
        DeleteJob job = deleteJobRepository.findById(jobId)
            .orElseThrow(() -> new RuntimeException("Job not found"));

        try {
            job.setStatus(DeleteJobStatus.IN_PROGRESS);
            deleteJobRepository.save(job);

            Long userId = job.getUserId();
            int batchSize = 1000;
            long totalDeleted = 0;

            // Batch delete posts
            while (true) {
                List<Long> postIds = postRepository
                    .findTopNIdsByUserId(userId, batchSize);

                if (postIds.isEmpty()) break;

                postRepository.deleteByIdIn(postIds);
                totalDeleted += postIds.size();

                // Update progress
                job.setProcessedRecords(totalDeleted);
                deleteJobRepository.save(job);

                // Evict cache for deleted posts
                postIds.forEach(id ->
                    cacheManager.getCache("posts").evict(id)
                );

                log.info("Deleted {}/{} posts for user {}",
                    totalDeleted, job.getTotalRecords(), userId);

                if (postIds.size() < batchSize) break;
            }

            // Finally delete user
            userRepository.deleteById(userId);
            cacheManager.getCache("users").evict(userId);

            // Mark job as completed
            job.setStatus(DeleteJobStatus.COMPLETED);
            job.setCompletedAt(LocalDateTime.now());
            deleteJobRepository.save(job);

            log.info("User {} deleted successfully", userId);

        } catch (Exception e) {
            log.error("Failed to delete user {}", job.getUserId(), e);
            job.setStatus(DeleteJobStatus.FAILED);
            job.setErrorMessage(e.getMessage());
            deleteJobRepository.save(job);
            throw e;
        }

        return CompletableFuture.completedFuture(null);
    }

    /**
     * Get job status
     */
    public DeleteJob getJobStatus(String jobId) {
        return deleteJobRepository.findById(jobId)
            .orElseThrow(() -> new RuntimeException("Job not found"));
    }
}
```

#### Bước 4: Repository Methods

```java
public interface PostRepository extends JpaRepository<Post, Long> {

    long countByUserId(Long userId);

    @Query("SELECT p.id FROM Post p WHERE p.user.id = :userId ORDER BY p.id LIMIT :limit")
    List<Long> findTopNIdsByUserId(@Param("userId") Long userId, @Param("limit") int limit);

    @Modifying
    @Query("DELETE FROM Post p WHERE p.id IN :ids")
    void deleteByIdIn(@Param("ids") List<Long> ids);
}
```

#### Bước 5: Controller Endpoints

```java
@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserDeletionService deletionService;

    /**
     * Delete user - returns immediately with job ID
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<DeleteJobResponse> deleteUser(@PathVariable Long id) {
        if (!userRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        String jobId = deletionService.scheduleDelete(id);

        return ResponseEntity.accepted()
            .body(new DeleteJobResponse(jobId, "PENDING"));
    }

    /**
     * Check delete job status
     */
    @GetMapping("/delete-jobs/{jobId}")
    public ResponseEntity<DeleteJobStatusResponse> getDeleteStatus(
            @PathVariable String jobId) {

        DeleteJob job = deletionService.getJobStatus(jobId);

        return ResponseEntity.ok(new DeleteJobStatusResponse(
            job.getId(),
            job.getStatus().name(),
            job.getProgress(),
            job.getProcessedRecords(),
            job.getTotalRecords(),
            job.getErrorMessage()
        ));
    }
}
```

#### Bước 6: Response DTOs

```java
@Data
@AllArgsConstructor
public class DeleteJobResponse {
    private String jobId;
    private String status;
}

@Data
@AllArgsConstructor
public class DeleteJobStatusResponse {
    private String jobId;
    private String status;
    private int progress;  // 0-100
    private Long processedRecords;
    private Long totalRecords;
    private String errorMessage;
}
```

### Ưu Điểm

✅ **Non-blocking API** (Quan trọng nhất)
- Response < 1 giây
- User experience tốt
- Không timeout

✅ **Scalability**
- Xử lý nhiều delete requests đồng thời
- Thread pool control resource usage
- Queue nếu quá tải

✅ **Progress Tracking**
- User xem được tiến trình (50%, 80%...)
- Có thể implement cancel functionality
- Better UX cho long operations

✅ **Flexible Processing**
- Batch delete (1000 posts/lần) control memory
- Selective cache invalidation per batch
- Dễ tích hợp business logic

✅ **Business Logic Integration**
- Dễ thêm audit logs
- Send notification khi xong
- Soft delete option
- Retry logic

### Nhược Điểm

❌ **Complexity cao**
- Cần async infrastructure (executor, thread pool)
- Job status tracking (database/Redis)
- More code to maintain

❌ **Eventual Consistency**
- User vẫn thấy data trong vài giây/phút
- Phải handle "deleting" state
- Race conditions nếu không careful

❌ **Error Handling phức tạp**
- Job fail giữa chừng phải cleanup
- Partial deletes cần rollback mechanism
- Dead letter queue cho failed jobs

❌ **Infrastructure Dependencies**
- Cần database cho job status (hoặc Redis)
- Monitoring tools
- More deployment complexity

❌ **Testing khó hơn**
- Async code harder to test
- Need integration tests
- Timing issues in tests

### Khi Nào Dùng

Sử dụng Async Background Job khi:
- ✅ User-facing feature (UX quan trọng)
- ✅ Delete thường xuyên (nhiều users xóa account)
- ✅ Cần tracking progress
- ✅ System có nhiều traffic (scalability quan trọng)
- ✅ Có infrastructure hỗ trợ async

**Use case:** User tự xóa account của mình

---

## Giải Pháp 3: HYBRID (Recommended)

### Mô Tả

Kết hợp Database CASCADE DELETE (performance) với Async Job (UX), đạt được cả hai lợi ích.

### Implementation

```java
@DeleteMapping("/{id}")
public ResponseEntity<?> deleteUser(
    @PathVariable Long id,
    @RequestParam(defaultValue = "async") String mode
) {
    if (!userRepository.existsById(id)) {
        return ResponseEntity.notFound().build();
    }

    if (mode.equals("sync")) {
        // Database CASCADE - for admin/batch operations
        userRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    } else {
        // Async + DB CASCADE - for user-facing operations
        String jobId = deletionService.scheduleDelete(id);
        return ResponseEntity.accepted()
            .body(new DeleteJobResponse(jobId, "PENDING"));
    }
}
```

**Async Service sử dụng DB CASCADE:**

```java
@Async("deleteUserExecutor")
@Transactional
public CompletableFuture<Void> deleteUserAsync(String jobId) {
    DeleteJob job = deleteJobRepository.findById(jobId)
        .orElseThrow(() -> new RuntimeException("Job not found"));

    try {
        job.setStatus(DeleteJobStatus.IN_PROGRESS);
        deleteJobRepository.save(job);

        // Simple delete - DB CASCADE handles posts
        userRepository.deleteById(job.getUserId());

        // Clear cache
        cacheManager.getCache("users").evict(job.getUserId());
        cacheManager.getCache("posts").clear();

        job.setStatus(DeleteJobStatus.COMPLETED);
        job.setCompletedAt(LocalDateTime.now());
        deleteJobRepository.save(job);

    } catch (Exception e) {
        job.setStatus(DeleteJobStatus.FAILED);
        job.setErrorMessage(e.getMessage());
        deleteJobRepository.save(job);
        throw e;
    }

    return CompletableFuture.completedFuture(null);
}
```

### Ưu Điểm

✅ **Best of both worlds:**
- Database CASCADE: Fast delete (10-30s)
- Async Job: Non-blocking API (< 1s response)
- Progress tracking
- Good scalability

✅ **Flexibility:**
- Sync mode cho admin
- Async mode cho users
- Dễ switch giữa 2 modes

✅ **Simpler than pure async:**
- Không cần batch processing logic
- Database handles cascade efficiently

### Nhược Điểm

❌ **Vẫn cần async infrastructure**
- Job tracking table/Redis
- Thread pool configuration

❌ **Database locks trong async job**
- Lock vẫn xảy ra, nhưng không block API response

### Performance So Sánh

| Metric | Current (JPA) | DB CASCADE | Async + Batch | Hybrid |
|--------|--------------|-----------|---------------|---------|
| API Response | 120-600s | 10-30s | < 1s | < 1s |
| Actual Delete | 120-600s | 10-30s | 10-30s | 10-30s |
| Memory Usage | Very High | Low | Low | Low |
| User Can Use App | ❌ No | ❌ No | ✅ Yes | ✅ Yes |
| Progress Track | ❌ No | ❌ No | ✅ Yes | ⚠️ Limited |
| Code Complexity | Low | Low | High | Medium |

---

## Monitoring & Metrics

### Metrics cần track

```java
// For async approach
@Component
public class DeleteMetrics {

    private final MeterRegistry meterRegistry;

    public void recordDeleteDuration(Long userId, long durationMs) {
        Timer.builder("user.delete.duration")
            .tag("userId", userId.toString())
            .register(meterRegistry)
            .record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordDeletedPosts(long count) {
        Counter.builder("user.delete.posts.count")
            .register(meterRegistry)
            .increment(count);
    }

    public void recordDeleteFailure(String reason) {
        Counter.builder("user.delete.failures")
            .tag("reason", reason)
            .register(meterRegistry)
            .increment();
    }
}
```

### Database Monitoring

```sql
-- Monitor long-running delete operations
SELECT
    pid,
    usename,
    application_name,
    state,
    query,
    now() - query_start AS duration
FROM pg_stat_activity
WHERE query LIKE '%DELETE%'
  AND state = 'active'
ORDER BY duration DESC;

-- Check table locks
SELECT
    t.relname AS table_name,
    l.locktype,
    l.mode,
    l.granted
FROM pg_locks l
JOIN pg_class t ON l.relation = t.oid
WHERE t.relname IN ('users', 'posts');
```

---

## Testing Strategy

### Load Testing

```java
@Test
public void testDeleteUserWith1MillionPosts() {
    // Setup
    User user = createUser();
    createMillionPosts(user);

    // Measure
    long startTime = System.currentTimeMillis();
    userRepository.deleteById(user.getId());
    long duration = System.currentTimeMillis() - startTime;

    // Assert
    assertThat(duration).isLessThan(30_000); // < 30 seconds
    assertThat(postRepository.countByUserId(user.getId())).isZero();
}
```

### Async Job Testing

```java
@Test
public void testAsyncDeleteWithProgressTracking() throws Exception {
    // Setup
    User user = createUserWithPosts(10000);

    // Schedule delete
    String jobId = deletionService.scheduleDelete(user.getId());

    // Wait and check progress
    await().atMost(60, TimeUnit.SECONDS)
        .pollInterval(1, TimeUnit.SECONDS)
        .until(() -> {
            DeleteJob job = deletionService.getJobStatus(jobId);
            return job.getStatus() == DeleteJobStatus.COMPLETED;
        });

    // Verify
    assertThat(userRepository.existsById(user.getId())).isFalse();
    assertThat(postRepository.countByUserId(user.getId())).isZero();
}
```

---

## Migration Plan

### Phase 1: Add Database Constraint
```sql
ALTER TABLE posts
ADD CONSTRAINT posts_user_id_fkey
FOREIGN KEY (user_id)
REFERENCES users(id)
ON DELETE CASCADE;
```

### Phase 2: Update Entity (Optional)
Remove `cascade = CascadeType.ALL` from User entity

### Phase 3: Implement Async (if needed)
Add async infrastructure, job tracking, service layer

### Phase 4: Monitor & Optimize
Track metrics, adjust batch sizes, tune thread pools

---

## Recommendation

**For this project, recommend: HYBRID approach**

Lý do:
1. ✅ User-facing feature cần good UX
2. ✅ Cần scalability cho multiple concurrent deletes
3. ✅ Database CASCADE đơn giản và fast
4. ✅ Async wrapper không phức tạp lắm
5. ✅ Best balance giữa performance, UX, và complexity

### Implementation Priority

1. **Immediate:** Add DB CASCADE constraint (quick win)
2. **Short term:** Implement async wrapper
3. **Long term:** Add monitoring, retry logic, notifications

---

## References

- Spring Data JPA: https://docs.spring.io/spring-data/jpa/docs/current/reference/html/
- Spring Async: https://spring.io/guides/gs/async-method/
- PostgreSQL CASCADE: https://www.postgresql.org/docs/current/ddl-constraints.html
- JPA Performance: https://vladmihalcea.com/jpa-hibernate-performance-tuning/
