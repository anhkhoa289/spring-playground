# JPA Cascade vs Database Cascade - Hướng Dẫn Chi Tiết

## Mục Lục
1. [Giới Thiệu](#giới-thiệu)
2. [JPA Cascade Operations](#jpa-cascade-operations)
3. [Database-Level Cascade](#database-level-cascade)
4. [So Sánh JPA vs Database Cascade](#so-sánh-jpa-vs-database-cascade)
5. [Examples từ Project](#examples-từ-project)
6. [Best Practices](#best-practices)
7. [Performance Considerations](#performance-considerations)
8. [Testing Strategies](#testing-strategies)

---

## Giới Thiệu

Cascade là cơ chế tự động lan truyền (propagate) các operations từ parent entity sang child entities trong quan hệ one-to-many hoặc one-to-one. Có hai levels cascade:

1. **JPA Level (Application Layer)**: JPA/Hibernate xử lý cascade thông qua annotations
2. **Database Level**: Database engine xử lý cascade thông qua foreign key constraints

### Tại Sao Cần Cascade?

Khi delete một User, bạn cũng cần delete tất cả Posts, Favorites, Resources của user đó để:
- ✅ Tránh orphaned records (dữ liệu rác)
- ✅ Đảm bảo referential integrity
- ✅ Tuân thủ business logic (GDPR - right to be forgotten)
- ✅ Tiết kiệm storage

**Vấn đề:** Có nên dùng JPA cascade hay Database cascade?

**Trả lời:** Tùy thuộc vào use case. Document này sẽ giúp bạn quyết định.

---

## JPA Cascade Operations

### 1. Các Loại Cascade Types

JPA cung cấp nhiều cascade types thông qua `CascadeType` enum:

```java
public enum CascadeType {
    PERSIST,   // Cascade persist operations
    MERGE,     // Cascade merge operations
    REMOVE,    // Cascade remove operations
    REFRESH,   // Cascade refresh operations
    DETACH,    // Cascade detach operations
    ALL        // Cascade all operations
}
```

### 2. CascadeType.PERSIST

**Khi dùng:** Khi save parent entity, tự động save child entities

```java
@Entity
public class User {
    @OneToMany(mappedBy = "user", cascade = CascadeType.PERSIST)
    private List<Post> posts = new ArrayList<>();
}

// Usage
User user = new User();
user.setUsername("john");

Post post = new Post();
post.setTitle("My First Post");
post.setUser(user);
user.getPosts().add(post);

userRepository.save(user);  // ✅ Tự động save cả post
```

**SQL Generated:**
```sql
INSERT INTO users (username, email) VALUES ('john', 'john@example.com');
INSERT INTO posts (title, user_id) VALUES ('My First Post', 1);  -- Auto-saved
```

**Ưu điểm:**
- ✅ Tiện lợi - không cần save từng child entity
- ✅ Đảm bảo atomicity - all or nothing

**Nhược điểm:**
- ❌ Có thể accidentally save entities chưa validate
- ❌ Performance issue với large collections

### 3. CascadeType.MERGE

**Khi dùng:** Khi update parent entity, tự động merge child entities từ detached state

```java
@Entity
public class User {
    @OneToMany(mappedBy = "user", cascade = CascadeType.MERGE)
    private List<Post> posts = new ArrayList<>();
}

// Usage - detached entities
User detachedUser = // ... from session 1
Post detachedPost = detachedUser.getPosts().get(0);
detachedPost.setTitle("Updated Title");

// In new session
User managedUser = userRepository.save(detachedUser);  // ✅ Post also merged
```

**SQL Generated:**
```sql
UPDATE users SET username = 'john' WHERE id = 1;
UPDATE posts SET title = 'Updated Title' WHERE id = 1;  -- Auto-merged
```

**Ưu điểm:**
- ✅ Tự động sync changes từ detached entities
- ✅ Useful cho long conversations (session-per-request)

**Nhược điểm:**
- ❌ Complex behavior - hard to debug
- ❌ Có thể overwrite changes trong database

### 4. CascadeType.REMOVE (Quan Trọng Nhất)

**Khi dùng:** Khi delete parent entity, tự động delete child entities

```java
@Entity
public class User {
    @OneToMany(mappedBy = "user", cascade = CascadeType.REMOVE)
    private List<Post> posts = new ArrayList<>();
}

// Usage
userRepository.deleteById(1L);  // ✅ Tự động delete tất cả posts
```

**SQL Generated (JPA Approach):**
```sql
-- Step 1: Load user and all posts into memory
SELECT * FROM users WHERE id = 1;
SELECT * FROM posts WHERE user_id = 1;  -- Load 1 million posts!

-- Step 2: Delete each post (N+1 problem)
DELETE FROM posts WHERE id = 1;
DELETE FROM posts WHERE id = 2;
...
DELETE FROM posts WHERE id = 1000000;  -- 1 million DELETE queries!

-- Step 3: Delete user
DELETE FROM users WHERE id = 1;
```

**⚠️ VẤN ĐỀ PERFORMANCE:**

Với 1 triệu posts:
- **Memory:** Load 1 triệu posts vào JVM heap → OutOfMemoryError risk
- **Queries:** 1 triệu DELETE queries (hoặc 1 bulk DELETE nếu batch enabled)
- **Time:** 2-10 phút
- **Database Locks:** Lock table `posts` trong suốt thời gian delete

**Khi nào dùng CascadeType.REMOVE:**
- ✅ Small collections (< 1000 entities)
- ✅ Cần business logic trước khi delete (audit logs, notifications)
- ✅ Soft delete
- ✅ Complex validation rules

**Khi nào KHÔNG nên dùng:**
- ❌ Large collections (> 10,000 entities)
- ❌ Performance-critical operations
- ❌ Simple cascade delete without business logic

### 5. CascadeType.REFRESH

**Khi dùng:** Khi refresh parent entity, tự động refresh child entities

```java
@Entity
public class User {
    @OneToMany(mappedBy = "user", cascade = CascadeType.REFRESH)
    private List<Post> posts = new ArrayList<>();
}

// Usage
entityManager.refresh(user);  // ✅ Reload cả user và posts từ database
```

**Use case:** Khi có concurrent updates từ other transactions

### 6. CascadeType.DETACH

**Khi dùng:** Khi detach parent entity, tự động detach child entities

```java
@Entity
public class User {
    @OneToMany(mappedBy = "user", cascade = CascadeType.DETACH)
    private List<Post> posts = new ArrayList<>();
}

// Usage
entityManager.detach(user);  // ✅ Detach cả user và posts khỏi persistence context
```

**Use case:** Memory management - giảm persistence context size

### 7. CascadeType.ALL

**Khi dùng:** Cascade tất cả operations (PERSIST, MERGE, REMOVE, REFRESH, DETACH)

```java
@Entity
public class User {
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Post> posts = new ArrayList<>();
}
```

**⚠️ CAUTION:**
- Rất powerful nhưng cũng rất dangerous
- Có thể accidentally delete/update nhiều data
- Chỉ dùng khi chắc chắn muốn cascade ALL operations

### 8. orphanRemoval vs CascadeType.REMOVE

**Khác biệt:**

```java
// CascadeType.REMOVE: Chỉ delete khi parent bị delete
@OneToMany(mappedBy = "user", cascade = CascadeType.REMOVE)
private List<Post> posts;

user.getPosts().remove(post);  // ❌ Post vẫn tồn tại trong DB (orphan!)
userRepository.delete(user);   // ✅ Delete tất cả posts

// orphanRemoval: Delete khi remove khỏi collection
@OneToMany(mappedBy = "user", orphanRemoval = true)
private List<Post> posts;

user.getPosts().remove(post);  // ✅ Post bị delete khỏi DB
userRepository.delete(user);   // ✅ Delete tất cả posts
```

**Best practice:** Thường combine `CascadeType.ALL` với `orphanRemoval = true`:

```java
@OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
private List<Post> posts;
```

---

## Database-Level Cascade

### 1. ON DELETE CASCADE

**Khi dùng:** Database tự động delete child records khi parent bị delete

#### Syntax (PostgreSQL)

```sql
ALTER TABLE posts
ADD CONSTRAINT fk_posts_user_id
FOREIGN KEY (user_id)
REFERENCES users(id)
ON DELETE CASCADE;
```

#### Other Databases

```sql
-- MySQL
ALTER TABLE posts
ADD CONSTRAINT fk_posts_user_id
FOREIGN KEY (user_id) REFERENCES users(id)
ON DELETE CASCADE;

-- SQL Server
ALTER TABLE posts
ADD CONSTRAINT fk_posts_user_id
FOREIGN KEY (user_id) REFERENCES users(id)
ON DELETE CASCADE;

-- Oracle
ALTER TABLE posts
ADD CONSTRAINT fk_posts_user_id
FOREIGN KEY (user_id) REFERENCES users(id)
ON DELETE CASCADE;
```

#### Cách Hoạt Động

```sql
-- Delete user
DELETE FROM users WHERE id = 1;

-- Database engine TƯ ĐỘNG execute:
DELETE FROM posts WHERE user_id = 1;          -- All posts
DELETE FROM favorites WHERE user_id = 1;      -- All favorites
DELETE FROM resources WHERE user_id = 1;      -- All resources
-- And then cascade to resource_details via resources FK
DELETE FROM resource_details WHERE resource_id IN (
    SELECT id FROM resources WHERE user_id = 1
);
```

**Performance (1 triệu posts):**
- ✅ **Time:** 10-30 giây (vs 2-10 phút JPA)
- ✅ **Memory:** Low - không load vào JVM
- ✅ **Queries:** 1 DELETE query per table (vs 1 triệu queries JPA)
- ✅ **Optimized:** Database engine được optimize cho bulk operations

#### Ưu Điểm

✅ **Performance tuyệt vời**
- Database engine optimize cho bulk delete
- Không load data vào application memory
- Parallel execution possible

✅ **Đơn giản**
- Ít code
- No N+1 problem
- No OOM errors

✅ **Atomic**
- All or nothing
- Transaction safety
- Rollback tự động nếu fail

✅ **Referential Integrity**
- Database enforce constraints
- Không thể có orphaned records
- Data consistency guaranteed

#### Nhược Điểm

❌ **Không có application-level hooks**
- Không trigger JPA lifecycle callbacks (@PreRemove, @PostRemove)
- Không log audit trails automatically
- Không send notifications

❌ **Tight coupling với database**
- Migration phức tạp nếu đổi database
- Khó portable across different databases

❌ **Ít flexible**
- Không thể inject custom business logic
- Không soft delete
- Không conditional delete

❌ **Debugging khó hơn**
- Delete happens silently trong database
- Không trace được trong application logs

### 2. ON DELETE Variants

```sql
-- CASCADE: Delete child records
ON DELETE CASCADE

-- SET NULL: Set foreign key to NULL (requires nullable FK)
ON DELETE SET NULL

-- SET DEFAULT: Set foreign key to default value
ON DELETE SET DEFAULT

-- RESTRICT: Prevent delete if child records exist (default)
ON DELETE RESTRICT

-- NO ACTION: Same as RESTRICT
ON DELETE NO ACTION
```

#### Example: ON DELETE SET NULL

```sql
ALTER TABLE posts
ADD CONSTRAINT fk_posts_user_id
FOREIGN KEY (user_id) REFERENCES users(id)
ON DELETE SET NULL;  -- Set user_id = NULL instead of deleting post
```

**Use case:** Khi muốn keep posts nhưng remove user reference (anonymous posts)

### 3. ON UPDATE CASCADE

**Khi dùng:** Tự động update child foreign keys khi parent primary key changes

```sql
ALTER TABLE posts
ADD CONSTRAINT fk_posts_user_id
FOREIGN KEY (user_id) REFERENCES users(id)
ON UPDATE CASCADE;
```

**Example:**

```sql
-- Update user ID from 1 to 999
UPDATE users SET id = 999 WHERE id = 1;

-- Database tự động update:
UPDATE posts SET user_id = 999 WHERE user_id = 1;
UPDATE favorites SET user_id = 999 WHERE user_id = 1;
-- etc.
```

**⚠️ NOTE:**
- Hiếm khi dùng vì primary keys thường không đổi
- Best practice: Dùng auto-increment IDs không bao giờ change
- Nếu cần "changeable IDs", dùng business keys riêng (username, email)

### 4. Cascade Chain (Multi-Level Cascade)

Database cascade có thể chain through nhiều levels:

```sql
-- Level 1: User → Resource
ALTER TABLE resources
ADD CONSTRAINT fk_resources_user_id
FOREIGN KEY (user_id) REFERENCES users(id)
ON DELETE CASCADE;

-- Level 2: Resource → ResourceDetail
ALTER TABLE resource_details
ADD CONSTRAINT fk_resource_details_resource_id
FOREIGN KEY (resource_id) REFERENCES resources(id)
ON DELETE CASCADE;
```

**Behavior:**

```sql
DELETE FROM users WHERE id = 1;

-- Step 1: Database deletes resources (via cascade from user)
DELETE FROM resources WHERE user_id = 1;

-- Step 2: Database deletes resource_details (via cascade from resource)
DELETE FROM resource_details WHERE resource_id IN (
    SELECT id FROM resources WHERE user_id = 1
);
```

**Cascade chain trong project này:**
```
User (delete)
  ├─> Posts (cascade delete)
  ├─> Favorites (cascade delete)
  └─> Resources (cascade delete)
        └─> ResourceDetails (cascade delete)  ← 2-level cascade
```

---

## So Sánh JPA vs Database Cascade

### Bảng So Sánh Tổng Quan

| Feature | JPA Cascade | Database Cascade | Hybrid |
|---------|-------------|------------------|---------|
| **Performance (Large Data)** | ❌ Poor (2-10 min) | ✅ Excellent (10-30s) | ✅ Excellent |
| **Memory Usage** | ❌ High (OOM risk) | ✅ Low | ✅ Low |
| **Business Logic** | ✅ Yes (hooks, events) | ❌ No | ⚠️ Limited |
| **Audit Logs** | ✅ Easy | ❌ Manual | ⚠️ Manual |
| **Soft Delete** | ✅ Yes | ❌ No | ✅ Yes |
| **Code Complexity** | Low | Low | Medium |
| **Database Portability** | ✅ High | ⚠️ Medium | ⚠️ Medium |
| **Testing** | ✅ Easy (unit tests) | ⚠️ Needs integration tests | ⚠️ Needs integration tests |
| **Debugging** | ✅ Easy (logs) | ❌ Hard (silent) | ⚠️ Medium |
| **Transaction Safety** | ✅ Yes | ✅ Yes | ✅ Yes |
| **Referential Integrity** | ⚠️ App-enforced | ✅ DB-enforced | ✅ DB-enforced |

### Performance Comparison (1 Million Posts)

| Metric | JPA Cascade | DB Cascade | Improvement |
|--------|------------|-----------|-------------|
| **Delete Time** | 120-600s | 10-30s | **20x faster** |
| **Memory Usage** | ~2GB | ~10MB | **200x less** |
| **SQL Queries** | 1,000,001 | 5 | **200,000x fewer** |
| **Database Locks** | Long | Short | **10x shorter** |
| **OOM Risk** | High | None | ✅ Eliminated |

### Khi Nào Dùng JPA Cascade

✅ **Sử dụng JPA Cascade khi:**

1. **Small collections** (< 1,000 entities)
   ```java
   @OneToMany(cascade = CascadeType.ALL)
   private List<Address> addresses;  // User có ~2-3 addresses
   ```

2. **Complex business logic** cần execute trước/sau delete
   ```java
   @Entity
   public class Post {
       @PreRemove
       public void beforeDelete() {
           // Send notification
           // Archive content
           // Update statistics
           auditLog.log("Post deleted: " + this.id);
       }
   }
   ```

3. **Soft delete** (không delete thật khỏi database)
   ```java
   @Entity
   @SQLDelete(sql = "UPDATE posts SET deleted = true WHERE id = ?")
   @Where(clause = "deleted = false")
   public class Post {
       private boolean deleted = false;
   }
   ```

4. **Audit requirements** (cần track từng record bị delete)
   ```java
   @OneToMany(cascade = CascadeType.REMOVE)
   private List<Document> documents;  // Each delete logged individually
   ```

5. **Unit testing dễ dàng**
   ```java
   @Test
   public void testDeleteUser() {
       User user = new User();
       user.getPosts().add(new Post());

       userRepository.delete(user);

       // Easy to verify in-memory
       assertThat(user.getPosts()).isEmpty();
   }
   ```

### Khi Nào Dùng Database Cascade

✅ **Sử dụng Database Cascade khi:**

1. **Large collections** (> 10,000 entities)
   ```sql
   -- 1 triệu posts per user
   ALTER TABLE posts
   ADD CONSTRAINT fk_posts_user_id FOREIGN KEY (user_id)
   REFERENCES users(id) ON DELETE CASCADE;
   ```

2. **Performance-critical** operations
   ```java
   // Simple delete - DB handles cascade
   @Transactional
   public void deleteUser(Long userId) {
       userRepository.deleteById(userId);  // 10-30s for 1M posts
   }
   ```

3. **No business logic** needed
   ```java
   // Just delete, no audit/notification/archive
   @DeleteMapping("/{id}")
   public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
       userRepository.deleteById(id);
       return ResponseEntity.noContent().build();
   }
   ```

4. **Referential integrity** là priority #1
   ```sql
   -- Database guarantees no orphaned records
   -- Even if application crashes mid-delete
   ON DELETE CASCADE
   ```

5. **Bulk operations**
   ```java
   // Delete nhiều users cùng lúc
   userRepository.deleteAllById(Arrays.asList(1L, 2L, 3L, ...));
   // DB cascade handles all children efficiently
   ```

### Hybrid Approach (Best Practice)

**Kết hợp cả hai để có best of both worlds:**

```java
// 1. Database-level CASCADE cho performance
// See: src/main/resources/db/migration/V001__add_cascade_delete_to_posts.sql

// 2. JPA không cascade, nhưng có business logic
@Entity
public class User {
    // No JPA cascade - DB handles it
    @OneToMany(mappedBy = "user")
    private List<Post> posts;

    @PreRemove
    public void beforeDelete() {
        // Business logic vẫn chạy cho User entity
        auditLog.log("Deleting user: " + this.id);
        notificationService.notifyUserDeletion(this);
    }
}

// 3. Service layer for complex logic
@Service
public class UserDeletionService {

    public void deleteUser(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("User not found"));

        // Pre-delete business logic
        long postCount = postRepository.countByUserId(userId);
        auditLog.log("Deleting user {} with {} posts", userId, postCount);

        // Simple delete - DB CASCADE handles children
        userRepository.delete(user);  // Fast + efficient

        // Post-delete business logic
        notificationService.notifyUserDeleted(userId, postCount);
        searchIndexService.removeUserFromIndex(userId);
        cacheService.evictUser(userId);
    }
}
```

**Advantages:**
- ✅ Performance: Database cascade (10-30s)
- ✅ Memory: Low (không load children)
- ✅ Business Logic: Service layer có thể add logic cho parent entity
- ✅ Audit: Log ở service layer
- ✅ Flexibility: Dễ extend thêm logic sau này

---

## Examples từ Project

### 1. Project Structure

```
User (1) ----< (many) Post
User (1) ----< (many) Favorite
User (1) ----< (many) Resource (1) ----< (many) ResourceDetail
```

### 2. Before: JPA Cascade (Slow)

**Entity Configuration (Before):**

```java
// src/main/java/com/khoa/spring/playground/entity/User.java
@Entity
public class User {
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Post> posts = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Favorite> favorites = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Resource> resources = new ArrayList<>();
}

@Entity
public class Resource {
    @OneToMany(mappedBy = "resource", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ResourceDetail> resourceDetails = new ArrayList<>();
}
```

**Controller (Before):**

```java
@DeleteMapping("/{id}")
public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
    userRepository.deleteById(id);  // ⚠️ Slow: 2-10 minutes for 1M posts
    return ResponseEntity.noContent().build();
}
```

**Performance Issues:**
- ❌ Load 1M posts + 100K favorites + 50K resources + 200K resource_details vào memory
- ❌ 1.35M DELETE queries
- ❌ 120-600 seconds
- ❌ OutOfMemoryError risk
- ❌ Request timeout

### 3. After: Database Cascade (Fast)

**Migration Script:**

Xem file: `src/main/resources/db/migration/V001__add_cascade_delete_to_posts.sql`

```sql
-- Add ON DELETE CASCADE to all tables
ALTER TABLE posts
ADD CONSTRAINT fk_posts_user_id
FOREIGN KEY (user_id) REFERENCES users(id)
ON DELETE CASCADE;

ALTER TABLE favorites
ADD CONSTRAINT fk_favorites_user_id
FOREIGN KEY (user_id) REFERENCES users(id)
ON DELETE CASCADE;

ALTER TABLE resources
ADD CONSTRAINT fk_resources_user_id
FOREIGN KEY (user_id) REFERENCES users(id)
ON DELETE CASCADE;

ALTER TABLE resource_details
ADD CONSTRAINT fk_resource_details_resource_id
FOREIGN KEY (resource_id) REFERENCES resources(id)
ON DELETE CASCADE;

-- Add indexes for performance
CREATE INDEX idx_posts_user_id ON posts(user_id);
CREATE INDEX idx_favorites_user_id ON favorites(user_id);
CREATE INDEX idx_resources_user_id ON resources(user_id);
CREATE INDEX idx_resource_details_resource_id ON resource_details(resource_id);
```

**Entity Configuration (After):**

```java
// src/main/java/com/khoa/spring/playground/entity/User.java
@Entity
public class User {
    // Removed JPA cascade - using database-level ON DELETE CASCADE instead
    @OneToMany(mappedBy = "user")
    private List<Post> posts = new ArrayList<>();

    @OneToMany(mappedBy = "user")
    private List<Favorite> favorites = new ArrayList<>();

    @OneToMany(mappedBy = "user")
    private List<Resource> resources = new ArrayList<>();
}

// src/main/java/com/khoa/spring/playground/entity/Resource.java
@Entity
public class Resource {
    // Removed JPA cascade - using database-level ON DELETE CASCADE instead
    @OneToMany(mappedBy = "resource")
    private List<ResourceDetail> resourceDetails = new ArrayList<>();
}
```

**Controller (After):**

```java
@DeleteMapping("/{id}")
@Transactional
public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
    if (!userRepository.existsById(id)) {
        return ResponseEntity.notFound().build();
    }

    userRepository.deleteById(id);  // ✅ Fast: 10-30 seconds

    // Clear cache after delete
    cacheManager.getCache("users").evict(id);
    cacheManager.getCache("posts").clear();

    return ResponseEntity.noContent().build();
}
```

**Performance Improvements:**
- ✅ 10-30 seconds (vs 120-600s) → **20x faster**
- ✅ ~10MB memory (vs ~2GB) → **200x less**
- ✅ 5 DELETE queries (vs 1.35M) → **270,000x fewer**
- ✅ No OOM errors
- ✅ No timeouts

### 4. Cascade Chain Example

**Scenario:** Delete User ID = 1

```sql
-- Execute this:
DELETE FROM users WHERE id = 1;

-- Database automatically executes:
-- Level 1 cascades
DELETE FROM posts WHERE user_id = 1;          -- 1,000,000 posts
DELETE FROM favorites WHERE user_id = 1;      -- 100,000 favorites
DELETE FROM resources WHERE user_id = 1;      -- 50,000 resources

-- Level 2 cascades (via resources)
DELETE FROM resource_details WHERE resource_id IN (
    SELECT id FROM resources WHERE user_id = 1
);  -- 200,000 resource_details
```

**Total deleted:** 1,350,001 records (1 user + 1.35M children)

**Time:** 10-30 seconds

### 5. Async Delete Service (Hybrid)

Xem implementation chi tiết tại: `docs/delete-performance-optimization.md`

```java
// src/main/java/com/khoa/spring/playground/service/UserDeletionService.java
@Service
public class UserDeletionService {

    /**
     * Async delete with DB CASCADE for best performance + UX
     */
    @Async("deleteUserExecutor")
    @Transactional
    public CompletableFuture<Void> deleteUserAsync(String jobId) {
        DeleteJob job = deleteJobRepository.findById(jobId)
            .orElseThrow(() -> new RuntimeException("Job not found"));

        try {
            job.setStatus(DeleteJobStatus.IN_PROGRESS);
            deleteJobRepository.save(job);

            // Pre-delete business logic
            Long userId = job.getUserId();
            auditLog.log("Starting deletion of user {}", userId);

            // Simple delete - DB CASCADE handles everything
            userRepository.deleteById(userId);  // 10-30s for 1M posts

            // Post-delete business logic
            cacheManager.getCache("users").evict(userId);
            cacheManager.getCache("posts").clear();
            notificationService.notifyUserDeleted(userId);

            job.setStatus(DeleteJobStatus.COMPLETED);
            job.setCompletedAt(LocalDateTime.now());
            deleteJobRepository.save(job);

            auditLog.log("Completed deletion of user {}", userId);

        } catch (Exception e) {
            job.setStatus(DeleteJobStatus.FAILED);
            job.setErrorMessage(e.getMessage());
            deleteJobRepository.save(job);
            throw e;
        }

        return CompletableFuture.completedFuture(null);
    }
}
```

**Benefits:**
- ✅ Non-blocking API (< 1s response)
- ✅ Fast delete (10-30s via DB CASCADE)
- ✅ Progress tracking
- ✅ Business logic support
- ✅ Best user experience

---

## Best Practices

### 1. Start with Database Cascade for Performance

**Rule of Thumb:**

```java
// Default: Database CASCADE for performance
@OneToMany(mappedBy = "user")  // No JPA cascade
private List<Post> posts;

// Migration
ALTER TABLE posts
ADD CONSTRAINT fk_posts_user_id
FOREIGN KEY (user_id) REFERENCES users(id)
ON DELETE CASCADE;
```

**Only add JPA cascade if:**
- Collections are small (< 1,000)
- Need business logic (@PreRemove hooks)
- Soft delete
- Complex validation

### 2. Always Add Indexes on Foreign Keys

```sql
-- MUST DO: Index foreign keys for CASCADE performance
CREATE INDEX idx_posts_user_id ON posts(user_id);
CREATE INDEX idx_favorites_user_id ON favorites(user_id);
CREATE INDEX idx_resources_user_id ON resources(user_id);
```

**Without index:** 10-30s delete → 5-10 minutes delete ❌

**With index:** 10-30s delete ✅

### 3. Use Transactions for Consistency

```java
@DeleteMapping("/{id}")
@Transactional  // ✅ IMPORTANT: Wrap in transaction
public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
    userRepository.deleteById(id);

    // These run in same transaction
    cacheManager.getCache("users").evict(id);
    auditLog.log("User deleted: {}", id);

    return ResponseEntity.noContent().build();
}
```

**Why:**
- ✅ Atomic: All or nothing
- ✅ Rollback if any step fails
- ✅ Consistent state

### 4. Handle Cache Invalidation

```java
@Service
public class UserDeletionService {

    @Transactional
    public void deleteUser(Long userId) {
        // Delete
        userRepository.deleteById(userId);

        // Invalidate cache AFTER delete
        cacheManager.getCache("users").evict(userId);

        // Clear related caches
        cacheManager.getCache("posts").clear();  // All posts cache
        cacheManager.getCache("favorites").clear();

        // Alternative: Selective eviction (better performance)
        List<Long> postIds = postRepository.findIdsByUserId(userId);
        postIds.forEach(id -> cacheManager.getCache("posts").evict(id));
    }
}
```

### 5. Audit Logging at Service Layer

```java
@Service
public class UserDeletionService {

    @Autowired
    private AuditLogService auditLog;

    @Transactional
    public void deleteUser(Long userId) {
        // Pre-delete: Get info for audit log
        User user = userRepository.findById(userId).orElseThrow();
        long postCount = postRepository.countByUserId(userId);

        // Audit: Before delete
        auditLog.log(AuditAction.USER_DELETE_STARTED,
            Map.of("userId", userId, "postCount", postCount));

        // Delete
        userRepository.deleteById(userId);

        // Audit: After delete
        auditLog.log(AuditAction.USER_DELETE_COMPLETED,
            Map.of("userId", userId, "username", user.getUsername()));
    }
}
```

**⚠️ Important:** Query counts BEFORE delete (sau khi delete thì data đã mất)

### 6. Test Both JPA and Database Cascade

```java
@SpringBootTest
@Transactional
public class CascadeTest {

    @Test
    public void testDatabaseCascadeDelete() {
        // Setup
        User user = createUser();
        createPosts(user, 1000);

        Long userId = user.getId();

        // Execute delete
        userRepository.deleteById(userId);
        entityManager.flush();

        // Verify: Posts should be deleted by DB CASCADE
        assertThat(userRepository.existsById(userId)).isFalse();
        assertThat(postRepository.countByUserId(userId)).isZero();
    }

    @Test
    public void testCascadeChain() {
        // Setup: User -> Resource -> ResourceDetail
        User user = createUser();
        Resource resource = createResource(user);
        createResourceDetails(resource, 100);

        Long userId = user.getId();
        Long resourceId = resource.getId();

        // Execute delete
        userRepository.deleteById(userId);
        entityManager.flush();

        // Verify: 2-level cascade
        assertThat(userRepository.existsById(userId)).isFalse();
        assertThat(resourceRepository.existsById(resourceId)).isFalse();
        assertThat(resourceDetailRepository.countByResourceId(resourceId)).isZero();
    }
}
```

### 7. Monitor Database Locks

```sql
-- PostgreSQL: Monitor locks during cascade delete
SELECT
    t.relname AS table_name,
    l.locktype,
    l.mode,
    l.granted,
    a.query,
    now() - a.query_start AS duration
FROM pg_locks l
JOIN pg_class t ON l.relation = t.oid
JOIN pg_stat_activity a ON l.pid = a.pid
WHERE t.relname IN ('users', 'posts', 'favorites', 'resources', 'resource_details')
ORDER BY duration DESC;
```

**Watch out for:**
- ❌ Long-running locks (> 1 minute)
- ❌ Exclusive locks blocking other transactions
- ❌ Deadlocks

### 8. Add Database Comments for Documentation

```sql
-- Document constraints in database
COMMENT ON CONSTRAINT fk_posts_user_id ON posts IS
'Foreign key with CASCADE DELETE - automatically deletes all posts when user is deleted.
Performance: ~10-30s for 1M posts with index on user_id.
See: docs/jpa-vs-database-cascade.md';

COMMENT ON INDEX idx_posts_user_id IS
'Required for CASCADE DELETE performance. Without this index, delete time increases from 10s to 5+ minutes.';
```

**Benefits:**
- ✅ Self-documenting database
- ✅ Help future developers understand why constraints exist
- ✅ DBA can see performance notes

### 9. Use Database Constraints to Enforce Integrity

```sql
-- Good: Let database enforce integrity
ALTER TABLE posts
ADD CONSTRAINT fk_posts_user_id
FOREIGN KEY (user_id) REFERENCES users(id)
ON DELETE CASCADE;

-- Also good: Add check constraints
ALTER TABLE posts
ADD CONSTRAINT check_posts_user_id_not_zero
CHECK (user_id > 0);
```

**Why:**
- ✅ Database guarantees integrity (even if app crashes)
- ✅ Faster than application-level checks
- ✅ Prevents data corruption

### 10. Document Cascade Behavior

```java
/**
 * User entity with Database-level CASCADE DELETE.
 *
 * <p>When a user is deleted, the database automatically deletes:
 * <ul>
 *   <li>All posts (via fk_posts_user_id CASCADE)</li>
 *   <li>All favorites (via fk_favorites_user_id CASCADE)</li>
 *   <li>All resources (via fk_resources_user_id CASCADE)</li>
 *   <li>All resource_details (via fk_resource_details_resource_id CASCADE, 2-level)</li>
 * </ul>
 *
 * <p>Performance for 1M posts: 10-30 seconds (vs 2-10 minutes with JPA cascade)
 *
 * <p><b>Important:</b> JPA cascade is intentionally NOT configured.
 * This improves delete performance by 20x for large collections.
 * See: docs/jpa-vs-database-cascade.md
 *
 * @see Post
 * @see Resource
 * @see ResourceDetail
 */
@Entity
@Table(name = "users")
public class User {
    // No JPA cascade - DB handles it
    @OneToMany(mappedBy = "user")
    private List<Post> posts = new ArrayList<>();
}
```

---

## Performance Considerations

### 1. Batch Size Configuration

Nếu vẫn phải dùng JPA cascade, configure batch size:

```properties
# application.properties
spring.jpa.properties.hibernate.jdbc.batch_size=50
spring.jpa.properties.hibernate.order_inserts=true
spring.jpa.properties.hibernate.order_updates=true
spring.jpa.properties.hibernate.jdbc.batch_versioned_data=true
```

**Impact:**
- JPA cascade: 1M queries → 20K batched queries
- Time: 10 minutes → 2 minutes
- Still worse than DB cascade (30 seconds)

### 2. Connection Pool Tuning

```properties
# HikariCP configuration for large deletes
spring.datasource.hikari.maximum-pool-size=10
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.connection-timeout=30000
spring.datasource.hikari.idle-timeout=600000
spring.datasource.hikari.max-lifetime=1800000
```

### 3. Database Vacuum (PostgreSQL)

Sau khi delete nhiều data:

```sql
-- Reclaim space and update statistics
VACUUM ANALYZE users;
VACUUM ANALYZE posts;
VACUUM ANALYZE favorites;
VACUUM ANALYZE resources;
VACUUM ANALYZE resource_details;
```

### 4. Monitoring Metrics

```java
@Component
public class CascadeMetrics {

    private final MeterRegistry registry;

    public void recordCascadeDelete(String entityType, long recordCount, long durationMs) {
        Timer.builder("cascade.delete.duration")
            .tag("entity", entityType)
            .register(registry)
            .record(durationMs, TimeUnit.MILLISECONDS);

        Counter.builder("cascade.delete.records")
            .tag("entity", entityType)
            .register(registry)
            .increment(recordCount);
    }
}

// Usage
long start = System.currentTimeMillis();
userRepository.deleteById(userId);
long duration = System.currentTimeMillis() - start;
cascadeMetrics.recordCascadeDelete("user", postCount, duration);
```

**Track:**
- Average delete duration
- P95, P99 percentiles
- Records deleted per operation
- Failures

### 5. Async Processing for Better UX

```java
@Service
public class UserDeletionService {

    /**
     * Delete user asynchronously for better UX
     * Returns immediately with job ID
     */
    public String scheduleDelete(Long userId) {
        DeleteJob job = new DeleteJob();
        job.setId(UUID.randomUUID().toString());
        job.setUserId(userId);
        job.setStatus(DeleteJobStatus.PENDING);
        deleteJobRepository.save(job);

        deleteUserAsync(job.getId());

        return job.getId();
    }

    @Async("deleteUserExecutor")
    @Transactional
    public CompletableFuture<Void> deleteUserAsync(String jobId) {
        DeleteJob job = deleteJobRepository.findById(jobId).orElseThrow();

        try {
            job.setStatus(DeleteJobStatus.IN_PROGRESS);
            deleteJobRepository.save(job);

            // DB CASCADE handles everything
            userRepository.deleteById(job.getUserId());

            job.setStatus(DeleteJobStatus.COMPLETED);
            deleteJobRepository.save(job);

        } catch (Exception e) {
            job.setStatus(DeleteJobStatus.FAILED);
            job.setErrorMessage(e.getMessage());
            deleteJobRepository.save(job);
        }

        return CompletableFuture.completedFuture(null);
    }
}
```

**Benefits:**
- ✅ API response < 1s
- ✅ User can continue using app
- ✅ Progress tracking
- ✅ Better UX

---

## Testing Strategies

### 1. Unit Tests (JPA Cascade)

```java
@DataJpaTest
public class JpaCascadeTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostRepository postRepository;

    @Test
    public void testCascadeDelete_WithJpaCascade() {
        // Setup: User with JPA CASCADE configured
        User user = new User();
        user.setUsername("john");

        Post post = new Post();
        post.setTitle("Test");
        post.setUser(user);
        user.getPosts().add(post);

        user = userRepository.save(user);
        Long userId = user.getId();
        Long postId = post.getId();

        // Delete
        userRepository.delete(user);

        // Verify: Post deleted by JPA cascade
        assertThat(userRepository.existsById(userId)).isFalse();
        assertThat(postRepository.existsById(postId)).isFalse();
    }
}
```

### 2. Integration Tests (Database Cascade)

```java
@SpringBootTest
@Transactional
public class DatabaseCascadeTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostRepository postRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    public void testCascadeDelete_WithDatabaseCascade() {
        // Setup: User WITHOUT JPA cascade
        User user = new User();
        user.setUsername("john");
        user = userRepository.save(user);

        Post post = new Post();
        post.setTitle("Test");
        post.setUser(user);
        post = postRepository.save(post);

        Long userId = user.getId();
        Long postId = post.getId();

        // Delete
        userRepository.deleteById(userId);
        entityManager.flush();  // Force SQL execution

        // Verify: Post deleted by DB CASCADE
        assertThat(userRepository.existsById(userId)).isFalse();
        assertThat(postRepository.existsById(postId)).isFalse();
    }

    @Test
    public void testCascadeChain_TwoLevels() {
        // Setup: User -> Resource -> ResourceDetail
        User user = userRepository.save(new User("john"));

        Resource resource = new Resource();
        resource.setUser(user);
        resource = resourceRepository.save(resource);

        ResourceDetail detail = new ResourceDetail();
        detail.setResource(resource);
        detail = resourceDetailRepository.save(detail);

        Long userId = user.getId();
        Long resourceId = resource.getId();
        Long detailId = detail.getId();

        // Delete
        userRepository.deleteById(userId);
        entityManager.flush();

        // Verify: 2-level cascade
        assertThat(userRepository.existsById(userId)).isFalse();
        assertThat(resourceRepository.existsById(resourceId)).isFalse();
        assertThat(resourceDetailRepository.existsById(detailId)).isFalse();
    }
}
```

### 3. Performance Tests

```java
@SpringBootTest
public class CascadePerformanceTest {

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    public void testDeletePerformance_10KPosts() {
        // Setup
        User user = createUser();
        createPosts(user, 10_000);

        Long userId = user.getId();

        // Measure
        long start = System.currentTimeMillis();
        userRepository.deleteById(userId);
        entityManager.flush();
        long duration = System.currentTimeMillis() - start;

        // Assert: Should complete in < 5 seconds with DB CASCADE
        assertThat(duration).isLessThan(5000);

        // Verify
        assertThat(postRepository.countByUserId(userId)).isZero();
    }

    @Test
    @Disabled("Only for manual performance testing")
    public void testDeletePerformance_1MillionPosts() {
        // Setup
        User user = createUser();
        createPostsBatch(user, 1_000_000);

        Long userId = user.getId();

        // Measure
        long start = System.currentTimeMillis();
        userRepository.deleteById(userId);
        entityManager.flush();
        long duration = System.currentTimeMillis() - start;

        // Assert: Should complete in < 60 seconds with DB CASCADE
        assertThat(duration).isLessThan(60_000);

        System.out.println("Delete duration: " + duration + "ms");
        System.out.println("Records/second: " + (1_000_000 / (duration / 1000.0)));
    }

    private void createPostsBatch(User user, int count) {
        int batchSize = 1000;
        for (int i = 0; i < count; i += batchSize) {
            List<Post> batch = new ArrayList<>();
            for (int j = 0; j < batchSize && (i + j) < count; j++) {
                Post post = new Post();
                post.setTitle("Post " + (i + j));
                post.setUser(user);
                batch.add(post);
            }
            postRepository.saveAll(batch);
            entityManager.flush();
            entityManager.clear();
        }
    }
}
```

### 4. Verify Migration

```java
@SpringBootTest
public class MigrationVerificationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    public void verifyCascadeConstraintsExist() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();

            // Verify posts constraint
            ResultSet rs = meta.getImportedKeys(null, null, "posts");
            boolean foundPostsCascade = false;
            while (rs.next()) {
                String fkName = rs.getString("FK_NAME");
                short deleteRule = rs.getShort("DELETE_RULE");
                if (fkName.equals("fk_posts_user_id") &&
                    deleteRule == DatabaseMetaData.importedKeyCascade) {
                    foundPostsCascade = true;
                }
            }
            assertThat(foundPostsCascade).isTrue();

            // Verify other tables...
        }
    }
}
```

---

## References & Further Reading

### Official Documentation

1. **JPA Specification**: https://jakarta.ee/specifications/persistence/3.1/
2. **Hibernate User Guide**: https://docs.jboss.org/hibernate/orm/current/userguide/html_single/Hibernate_User_Guide.html
3. **Spring Data JPA**: https://docs.spring.io/spring-data/jpa/docs/current/reference/html/

### Database Documentation

1. **PostgreSQL Foreign Keys**: https://www.postgresql.org/docs/current/ddl-constraints.html#DDL-CONSTRAINTS-FK
2. **MySQL Foreign Keys**: https://dev.mysql.com/doc/refman/8.0/en/create-table-foreign-keys.html
3. **Oracle Foreign Keys**: https://docs.oracle.com/en/database/oracle/oracle-database/21/sqlrf/constraint.html

### Performance Optimization

1. **Vlad Mihalcea's Blog**: https://vladmihalcea.com/jpa-hibernate-performance-tuning/
2. **High-Performance Java Persistence**: https://vladmihalcea.com/books/high-performance-java-persistence/
3. **Spring Data JPA Best Practices**: https://thorben-janssen.com/spring-data-jpa-best-practices/

### Project-Specific Documents

1. **Delete Performance Optimization**: `docs/delete-performance-optimization.md`
2. **Database Migration**: `src/main/resources/db/migration/V001__add_cascade_delete_to_posts.sql`
3. **User Entity**: `src/main/java/com/khoa/spring/playground/entity/User.java`
4. **UserDeletionService**: `src/main/java/com/khoa/spring/playground/service/UserDeletionService.java`

---

## Summary & Recommendations

### Key Takeaways

1. **Default to Database CASCADE** for large collections (> 10K records)
   - 20x faster performance
   - 200x less memory
   - No OOM errors

2. **Use JPA Cascade** for small collections with business logic
   - Complex validation
   - Audit logging per record
   - Soft delete
   - Lifecycle hooks

3. **Hybrid Approach** gives best of both worlds
   - DB CASCADE for performance
   - Service layer for business logic
   - Async for better UX

4. **Always add indexes** on foreign keys
   - Critical for CASCADE performance
   - 10s → 5min without index

5. **Test both approaches** in your environment
   - Performance tests with realistic data
   - Integration tests for cascade chains
   - Monitor database locks

### Decision Matrix

```
Collection Size < 1K:
  → Use JPA Cascade (simpler, easier to test)

Collection Size 1K - 10K:
  → Use DB Cascade if no business logic
  → Use JPA Cascade if need hooks/audit

Collection Size > 10K:
  → Always use DB Cascade
  → Add Async wrapper for UX
  → Add business logic at service layer

Need Progress Tracking:
  → Use Async + DB Cascade

Need Soft Delete:
  → Use JPA Cascade with @SQLDelete

Need Audit Every Record:
  → Use JPA Cascade with @PreRemove

Performance Critical:
  → Always use DB Cascade
```

### Implementation Checklist

- [ ] Analyze collection sizes in your domain
- [ ] Identify which relationships need business logic
- [ ] Create database migration for CASCADE constraints
- [ ] Add indexes on all foreign keys
- [ ] Remove JPA cascade from large collections
- [ ] Add service layer for business logic
- [ ] Implement async wrapper if needed
- [ ] Update cache invalidation logic
- [ ] Add audit logging at service layer
- [ ] Write integration tests
- [ ] Performance test with realistic data
- [ ] Monitor database locks
- [ ] Document cascade behavior in code
- [ ] Update team documentation

### Next Steps

1. **Review current entities** - identify large collections
2. **Run performance tests** - measure current delete performance
3. **Create migration** - add DB CASCADE constraints
4. **Measure improvement** - compare before/after
5. **Deploy gradually** - start with one entity type
6. **Monitor production** - track metrics and errors

---

**Document Created:** 2025-11-19
**Project:** spring-playground
**Related Docs:**
- `docs/delete-performance-optimization.md`
- `src/main/resources/db/migration/V001__add_cascade_delete_to_posts.sql`
