package com.khoa.spring.playground.controller;

import com.khoa.spring.playground.dto.DeleteJobResponse;
import com.khoa.spring.playground.dto.DeleteJobStatusResponse;
import com.khoa.spring.playground.entity.DeleteJob;
import com.khoa.spring.playground.entity.User;
import com.khoa.spring.playground.repository.UserRepository;
import com.khoa.spring.playground.service.UserDeletionService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final UserDeletionService deletionService;

    @GetMapping
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(userRepository.findAll());
    }

    @GetMapping("/{id}")
    @Cacheable(value = "users", key = "#id")
    public ResponseEntity<User> getUserById(@PathVariable Long id) {
        return userRepository.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/username/{username}")
    @Cacheable(value = "users", key = "#username")
    public ResponseEntity<User> getUserByUsername(@PathVariable String username) {
        return userRepository.findByUsername(username)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @CacheEvict(value = "users", allEntries = true)
    public ResponseEntity<User> createUser(@RequestBody User user) {
        if (userRepository.existsByUsername(user.getUsername())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        User savedUser = userRepository.save(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedUser);
    }

    @PutMapping("/{id}")
    @CacheEvict(value = "users", allEntries = true)
    public ResponseEntity<User> updateUser(@PathVariable Long id, @RequestBody User user) {
        return userRepository.findById(id)
            .map(existingUser -> {
                existingUser.setUsername(user.getUsername());
                existingUser.setEmail(user.getEmail());
                return ResponseEntity.ok(userRepository.save(existingUser));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Delete user - Hybrid approach (supports both sync and async modes)
     *
     * Default mode: async (non-blocking, returns job ID)
     * - Response: 202 Accepted with job ID
     * - User experience: Immediate response, track progress via job ID
     * - Use case: User-facing operations, high-traffic scenarios
     *
     * Sync mode: ?mode=sync (blocking, waits for completion)
     * - Response: 204 No Content after deletion completes
     * - User experience: Waits 10-30 seconds for large datasets
     * - Use case: Admin operations, batch processing
     *
     * Performance (1M posts):
     * - Async: API response < 1 second, actual delete ~10-30 seconds
     * - Sync: API response ~10-30 seconds
     *
     * @param id User ID to delete
     * @param mode "async" (default) or "sync"
     * @return DeleteJobResponse (async) or Void (sync)
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUser(
            @PathVariable Long id,
            @RequestParam(defaultValue = "async") String mode) {

        if (!userRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        if ("sync".equalsIgnoreCase(mode)) {
            // Synchronous mode - blocks until complete
            // Database CASCADE handles posts automatically
            deletionService.deleteUserSync(id);
            return ResponseEntity.noContent().build();
        } else {
            // Asynchronous mode (default) - returns immediately
            try {
                String jobId = deletionService.scheduleDelete(id);
                return ResponseEntity.accepted()
                    .body(new DeleteJobResponse(jobId, "PENDING"));
            } catch (IllegalStateException e) {
                // Delete already in progress
                return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new DeleteJobResponse(null, "ERROR", e.getMessage()));
            }
        }
    }

    /**
     * Get delete job status
     * Use this endpoint to track progress of async delete operations
     *
     * @param jobId Job ID returned from DELETE /api/users/{id}
     * @return Job status with progress information
     */
    @GetMapping("/delete-jobs/{jobId}")
    public ResponseEntity<DeleteJobStatusResponse> getDeleteJobStatus(
            @PathVariable String jobId) {

        try {
            DeleteJob job = deletionService.getJobStatus(jobId);

            DeleteJobStatusResponse response = new DeleteJobStatusResponse(
                job.getId(),
                job.getStatus().name(),
                job.getProgress(),
                job.getProcessedRecords(),
                job.getTotalRecords(),
                job.getErrorMessage()
            );

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
