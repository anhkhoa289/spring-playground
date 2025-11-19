# Spring @Transactional - Hướng Dẫn Chi Tiết

## Mục Lục
1. [Giới Thiệu](#giới-thiệu)
2. [Transaction Basics](#transaction-basics)
3. [Propagation Types](#propagation-types)
4. [Isolation Levels](#isolation-levels)
5. [Best Practices](#best-practices)
6. [Common Pitfalls](#common-pitfalls)
7. [Examples từ Project](#examples-từ-project)
8. [Testing Strategies](#testing-strategies)

---

## Giới Thiệu

### @Transactional Annotation là gì?

`@Transactional` là annotation trong Spring Framework để quản lý database transactions một cách declarative. Thay vì phải manually manage transactions (begin, commit, rollback), Spring tự động xử lý tất cả cho bạn.

```java
@Service
public class UserService {

    @Transactional
    public void createUser(User user) {
        // Spring tự động:
        // 1. BEGIN transaction
        // 2. Execute method
        // 3. COMMIT nếu success
        // 4. ROLLBACK nếu có exception
        userRepository.save(user);
        auditLogRepository.save(new AuditLog("User created"));
    }
}
```

### ACID Properties

Transactions đảm bảo 4 properties quan trọng (ACID):

1. **Atomicity** (Tính nguyên tử): All or nothing
   ```java
   @Transactional
   public void transferMoney(Long fromId, Long toId, BigDecimal amount) {
       accountRepository.debit(fromId, amount);   // Step 1
       accountRepository.credit(toId, amount);     // Step 2
       // Nếu step 2 fail → rollback cả step 1
   }
   ```

2. **Consistency** (Tính nhất quán): Database remains in valid state
   ```java
   @Transactional
   public void updateUserAndProfile(User user, Profile profile) {
       userRepository.save(user);
       profileRepository.save(profile);
       // Đảm bảo foreign key constraints
   }
   ```

3. **Isolation** (Tính cô lập): Concurrent transactions không affect nhau
   ```java
   @Transactional(isolation = Isolation.SERIALIZABLE)
   public void processOrder(Order order) {
       // Transaction này isolated khỏi transactions khác
   }
   ```

4. **Durability** (Tính bền vững): Committed data persists
   ```java
   @Transactional
   public void createOrder(Order order) {
       orderRepository.save(order);
       // Sau khi commit, data guaranteed to persist
   }
   ```

### Khi Nào Cần @Transactional?

✅ **Sử dụng khi:**
- Multiple database operations cần atomic (all or nothing)
- Update multiple entities
- Complex business logic với nhiều repository calls
- Cần isolation để tránh concurrent access issues

❌ **KHÔNG cần khi:**
- Single read-only operation (hoặc dùng `@Transactional(readOnly = true)`)
- Operations không modify data
- Simple queries

---

## Transaction Basics

### 1. Transaction Lifecycle

```java
@Transactional
public void processOrder(Order order) {
    // 1. Transaction BEGIN (automatic)

    // 2. Execute business logic
    orderRepository.save(order);
    inventoryService.reduceStock(order.getProductId(), order.getQuantity());

    // 3a. COMMIT if success (automatic)
    // 3b. ROLLBACK if exception (automatic)
}
```

### 2. Transaction Manager

Spring sử dụng PlatformTransactionManager để quản lý transactions:

```java
// Configuration (usually auto-configured)
@Configuration
public class TransactionConfig {

    @Bean
    public PlatformTransactionManager transactionManager(EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }
}
```

**Common Transaction Managers:**
- `JpaTransactionManager` - JPA/Hibernate (most common)
- `DataSourceTransactionManager` - JDBC
- `JtaTransactionManager` - Distributed transactions
- `HibernateTransactionManager` - Legacy Hibernate

### 3. Rollback Rules

**Default behavior:**
- ✅ Rollback: RuntimeException & Error
- ❌ NO Rollback: Checked exceptions

```java
// Default: Rollback on RuntimeException
@Transactional
public void createUser(User user) {
    userRepository.save(user);
    throw new RuntimeException("Error");  // ✅ Rollback
}

// Checked exception: NO rollback by default
@Transactional
public void createUser(User user) throws IOException {
    userRepository.save(user);
    throw new IOException("Error");  // ❌ NO Rollback (committed!)
}

// Custom rollback rules
@Transactional(rollbackFor = Exception.class)
public void createUser(User user) throws IOException {
    userRepository.save(user);
    throw new IOException("Error");  // ✅ Rollback
}

@Transactional(noRollbackFor = IllegalArgumentException.class)
public void createUser(User user) {
    userRepository.save(user);
    throw new IllegalArgumentException("Error");  // ❌ NO Rollback
}
```

### 4. Read-Only Transactions

```java
@Transactional(readOnly = true)
public List<User> getAllUsers() {
    return userRepository.findAll();
}
```

**Benefits:**
- ✅ Performance optimization (database can optimize read-only queries)
- ✅ Prevents accidental writes
- ✅ Hibernate flush mode set to MANUAL (no dirty checking)
- ✅ Clear intent in code

**⚠️ Important:** Read-only hint is just that - a hint. Không prevent writes nếu code explicitly calls save/update.

### 5. Timeout

```java
@Transactional(timeout = 30)  // 30 seconds
public void longRunningOperation() {
    // If not completed in 30s → rollback
    processLargeDataset();
}
```

**Use cases:**
- Prevent long-running transactions
- Avoid database lock timeouts
- Resource cleanup

---

## Propagation Types

Propagation định nghĩa **transaction boundary behavior** khi method gọi method khác.

### 1. REQUIRED (Default)

**Behavior:** Join existing transaction hoặc tạo mới nếu chưa có

```java
@Service
public class OrderService {

    @Transactional(propagation = Propagation.REQUIRED)
    public void createOrder(Order order) {
        orderRepository.save(order);           // Transaction A
        inventoryService.reduceStock(order);   // Join Transaction A
    }
}

@Service
public class InventoryService {

    @Transactional(propagation = Propagation.REQUIRED)  // Default
    public void reduceStock(Order order) {
        // Join existing transaction from createOrder
        inventoryRepository.updateStock(order.getProductId(), -order.getQuantity());
    }
}
```

**Behavior:**
```
createOrder() starts → Transaction A BEGIN
  ├─ orderRepository.save() → uses Transaction A
  └─ reduceStock() → JOINS Transaction A (không tạo mới)
       └─ inventoryRepository.updateStock() → uses Transaction A
Transaction A COMMIT (hoặc ROLLBACK nếu có lỗi)
```

**Use cases:**
- ✅ Default choice
- ✅ Muốn tất cả operations trong cùng 1 transaction
- ✅ All or nothing behavior

**⚠️ Important:** Nếu `reduceStock()` throw exception → TOÀN BỘ transaction rollback (kể cả `orderRepository.save()`)

### 2. REQUIRES_NEW

**Behavior:** Luôn tạo transaction mới, suspend existing transaction

```java
@Service
public class OrderService {

    @Transactional
    public void createOrder(Order order) {
        orderRepository.save(order);           // Transaction A
        auditService.logOrderCreated(order);   // Transaction B (new)
        // Nếu có lỗi ở đây → rollback Transaction A nhưng Transaction B đã commit
    }
}

@Service
public class AuditService {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logOrderCreated(Order order) {
        // NEW transaction (independent of caller)
        auditLogRepository.save(new AuditLog("Order created: " + order.getId()));
        // Commits immediately when method exits
    }
}
```

**Behavior:**
```
createOrder() starts → Transaction A BEGIN
  ├─ orderRepository.save() → uses Transaction A
  └─ logOrderCreated() → Transaction A SUSPENDED
       ├─ Transaction B BEGIN (new)
       ├─ auditLogRepository.save() → uses Transaction B
       └─ Transaction B COMMIT
  ← Transaction A RESUMED
Transaction A COMMIT
```

**Use cases:**
- ✅ Audit logging (luôn muốn log kể cả khi main transaction rollback)
- ✅ Independent operations
- ✅ Notification/Email sending

**Example: Audit Log Always Persists**

```java
@Transactional
public void processPayment(Payment payment) {
    try {
        paymentRepository.save(payment);
        auditService.log("Payment attempted");  // REQUIRES_NEW

        if (payment.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid amount");
        }

        auditService.log("Payment successful");  // REQUIRES_NEW

    } catch (Exception e) {
        auditService.log("Payment failed: " + e.getMessage());  // REQUIRES_NEW
        throw e;  // Rollback main transaction
    }
}

// Result nếu payment fail:
// - Payment NOT saved (rollback)
// - Audit logs SAVED (3 logs: attempted, failed)
```

**⚠️ Performance Warning:**
- Creating new transaction có overhead
- Cẩn thận với deadlocks (2 transactions cùng lock resources)

### 3. SUPPORTS

**Behavior:** Join transaction nếu có, không có thì execute non-transactionally

```java
@Service
public class ReportService {

    @Transactional(propagation = Propagation.SUPPORTS)
    public Report generateReport(Long userId) {
        // If called from transactional method → join transaction
        // If called from non-transactional method → no transaction
        List<Order> orders = orderRepository.findByUserId(userId);
        return new Report(orders);
    }
}

// Case 1: Called from transactional context
@Transactional
public void processUserData(Long userId) {
    updateUser(userId);
    reportService.generateReport(userId);  // ✅ Joins transaction
}

// Case 2: Called from non-transactional context
@GetMapping("/report/{userId}")
public Report getReport(@PathVariable Long userId) {
    return reportService.generateReport(userId);  // ⚠️ NO transaction
}
```

**Use cases:**
- ✅ Read-only operations that work with or without transaction
- ✅ Utility methods
- ✅ Flexible service methods

**⚠️ Warning:** Behavior khác nhau depending on caller → có thể confusing

### 4. NOT_SUPPORTED

**Behavior:** Luôn execute NON-transactionally, suspend existing transaction

```java
@Service
public class ExternalService {

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void callExternalAPI(Order order) {
        // Transaction suspended (if exists)
        // Execute without transaction
        externalApiClient.notifyOrder(order);
        // Transaction resumed (if exists)
    }
}

@Transactional
public void createOrder(Order order) {
    orderRepository.save(order);              // Transaction A
    externalService.callExternalAPI(order);   // NO transaction (A suspended)
    // Transaction A resumed
}
```

**Use cases:**
- ✅ External API calls (không cần transaction)
- ✅ File I/O operations
- ✅ Operations that shouldn't be part of transaction

**Why suspend transaction:**
- Free up database connection
- Avoid holding locks during slow operations
- Better resource utilization

### 5. MANDATORY

**Behavior:** MUST have existing transaction, throw exception nếu không có

```java
@Service
public class OrderItemService {

    @Transactional(propagation = Propagation.MANDATORY)
    public void addOrderItem(OrderItem item) {
        // REQUIRES existing transaction
        // Throw exception if called outside transaction
        orderItemRepository.save(item);
    }
}

// ✅ OK: Called from transactional method
@Transactional
public void createOrder(Order order) {
    orderRepository.save(order);
    orderItemService.addOrderItem(item);  // OK: Transaction exists
}

// ❌ ERROR: Called without transaction
@GetMapping("/item")
public void addItem(OrderItem item) {
    orderItemService.addOrderItem(item);  // ❌ Exception: No existing transaction
}
```

**Use cases:**
- ✅ Enforce transaction usage (prevent accidental non-transactional calls)
- ✅ Methods that MUST be part of larger transaction
- ✅ Internal service methods

**Exception thrown:**
```
IllegalTransactionStateException: No existing transaction found for transaction marked with propagation 'mandatory'
```

### 6. NEVER

**Behavior:** MUST NOT have transaction, throw exception nếu có

```java
@Service
public class CacheService {

    @Transactional(propagation = Propagation.NEVER)
    public void evictCache(String key) {
        // MUST execute outside transaction
        // Throw exception if called within transaction
        cacheManager.getCache("users").evict(key);
    }
}

// ✅ OK: Called without transaction
@GetMapping("/cache/evict/{key}")
public void evictCache(@PathVariable String key) {
    cacheService.evictCache(key);  // OK: No transaction
}

// ❌ ERROR: Called from transactional method
@Transactional
public void updateUser(User user) {
    userRepository.save(user);
    cacheService.evictCache(user.getId());  // ❌ Exception: Transaction exists
}
```

**Use cases:**
- ✅ Operations that conflict with transactions
- ✅ Cache eviction (should happen after commit)
- ✅ Enforce non-transactional execution

**Exception thrown:**
```
IllegalTransactionStateException: Existing transaction found for transaction marked with propagation 'never'
```

### 7. NESTED

**Behavior:** Tạo nested transaction (savepoint) trong existing transaction

```java
@Service
public class OrderService {

    @Transactional
    public void createOrder(Order order) {
        orderRepository.save(order);  // Main transaction

        try {
            discountService.applyDiscount(order);  // Nested transaction (savepoint)
        } catch (Exception e) {
            log.warn("Discount failed, continuing without discount", e);
            // Rollback to savepoint (order still saved)
            // Main transaction continues
        }

        // Main transaction commits (even if discount failed)
    }
}

@Service
public class DiscountService {

    @Transactional(propagation = Propagation.NESTED)
    public void applyDiscount(Order order) {
        // Creates savepoint in existing transaction
        Discount discount = discountRepository.findByCode(order.getDiscountCode());
        if (discount == null) {
            throw new DiscountNotFoundException();  // Rollback to savepoint only
        }
        order.setDiscount(discount.getAmount());
    }
}
```

**Behavior:**
```
createOrder() → Transaction A BEGIN
  ├─ orderRepository.save() → uses Transaction A
  └─ applyDiscount() → SAVEPOINT created
       ├─ discountRepository.findByCode() → uses Transaction A
       ├─ Exception thrown
       └─ ROLLBACK TO SAVEPOINT (order save preserved)
  ← Transaction A continues
Transaction A COMMIT (order saved, discount not applied)
```

**Use cases:**
- ✅ Optional operations (có thể fail nhưng không affect main transaction)
- ✅ Batch processing (rollback individual items, không affect batch)
- ✅ Partial rollback scenarios

**⚠️ Important:**
- Chỉ work với databases support savepoints (PostgreSQL, Oracle, SQL Server)
- MySQL/InnoDB supports savepoints
- Performance overhead (savepoint creation/rollback)

**Example: Batch Processing**

```java
@Transactional
public void processBatch(List<Order> orders) {
    int success = 0;
    int failed = 0;

    for (Order order : orders) {
        try {
            orderService.processOrder(order);  // NESTED
            success++;
        } catch (Exception e) {
            log.error("Failed to process order: " + order.getId(), e);
            failed++;
            // Continue với orders khác
        }
    }

    log.info("Batch completed: {} success, {} failed", success, failed);
    // Commit successful orders
}

@Transactional(propagation = Propagation.NESTED)
public void processOrder(Order order) {
    // Process individual order
    // If fail → rollback to savepoint only
}
```

### Propagation Comparison Table

| Propagation | Existing TX | No Existing TX | Creates TX | Can Rollback Independently |
|-------------|-------------|----------------|------------|---------------------------|
| **REQUIRED** (default) | Join | Create new | Yes | No |
| **REQUIRES_NEW** | Suspend, create new | Create new | Yes | Yes |
| **SUPPORTS** | Join | No TX | No | N/A |
| **NOT_SUPPORTED** | Suspend | No TX | No | N/A |
| **MANDATORY** | Join | Exception ❌ | No | No |
| **NEVER** | Exception ❌ | No TX | No | N/A |
| **NESTED** | Create savepoint | Create new | Yes | Yes (savepoint) |

### Common Use Cases

```java
// 1. Normal business logic (default)
@Transactional  // REQUIRED
public void createOrder(Order order) {
    orderRepository.save(order);
    orderItemRepository.saveAll(order.getItems());
}

// 2. Audit logging (always save)
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void logAudit(String message) {
    auditRepository.save(new AuditLog(message));
}

// 3. Read-only report (flexible)
@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
public Report generateReport() {
    return new Report(orderRepository.findAll());
}

// 4. External API call (no transaction needed)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public void notifyExternal(Order order) {
    externalApi.sendNotification(order);
}

// 5. Must be in transaction (enforce)
@Transactional(propagation = Propagation.MANDATORY)
public void addOrderItem(OrderItem item) {
    orderItemRepository.save(item);
}

// 6. Must NOT be in transaction (enforce)
@Transactional(propagation = Propagation.NEVER)
public void evictCache(String key) {
    cache.evict(key);
}

// 7. Optional operation (can fail)
@Transactional(propagation = Propagation.NESTED)
public void applyBonus(Order order) {
    bonusRepository.apply(order);
}
```

---

## Isolation Levels

Isolation level định nghĩa **how transactions see data modified by other concurrent transactions**.

### Concurrency Problems

**1. Dirty Read**: Read uncommitted data từ transaction khác

```sql
-- Transaction A
BEGIN;
UPDATE accounts SET balance = 1000 WHERE id = 1;
-- NOT YET COMMITTED

-- Transaction B (concurrent)
SELECT balance FROM accounts WHERE id = 1;  -- Reads 1000 (dirty!)

-- Transaction A
ROLLBACK;  -- Balance still 500

-- Transaction B đã đọc data sai (1000 instead of 500)
```

**2. Non-Repeatable Read**: Read same row twice → different results

```sql
-- Transaction A
BEGIN;
SELECT balance FROM accounts WHERE id = 1;  -- Returns 500

-- Transaction B (concurrent)
UPDATE accounts SET balance = 1000 WHERE id = 1;
COMMIT;

-- Transaction A
SELECT balance FROM accounts WHERE id = 1;  -- Returns 1000 (changed!)
```

**3. Phantom Read**: Read same query twice → different rows

```sql
-- Transaction A
BEGIN;
SELECT COUNT(*) FROM orders WHERE user_id = 1;  -- Returns 5

-- Transaction B (concurrent)
INSERT INTO orders (user_id, total) VALUES (1, 100);
COMMIT;

-- Transaction A
SELECT COUNT(*) FROM orders WHERE user_id = 1;  -- Returns 6 (phantom row!)
```

### 1. READ_UNCOMMITTED

**Level:** Lowest isolation (fastest, most dangerous)

**Allows:**
- ✅ Dirty reads
- ✅ Non-repeatable reads
- ✅ Phantom reads

```java
@Transactional(isolation = Isolation.READ_UNCOMMITTED)
public BigDecimal getAccountBalance(Long accountId) {
    // Can read uncommitted changes from other transactions
    Account account = accountRepository.findById(accountId).orElseThrow();
    return account.getBalance();
}
```

**Example Problem:**

```java
// Transaction A
@Transactional
public void transferMoney(Long fromId, Long toId, BigDecimal amount) {
    Account from = accountRepository.findById(fromId).orElseThrow();
    from.setBalance(from.getBalance().subtract(amount));  // Debit
    accountRepository.save(from);

    // NOT YET COMMITTED
    // Crash here → rollback
}

// Transaction B (concurrent)
@Transactional(isolation = Isolation.READ_UNCOMMITTED)
public BigDecimal getTotalBalance(Long userId) {
    // Reads uncommitted balance from Transaction A
    // Returns incorrect total if Transaction A rollback
    return accountRepository.findByUserId(userId)
        .stream()
        .map(Account::getBalance)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
}
```

**Use cases:**
- ⚠️ Rarely used
- ⚠️ Reporting where approximate data OK
- ⚠️ Performance critical read-only operations
- ❌ NEVER for financial/critical data

**⚠️ Warning:** Hầu hết databases không support hoặc treat như READ_COMMITTED

### 2. READ_COMMITTED (Default cho PostgreSQL, Oracle)

**Level:** Prevent dirty reads only

**Allows:**
- ❌ NO Dirty reads
- ✅ Non-repeatable reads
- ✅ Phantom reads

```java
@Transactional(isolation = Isolation.READ_COMMITTED)
public void processOrder(Order order) {
    // Only reads committed data
    // But data can change during transaction
    orderRepository.save(order);

    Account account = accountRepository.findById(order.getAccountId()).orElseThrow();
    // Account balance might change by other transaction here

    account.setBalance(account.getBalance().subtract(order.getTotal()));
}
```

**Example: Non-Repeatable Read**

```java
@Transactional(isolation = Isolation.READ_COMMITTED)
public void checkAndDebit(Long accountId, BigDecimal amount) {
    // Read 1
    Account account = accountRepository.findById(accountId).orElseThrow();
    BigDecimal balance1 = account.getBalance();  // 500

    if (balance1.compareTo(amount) >= 0) {
        // Other transaction changes balance here (500 → 100)

        // Read 2
        account = accountRepository.findById(accountId).orElseThrow();
        BigDecimal balance2 = account.getBalance();  // 100 (changed!)

        account.setBalance(balance2.subtract(amount));  // Might go negative!
        accountRepository.save(account);
    }
}
```

**Use cases:**
- ✅ Default choice for most applications
- ✅ Good balance between performance and consistency
- ✅ Web applications without strict consistency requirements

### 3. REPEATABLE_READ (Default cho MySQL/InnoDB)

**Level:** Prevent dirty reads and non-repeatable reads

**Allows:**
- ❌ NO Dirty reads
- ❌ NO Non-repeatable reads
- ✅ Phantom reads (depending on database)

```java
@Transactional(isolation = Isolation.REPEATABLE_READ)
public void processOrder(Order order) {
    // Same row always returns same data during transaction
    Account account = accountRepository.findById(order.getAccountId()).orElseThrow();
    BigDecimal balance1 = account.getBalance();

    // Do some processing...

    // Re-read account
    account = accountRepository.findById(order.getAccountId()).orElseThrow();
    BigDecimal balance2 = account.getBalance();

    // balance1 == balance2 (guaranteed)
}
```

**Example: Preventing Non-Repeatable Read**

```java
@Transactional(isolation = Isolation.REPEATABLE_READ)
public void processPayment(Long orderId, Long accountId) {
    // Read account balance
    Account account = accountRepository.findById(accountId).orElseThrow();
    BigDecimal initialBalance = account.getBalance();  // 1000

    // Other transaction tries to update balance
    // Will BLOCK until this transaction commits/rollback

    // Process order
    Order order = orderRepository.findById(orderId).orElseThrow();

    // Re-read account
    account = accountRepository.findById(accountId).orElseThrow();
    BigDecimal currentBalance = account.getBalance();  // Still 1000

    // Guaranteed: initialBalance == currentBalance
    account.setBalance(currentBalance.subtract(order.getTotal()));
    accountRepository.save(account);
}
```

**Example: Phantom Read Still Possible**

```java
@Transactional(isolation = Isolation.REPEATABLE_READ)
public BigDecimal calculateTotalSales(Long userId) {
    // Query 1
    List<Order> orders1 = orderRepository.findByUserId(userId);
    int count1 = orders1.size();  // 10 orders

    // Other transaction inserts new order
    // (might be visible depending on database implementation)

    // Query 2
    List<Order> orders2 = orderRepository.findByUserId(userId);
    int count2 = orders2.size();  // 11 orders (phantom row!)

    // count1 != count2 (possible phantom read)
}
```

**⚠️ Database-Specific Behavior:**

**MySQL/InnoDB:**
```java
@Transactional(isolation = Isolation.REPEATABLE_READ)
public void test() {
    // MySQL uses gap locking → prevents phantom reads
    // Acts similar to SERIALIZABLE for range queries
}
```

**PostgreSQL:**
```java
@Transactional(isolation = Isolation.REPEATABLE_READ)
public void test() {
    // PostgreSQL: Phantom reads possible
    // Use SERIALIZABLE for full protection
}
```

**Use cases:**
- ✅ Financial applications (prevent balance changes during transaction)
- ✅ Inventory management
- ✅ Calculation based on consistent data
- ✅ Default for MySQL (good choice)

### 4. SERIALIZABLE

**Level:** Highest isolation (slowest, safest)

**Allows:**
- ❌ NO Dirty reads
- ❌ NO Non-repeatable reads
- ❌ NO Phantom reads

```java
@Transactional(isolation = Isolation.SERIALIZABLE)
public void processOrder(Order order) {
    // Complete isolation
    // Như thể transactions execute sequentially
    // No concurrent access issues

    List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
    // Other transactions CANNOT insert/update/delete order_items
    // until this transaction completes
}
```

**Example: Preventing All Issues**

```java
@Transactional(isolation = Isolation.SERIALIZABLE)
public void reserveInventory(Long productId, int quantity) {
    // Read product
    Product product = productRepository.findById(productId).orElseThrow();
    int available = product.getQuantity();  // 100

    // Other transactions BLOCKED from:
    // - Updating this product
    // - Reading this product (for update)
    // - Inserting related records

    if (available >= quantity) {
        product.setQuantity(available - quantity);
        productRepository.save(product);

        // Create reservation
        Reservation reservation = new Reservation(productId, quantity);
        reservationRepository.save(reservation);
    }

    // No phantoms, no non-repeatable reads, no dirty reads
}
```

**Performance Cost:**

```java
// Transaction A
@Transactional(isolation = Isolation.SERIALIZABLE)
public void processOrder1(Long productId) {
    Product p = productRepository.findById(productId).orElseThrow();
    // Holds exclusive locks
    Thread.sleep(5000);  // Simulate slow processing
}

// Transaction B (concurrent)
@Transactional(isolation = Isolation.SERIALIZABLE)
public void processOrder2(Long productId) {
    // BLOCKED waiting for Transaction A
    Product p = productRepository.findById(productId).orElseThrow();
    // Might timeout or deadlock
}
```

**Deadlock Risk:**

```java
// Transaction A
@Transactional(isolation = Isolation.SERIALIZABLE)
public void transfer1() {
    accountRepository.findById(1L);  // Lock account 1
    Thread.sleep(100);
    accountRepository.findById(2L);  // Wait for account 2 (locked by B)
}

// Transaction B
@Transactional(isolation = Isolation.SERIALIZABLE)
public void transfer2() {
    accountRepository.findById(2L);  // Lock account 2
    Thread.sleep(100);
    accountRepository.findById(1L);  // Wait for account 1 (locked by A)
}

// Result: DEADLOCK
// Database will abort one transaction
```

**Use cases:**
- ✅ Critical financial transactions
- ✅ Money transfers
- ✅ Stock trading
- ✅ Inventory with strict accuracy requirements
- ⚠️ Only when absolutely necessary (performance cost)

### Isolation Level Comparison

| Isolation Level | Dirty Read | Non-Repeatable Read | Phantom Read | Performance | Use Case |
|----------------|------------|-------------------|--------------|-------------|----------|
| **READ_UNCOMMITTED** | ✅ Allowed | ✅ Allowed | ✅ Allowed | Fastest ⚡⚡⚡ | Approximate reporting |
| **READ_COMMITTED** | ❌ Prevented | ✅ Allowed | ✅ Allowed | Fast ⚡⚡ | Web apps (default) |
| **REPEATABLE_READ** | ❌ Prevented | ❌ Prevented | ⚠️ Maybe | Medium ⚡ | Financial apps |
| **SERIALIZABLE** | ❌ Prevented | ❌ Prevented | ❌ Prevented | Slow 🐌 | Critical transactions |

### Database Default Isolation Levels

| Database | Default Isolation |
|----------|------------------|
| PostgreSQL | `READ_COMMITTED` |
| MySQL/InnoDB | `REPEATABLE_READ` |
| Oracle | `READ_COMMITTED` |
| SQL Server | `READ_COMMITTED` |
| H2 | `READ_COMMITTED` |

### Choosing Isolation Level

```java
// Low concurrency, approximate data OK
@Transactional(
    isolation = Isolation.READ_COMMITTED,
    readOnly = true
)
public List<Order> getRecentOrders() {
    return orderRepository.findTop100ByOrderByCreatedAtDesc();
}

// Medium concurrency, balance consistency
@Transactional(isolation = Isolation.READ_COMMITTED)
public void createOrder(Order order) {
    orderRepository.save(order);
}

// High accuracy required
@Transactional(isolation = Isolation.REPEATABLE_READ)
public void calculateBalance(Long accountId) {
    Account account = accountRepository.findById(accountId).orElseThrow();
    List<Transaction> txs = transactionRepository.findByAccountId(accountId);
    // Balance calculation with consistent data
}

// Critical financial operation
@Transactional(
    isolation = Isolation.SERIALIZABLE,
    timeout = 30
)
public void transferMoney(Long fromId, Long toId, BigDecimal amount) {
    // Complete isolation, timeout to prevent deadlocks
    Account from = accountRepository.findById(fromId).orElseThrow();
    Account to = accountRepository.findById(toId).orElseThrow();

    from.setBalance(from.getBalance().subtract(amount));
    to.setBalance(to.getBalance().add(amount));

    accountRepository.save(from);
    accountRepository.save(to);
}
```

---

## Best Practices

### 1. Use @Transactional at Service Layer

```java
// ✅ GOOD: Service layer
@Service
public class OrderService {

    @Transactional
    public void createOrder(Order order) {
        orderRepository.save(order);
        inventoryService.updateStock(order);
    }
}

// ❌ BAD: Controller layer
@RestController
public class OrderController {

    @Transactional  // ❌ Avoid
    @PostMapping("/orders")
    public ResponseEntity<Order> createOrder(@RequestBody Order order) {
        orderService.createOrder(order);
        return ResponseEntity.ok(order);
    }
}

// ❌ BAD: Repository layer (already transactional)
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    @Transactional  // ❌ Unnecessary
    Order save(Order order);
}
```

**Why:**
- Service layer = business logic boundary
- Controllers handle HTTP, không business logic
- Repositories already wrapped by Spring Data

### 2. Keep Transactions Short

```java
// ❌ BAD: Long transaction
@Transactional
public void processOrder(Order order) {
    orderRepository.save(order);

    // External API call (slow!)
    externalService.notifyWarehouse(order);  // 5 seconds

    // Email sending (slow!)
    emailService.sendConfirmation(order);  // 3 seconds

    // Hold transaction for 8+ seconds → bad!
}

// ✅ GOOD: Short transaction
@Transactional
public void processOrder(Order order) {
    orderRepository.save(order);
    // Transaction ends here
}

@Async
public void sendNotifications(Order order) {
    // Outside transaction
    externalService.notifyWarehouse(order);
    emailService.sendConfirmation(order);
}
```

**Why:**
- Long transactions hold database locks
- Block other transactions
- Increase deadlock risk
- Reduce throughput

### 3. Use readOnly for Query Methods

```java
// ✅ GOOD
@Transactional(readOnly = true)
public List<Order> findOrdersByUserId(Long userId) {
    return orderRepository.findByUserId(userId);
}

// ❌ BAD
@Transactional  // Unnecessary write lock
public List<Order> findOrdersByUserId(Long userId) {
    return orderRepository.findByUserId(userId);
}
```

**Benefits:**
- Database can optimize read-only queries
- No dirty checking overhead
- Clear intent

### 4. Specify Rollback Rules for Checked Exceptions

```java
// ✅ GOOD: Explicit rollback for checked exceptions
@Transactional(rollbackFor = Exception.class)
public void createUser(User user) throws UserExistsException {
    if (userRepository.existsByEmail(user.getEmail())) {
        throw new UserExistsException();  // ✅ Rollback
    }
    userRepository.save(user);
}

// ❌ BAD: Default behavior (no rollback for checked exceptions)
@Transactional
public void createUser(User user) throws UserExistsException {
    if (userRepository.existsByEmail(user.getEmail())) {
        throw new UserExistsException();  // ❌ NO Rollback (committed!)
    }
    userRepository.save(user);
}
```

### 5. Avoid Transaction for Single Read Operation

```java
// ❌ BAD: Unnecessary transaction
@Transactional
public User findById(Long id) {
    return userRepository.findById(id).orElseThrow();
}

// ✅ BETTER: No transaction (Spring Data handles it)
public User findById(Long id) {
    return userRepository.findById(id).orElseThrow();
}

// ✅ OR: Use readOnly if you want explicit transaction
@Transactional(readOnly = true)
public User findById(Long id) {
    return userRepository.findById(id).orElseThrow();
}
```

### 6. Use Appropriate Propagation

```java
// ✅ GOOD: Audit always persists
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void logAudit(String action) {
    auditRepository.save(new AuditLog(action));
}

// ✅ GOOD: Enforce transaction
@Transactional(propagation = Propagation.MANDATORY)
public void addOrderItem(OrderItem item) {
    orderItemRepository.save(item);
}

// ✅ GOOD: No transaction needed
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public void callExternalAPI() {
    externalClient.notify();
}
```

### 7. Use Appropriate Isolation Level

```java
// ✅ GOOD: Default for most cases
@Transactional(isolation = Isolation.READ_COMMITTED)
public void createOrder(Order order) {
    orderRepository.save(order);
}

// ✅ GOOD: Consistent read for calculations
@Transactional(isolation = Isolation.REPEATABLE_READ)
public BigDecimal calculateTotal(Long orderId) {
    Order order = orderRepository.findById(orderId).orElseThrow();
    List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
    // Consistent data during calculation
    return items.stream()
        .map(OrderItem::getTotal)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
}

// ✅ GOOD: Critical operation
@Transactional(
    isolation = Isolation.SERIALIZABLE,
    timeout = 30
)
public void transferMoney(Long fromId, Long toId, BigDecimal amount) {
    // Complete isolation for money transfer
}
```

### 8. Set Timeout for Long Operations

```java
@Transactional(timeout = 30)  // 30 seconds
public void processBatch(List<Order> orders) {
    orders.forEach(order -> {
        orderRepository.save(order);
    });
}
```

### 9. Avoid Catching Exceptions Without Rethrowing

```java
// ❌ BAD: Swallow exception
@Transactional
public void createOrder(Order order) {
    try {
        orderRepository.save(order);
        throw new RuntimeException("Error");
    } catch (Exception e) {
        log.error("Error", e);
        // ⚠️ Transaction still commits! (no exception propagated)
    }
}

// ✅ GOOD: Rethrow or mark rollback
@Transactional
public void createOrder(Order order) {
    try {
        orderRepository.save(order);
        throw new RuntimeException("Error");
    } catch (Exception e) {
        log.error("Error", e);
        throw e;  // ✅ Rethrow → rollback
    }
}

// ✅ GOOD: Manual rollback
@Transactional
public void createOrder(Order order) {
    try {
        orderRepository.save(order);
        throw new RuntimeException("Error");
    } catch (Exception e) {
        log.error("Error", e);
        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        // ✅ Marked for rollback
    }
}
```

### 10. Be Aware of Proxy Limitations

```java
// ❌ BAD: Self-invocation (transaction không work)
@Service
public class OrderService {

    public void createOrder(Order order) {
        // Calls transactional method on same instance
        this.saveOrder(order);  // ❌ Transaction không active!
    }

    @Transactional
    private void saveOrder(Order order) {
        orderRepository.save(order);
    }
}

// ✅ GOOD: Inject self or use separate bean
@Service
public class OrderService {

    @Autowired
    private OrderService self;  // Self-injection

    public void createOrder(Order order) {
        self.saveOrder(order);  // ✅ Transaction works
    }

    @Transactional
    public void saveOrder(Order order) {
        orderRepository.save(order);
    }
}
```

---

## Common Pitfalls

### 1. Self-Invocation Problem

```java
@Service
public class UserService {

    // ❌ Problem: this.createUser() bypasses proxy
    public void registerUser(User user) {
        validateUser(user);
        this.createUser(user);  // ❌ Transaction NOT applied
    }

    @Transactional
    public void createUser(User user) {
        userRepository.save(user);
    }
}

// ✅ Solution 1: Separate bean
@Service
public class UserService {

    @Autowired
    private UserTransactionService transactionService;

    public void registerUser(User user) {
        validateUser(user);
        transactionService.createUser(user);  // ✅ Works
    }
}

@Service
class UserTransactionService {

    @Transactional
    public void createUser(User user) {
        userRepository.save(user);
    }
}

// ✅ Solution 2: Self-injection
@Service
public class UserService {

    @Autowired
    private UserService self;

    public void registerUser(User user) {
        validateUser(user);
        self.createUser(user);  // ✅ Works
    }

    @Transactional
    public void createUser(User user) {
        userRepository.save(user);
    }
}
```

### 2. Private Method Problem

```java
@Service
public class OrderService {

    // ❌ Private methods CANNOT be transactional
    @Transactional
    private void createOrder(Order order) {  // ❌ Ignored
        orderRepository.save(order);
    }

    // ✅ Must be public (or protected/package-private)
    @Transactional
    public void createOrder(Order order) {  // ✅ Works
        orderRepository.save(order);
    }
}
```

### 3. Checked Exception Not Rolling Back

```java
@Service
public class UserService {

    // ❌ Checked exceptions don't rollback by default
    @Transactional
    public void createUser(User user) throws IOException {
        userRepository.save(user);
        if (user.getEmail() == null) {
            throw new IOException("Invalid email");  // ❌ NO rollback
        }
    }

    // ✅ Solution: Use rollbackFor
    @Transactional(rollbackFor = Exception.class)
    public void createUser(User user) throws IOException {
        userRepository.save(user);
        if (user.getEmail() == null) {
            throw new IOException("Invalid email");  // ✅ Rollback
        }
    }
}
```

### 4. Transaction Too Large

```java
// ❌ BAD: Long-running transaction
@Transactional
public void processOrders(List<Long> orderIds) {
    for (Long orderId : orderIds) {  // 10,000 orders
        Order order = orderRepository.findById(orderId).orElseThrow();

        // Process order (slow)
        processPayment(order);  // External API call
        updateInventory(order);
        sendEmail(order);  // Email service

        orderRepository.save(order);
    }
    // Transaction held for very long time!
}

// ✅ GOOD: Smaller transactions
@Transactional
public void processOrders(List<Long> orderIds) {
    for (Long orderId : orderIds) {
        processOrder(orderId);  // Each order in separate transaction
    }
}

@Transactional(propagation = Propagation.REQUIRES_NEW)
public void processOrder(Long orderId) {
    Order order = orderRepository.findById(orderId).orElseThrow();
    // Process one order
    orderRepository.save(order);
}

@Async
public void sendNotifications(Long orderId) {
    // Outside transaction
    Order order = orderRepository.findById(orderId).orElseThrow();
    sendEmail(order);
}
```

### 5. LazyInitializationException

```java
@Service
public class OrderService {

    // ❌ Problem: Lazy loading outside transaction
    public void printOrderItems(Long orderId) {
        Order order = findOrder(orderId);  // Transaction ends here

        // ❌ LazyInitializationException
        order.getItems().forEach(item -> {
            System.out.println(item.getName());
        });
    }

    @Transactional(readOnly = true)
    public Order findOrder(Long orderId) {
        return orderRepository.findById(orderId).orElseThrow();
    }

    // ✅ Solution 1: Fetch within transaction
    @Transactional(readOnly = true)
    public void printOrderItems(Long orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow();
        order.getItems().forEach(item -> {  // ✅ Within transaction
            System.out.println(item.getName());
        });
    }

    // ✅ Solution 2: Eager fetch
    @Transactional(readOnly = true)
    public Order findOrderWithItems(Long orderId) {
        return orderRepository.findByIdWithItems(orderId);  // JOIN FETCH
    }
}
```

### 6. Wrong Exception Handling

```java
// ❌ BAD: Catch without rethrow
@Transactional
public void createOrder(Order order) {
    try {
        orderRepository.save(order);
        processPayment(order);  // Might throw exception
    } catch (PaymentException e) {
        log.error("Payment failed", e);
        // ⚠️ Transaction still commits!
    }
}

// ✅ GOOD: Rethrow or mark rollback
@Transactional
public void createOrder(Order order) {
    try {
        orderRepository.save(order);
        processPayment(order);
    } catch (PaymentException e) {
        log.error("Payment failed", e);
        throw e;  // ✅ Rollback
    }
}

// ✅ GOOD: Manual rollback
@Transactional
public void createOrder(Order order) {
    try {
        orderRepository.save(order);
        processPayment(order);
    } catch (PaymentException e) {
        log.error("Payment failed", e);
        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        // Handle gracefully without throwing
    }
}
```

### 7. Multiple Transaction Managers

```java
// Problem: Multiple databases
@Configuration
public class DatabaseConfig {

    @Bean
    @Primary
    public PlatformTransactionManager primaryTxManager() {
        return new JpaTransactionManager(primaryEntityManagerFactory());
    }

    @Bean
    public PlatformTransactionManager secondaryTxManager() {
        return new JpaTransactionManager(secondaryEntityManagerFactory());
    }
}

// ✅ Solution: Specify transaction manager
@Service
public class UserService {

    @Transactional("primaryTxManager")
    public void createUser(User user) {
        primaryUserRepository.save(user);
    }

    @Transactional("secondaryTxManager")
    public void createAuditLog(AuditLog log) {
        secondaryAuditRepository.save(log);
    }
}
```

---

## Examples từ Project

### Current Usage trong Codebase

```java
// src/main/java/com/khoa/spring/playground/service/UserDeletionService.java

@Service
@Slf4j
@RequiredArgsConstructor
public class UserDeletionService {

    /**
     * Asynchronous user deletion using database CASCADE DELETE
     *
     * Propagation: REQUIRED (default)
     * Isolation: READ_COMMITTED (default từ PostgreSQL)
     */
    @Async("deleteUserExecutor")
    @Transactional
    public CompletableFuture<Void> deleteUserAsync(String jobId) {
        DeleteJob job = deleteJobRepository.findById(jobId)
            .orElseThrow(() -> new RuntimeException("Job not found: " + jobId));

        try {
            job.setStatus(DeleteJobStatus.IN_PROGRESS);
            deleteJobRepository.save(job);

            Long userId = job.getUserId();

            // Simple delete - Database CASCADE handles all posts automatically
            userRepository.deleteById(userId);

            // Clear cache
            if (cacheManager.getCache("users") != null) {
                cacheManager.getCache("users").evict(userId);
            }

            job.setStatus(DeleteJobStatus.COMPLETED);
            job.setCompletedAt(LocalDateTime.now());
            deleteJobRepository.save(job);

        } catch (Exception e) {
            log.error("Failed to delete user", e);

            job.setStatus(DeleteJobStatus.FAILED);
            job.setErrorMessage(e.getMessage());
            job.setCompletedAt(LocalDateTime.now());
            deleteJobRepository.save(job);

            throw new RuntimeException("Delete operation failed", e);
        }

        return CompletableFuture.completedFuture(null);
    }

    /**
     * Synchronous user deletion (for admin/batch operations)
     *
     * Propagation: REQUIRED (default)
     * Isolation: READ_COMMITTED (default)
     */
    @Transactional
    public void deleteUserSync(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new IllegalArgumentException("User not found with id: " + userId);
        }

        log.info("Starting synchronous delete for user: {}", userId);

        // Database CASCADE handles all related entities automatically
        userRepository.deleteById(userId);

        // Clear cache
        if (cacheManager.getCache("users") != null) {
            cacheManager.getCache("users").evict(userId);
        }

        log.info("Synchronous delete completed for user: {}", userId);
    }
}
```

### Improved Examples với Propagation & Isolation

```java
@Service
@RequiredArgsConstructor
public class UserDeletionService {

    /**
     * Schedule async deletion
     * NO @Transactional: Just creates job record
     */
    public String scheduleDelete(Long userId) {
        // Validate user exists
        if (!userRepository.existsById(userId)) {
            throw new IllegalArgumentException("User not found");
        }

        // Create job
        DeleteJob job = new DeleteJob();
        job.setId(UUID.randomUUID().toString());
        job.setUserId(userId);
        job.setStatus(DeleteJobStatus.PENDING);
        deleteJobRepository.save(job);

        // Execute async
        deleteUserAsync(job.getId());

        return job.getId();
    }

    /**
     * Async deletion với proper transaction config
     *
     * - REQUIRES_NEW: Independent transaction (không affect caller)
     * - REPEATABLE_READ: Ensure consistent read during delete
     * - timeout: 120s for large deletes
     */
    @Async("deleteUserExecutor")
    @Transactional(
        propagation = Propagation.REQUIRES_NEW,
        isolation = Isolation.REPEATABLE_READ,
        timeout = 120
    )
    public CompletableFuture<Void> deleteUserAsync(String jobId) {
        DeleteJob job = deleteJobRepository.findById(jobId)
            .orElseThrow(() -> new RuntimeException("Job not found"));

        try {
            // Update job status
            updateJobStatus(jobId, DeleteJobStatus.IN_PROGRESS);

            Long userId = job.getUserId();

            // Audit log (separate transaction - always persists)
            auditService.logUserDeletion(userId);

            // Delete user (DB CASCADE handles children)
            userRepository.deleteById(userId);

            // Clear cache (outside transaction scope)
            evictCaches(userId);

            // Update job as completed
            updateJobStatus(jobId, DeleteJobStatus.COMPLETED);

        } catch (Exception e) {
            log.error("Failed to delete user", e);
            updateJobStatus(jobId, DeleteJobStatus.FAILED);
            throw e;
        }

        return CompletableFuture.completedFuture(null);
    }

    /**
     * Synchronous deletion với strict isolation
     *
     * - SERIALIZABLE: Complete isolation for critical operation
     * - timeout: 60s
     */
    @Transactional(
        isolation = Isolation.SERIALIZABLE,
        timeout = 60
    )
    public void deleteUserSync(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new IllegalArgumentException("User not found");
        }

        log.info("Starting synchronous delete for user: {}", userId);

        // Audit log (separate transaction)
        auditService.logUserDeletion(userId);

        // Delete user
        userRepository.deleteById(userId);

        log.info("Synchronous delete completed for user: {}", userId);
    }

    /**
     * Update job status - separate transaction
     *
     * - REQUIRES_NEW: Always persists (even if parent transaction rollback)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private void updateJobStatus(String jobId, DeleteJobStatus status) {
        DeleteJob job = deleteJobRepository.findById(jobId).orElseThrow();
        job.setStatus(status);
        if (status == DeleteJobStatus.COMPLETED || status == DeleteJobStatus.FAILED) {
            job.setCompletedAt(LocalDateTime.now());
        }
        deleteJobRepository.save(job);
    }

    /**
     * Evict caches - NO transaction needed
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    private void evictCaches(Long userId) {
        if (cacheManager.getCache("users") != null) {
            cacheManager.getCache("users").evict(userId);
        }
        if (cacheManager.getCache("posts") != null) {
            cacheManager.getCache("posts").clear();
        }
    }
}

/**
 * Audit service - always persists logs
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    /**
     * Audit logging - independent transaction
     *
     * - REQUIRES_NEW: Logs persist even if caller rollback
     * - READ_COMMITTED: Default isolation sufficient
     */
    @Transactional(
        propagation = Propagation.REQUIRES_NEW,
        isolation = Isolation.READ_COMMITTED
    )
    public void logUserDeletion(Long userId) {
        AuditLog log = new AuditLog();
        log.setAction("USER_DELETE");
        log.setUserId(userId);
        log.setTimestamp(LocalDateTime.now());
        auditLogRepository.save(log);
    }
}
```

### Example: Money Transfer với SERIALIZABLE

```java
@Service
@RequiredArgsConstructor
public class TransferService {

    /**
     * Money transfer - critical operation
     *
     * - SERIALIZABLE: Complete isolation (no concurrent access)
     * - timeout: 30s (prevent deadlocks)
     * - rollbackFor: All exceptions
     */
    @Transactional(
        isolation = Isolation.SERIALIZABLE,
        timeout = 30,
        rollbackFor = Exception.class
    )
    public void transferMoney(Long fromAccountId, Long toAccountId, BigDecimal amount)
            throws InsufficientFundsException {

        // Get accounts (locks acquired)
        Account fromAccount = accountRepository.findById(fromAccountId)
            .orElseThrow(() -> new AccountNotFoundException("From account not found"));

        Account toAccount = accountRepository.findById(toAccountId)
            .orElseThrow(() -> new AccountNotFoundException("To account not found"));

        // Validate balance
        if (fromAccount.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException("Insufficient funds");
        }

        // Audit log (separate transaction - always persists)
        auditService.logTransfer(fromAccountId, toAccountId, amount, "INITIATED");

        // Execute transfer
        fromAccount.setBalance(fromAccount.getBalance().subtract(amount));
        toAccount.setBalance(toAccount.getBalance().add(amount));

        accountRepository.save(fromAccount);
        accountRepository.save(toAccount);

        // Record transaction
        Transaction tx = new Transaction();
        tx.setFromAccountId(fromAccountId);
        tx.setToAccountId(toAccountId);
        tx.setAmount(amount);
        tx.setStatus(TransactionStatus.COMPLETED);
        transactionRepository.save(tx);

        // Audit log success (separate transaction)
        auditService.logTransfer(fromAccountId, toAccountId, amount, "COMPLETED");
    }
}
```

---

## Testing Strategies

### 1. Test Transaction Behavior

```java
@SpringBootTest
@Transactional
public class TransactionTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Test
    public void testTransactionCommit() {
        Order order = new Order();
        order.setTotal(BigDecimal.valueOf(100));

        orderService.createOrder(order);

        // Verify saved
        assertThat(orderRepository.findById(order.getId())).isPresent();
    }

    @Test
    public void testTransactionRollback() {
        Order order = new Order();
        order.setTotal(BigDecimal.valueOf(-100));  // Invalid

        assertThrows(IllegalArgumentException.class, () -> {
            orderService.createOrder(order);
        });

        // Verify NOT saved (rollback)
        assertThat(orderRepository.count()).isZero();
    }
}
```

### 2. Test Propagation

```java
@SpringBootTest
public class PropagationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private AuditService auditService;

    @Test
    @Transactional
    public void testRequiresNew_AuditPersistsEvenOnRollback() {
        Order order = new Order();
        order.setTotal(BigDecimal.valueOf(100));

        try {
            orderService.createOrderWithAudit(order);  // Will fail
        } catch (Exception e) {
            // Expected
        }

        // Order NOT saved (rollback)
        assertThat(orderRepository.count()).isZero();

        // Audit log SAVED (REQUIRES_NEW)
        assertThat(auditLogRepository.count()).isGreaterThan(0);
    }
}

@Service
class OrderService {

    @Transactional
    public void createOrderWithAudit(Order order) {
        auditService.log("Order creation started");  // REQUIRES_NEW

        orderRepository.save(order);

        throw new RuntimeException("Rollback!");  // Main tx rollback
    }
}

@Service
class AuditService {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String message) {
        auditLogRepository.save(new AuditLog(message));
    }
}
```

### 3. Test Isolation Levels

```java
@SpringBootTest
public class IsolationTest {

    @Test
    public void testReadCommitted_NonRepeatableRead() throws Exception {
        // Setup
        Account account = new Account();
        account.setBalance(BigDecimal.valueOf(1000));
        accountRepository.save(account);

        CountDownLatch latch1 = new CountDownLatch(1);
        CountDownLatch latch2 = new CountDownLatch(1);

        // Transaction 1: Read twice
        Thread t1 = new Thread(() -> {
            transactionTemplate.execute(status -> {
                // Read 1
                BigDecimal balance1 = accountRepository.findById(account.getId())
                    .orElseThrow().getBalance();

                latch1.countDown();  // Signal to T2

                try {
                    latch2.await();  // Wait for T2
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                // Read 2
                BigDecimal balance2 = accountRepository.findById(account.getId())
                    .orElseThrow().getBalance();

                // With READ_COMMITTED: balance1 != balance2
                assertThat(balance1).isNotEqualTo(balance2);

                return null;
            });
        });

        // Transaction 2: Update
        Thread t2 = new Thread(() -> {
            try {
                latch1.await();  // Wait for T1's first read
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            transactionTemplate.execute(status -> {
                Account acc = accountRepository.findById(account.getId()).orElseThrow();
                acc.setBalance(BigDecimal.valueOf(2000));
                accountRepository.save(acc);
                return null;
            });

            latch2.countDown();  // Signal to T1
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();
    }
}
```

### 4. Test Timeout

```java
@SpringBootTest
public class TimeoutTest {

    @Test
    public void testTransactionTimeout() {
        assertThrows(TransactionTimedOutException.class, () -> {
            orderService.longRunningOperation();
        });
    }
}

@Service
class OrderService {

    @Transactional(timeout = 1)  // 1 second
    public void longRunningOperation() {
        try {
            Thread.sleep(2000);  // 2 seconds (exceeds timeout)
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
```

---

## References & Further Reading

### Official Documentation

1. **Spring Transaction Management**: https://docs.spring.io/spring-framework/docs/current/reference/html/data-access.html#transaction
2. **Spring @Transactional**: https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/annotation/Transactional.html
3. **JPA Specification**: https://jakarta.ee/specifications/persistence/3.1/

### Database Documentation

1. **PostgreSQL Transaction Isolation**: https://www.postgresql.org/docs/current/transaction-iso.html
2. **MySQL InnoDB Transactions**: https://dev.mysql.com/doc/refman/8.0/en/innodb-transaction-isolation-levels.html

### Best Practices

1. **Vlad Mihalcea - Spring Transaction Management**: https://vladmihalcea.com/spring-transaction-best-practices/
2. **Baeldung Spring Transactions**: https://www.baeldung.com/transaction-configuration-with-jpa-and-spring

### Project Documentation

1. **JPA vs Database CASCADE**: `docs/jpa-vs-database-cascade.md`
2. **Delete Performance Optimization**: `docs/delete-performance-optimization.md`

---

## Summary

### Key Takeaways

1. **@Transactional Basics**
   - Use at service layer
   - Keep transactions short
   - Use readOnly for queries
   - Specify rollback rules

2. **Propagation**
   - `REQUIRED` (default): Join or create
   - `REQUIRES_NEW`: Always new (audit logs)
   - `MANDATORY`: Must have transaction
   - `NESTED`: Savepoints (optional operations)

3. **Isolation**
   - `READ_COMMITTED` (default): Good balance
   - `REPEATABLE_READ`: Consistent reads
   - `SERIALIZABLE`: Complete isolation (critical ops)

4. **Best Practices**
   - Service layer transactions
   - Short transaction scope
   - Explicit rollback rules
   - Appropriate propagation
   - Correct isolation level

5. **Common Pitfalls**
   - Self-invocation
   - Private methods
   - Checked exceptions
   - Large transactions
   - Wrong exception handling

---

**Document Created:** 2025-11-19
**Project:** spring-playground
**Related Docs:**
- `docs/jpa-vs-database-cascade.md`
- `docs/delete-performance-optimization.md`
