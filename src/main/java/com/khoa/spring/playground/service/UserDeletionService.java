package com.khoa.spring.playground.service;

import com.khoa.spring.playground.entity.DeleteJob;
import com.khoa.spring.playground.entity.DeleteJobStatus;
import com.khoa.spring.playground.repository.DeleteJobRepository;
import com.khoa.spring.playground.repository.FavoriteRepository;
import com.khoa.spring.playground.repository.PostRepository;
import com.khoa.spring.playground.repository.ResourceRepository;
import com.khoa.spring.playground.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service for handling user deletion operations
 * Supports both synchronous and asynchronous deletion modes
 * Uses database-level CASCADE DELETE for optimal performance
 *
 * Handles deletion of all user-related entities:
 * - Posts
 * - Favorites
 * - Resources (including nested ResourceDetails)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UserDeletionService {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final FavoriteRepository favoriteRepository;
    private final ResourceRepository resourceRepository;
    private final DeleteJobRepository deleteJobRepository;
    private final CacheManager cacheManager;

    /**
     * Schedule asynchronous user deletion
     * Returns job ID immediately for progress tracking
     *
     * @param userId ID of user to delete
     * @return Job ID for tracking
     * @throws IllegalStateException if user doesn't exist or has pending delete job
     */
    public String scheduleDelete(Long userId) {
        // Validate user exists
        if (!userRepository.existsById(userId)) {
            throw new IllegalArgumentException("User not found with id: " + userId);
        }

        // Check for existing pending/in-progress jobs for this user
        long existingJobs = deleteJobRepository.countByUserIdAndStatusIn(
            userId,
            Arrays.asList(DeleteJobStatus.PENDING, DeleteJobStatus.IN_PROGRESS)
        );

        if (existingJobs > 0) {
            throw new IllegalStateException(
                "User deletion already in progress for user id: " + userId
            );
        }

        // Count total records for progress tracking (all entities that will be deleted)
        long totalPosts = postRepository.countByUserId(userId);
        long totalFavorites = favoriteRepository.countByUserId(userId);
        long totalResources = resourceRepository.countByUserId(userId);
        long totalResourceDetails = resourceRepository.countResourceDetailsByUserId(userId);
        long totalRecords = totalPosts + totalFavorites + totalResources + totalResourceDetails;

        // Create job tracking record
        DeleteJob job = new DeleteJob();
        job.setId(UUID.randomUUID().toString());
        job.setUserId(userId);
        job.setStatus(DeleteJobStatus.PENDING);
        job.setTotalRecords(totalRecords);
        job.setProcessedRecords(0L);
        // Note: createdAt is automatically set by @CreationTimestamp in BaseEntity
        deleteJobRepository.save(job);

        log.info("Delete job scheduled - JobID: {}, UserID: {}, Total records: {} (posts: {}, favorites: {}, resources: {}, resource_details: {})",
            job.getId(), userId, totalRecords, totalPosts, totalFavorites, totalResources, totalResourceDetails);

        // Execute async deletion
        deleteUserAsync(job.getId());

        return job.getId();
    }

    /**
     * Asynchronous user deletion using database CASCADE DELETE
     * Runs in background thread pool, does not block API response
     *
     * Performance: ~10-30 seconds for 1M posts with DB CASCADE
     *
     * @param jobId Job ID to track progress
     * @return CompletableFuture for async execution
     */
    @Async("deleteUserExecutor")
    @Transactional
    public CompletableFuture<Void> deleteUserAsync(String jobId) {
        DeleteJob job = deleteJobRepository.findById(jobId)
            .orElseThrow(() -> new RuntimeException("Job not found: " + jobId));

        try {
            log.info("Starting async delete - JobID: {}, UserID: {}",
                jobId, job.getUserId());

            // Update job status to IN_PROGRESS
            job.setStatus(DeleteJobStatus.IN_PROGRESS);
            deleteJobRepository.save(job);

            Long userId = job.getUserId();

            // Simple delete - Database CASCADE handles all posts automatically
            // This is the key optimization: DB engine handles bulk delete efficiently
            userRepository.deleteById(userId);

            log.info("User deleted successfully - JobID: {}, UserID: {}", jobId, userId);

            // Update progress (DB CASCADE completed all posts)
            job.setProcessedRecords(job.getTotalRecords());

            // Clear cache for all affected entities
            // Note: Clearing entire caches because we don't know all record IDs
            // Alternative: Track IDs before delete for selective eviction
            if (cacheManager.getCache("users") != null) {
                cacheManager.getCache("users").evict(userId);
            }
            if (cacheManager.getCache("posts") != null) {
                cacheManager.getCache("posts").clear();
            }
            if (cacheManager.getCache("favorites") != null) {
                cacheManager.getCache("favorites").clear();
            }
            if (cacheManager.getCache("resources") != null) {
                cacheManager.getCache("resources").clear();
            }

            // Mark job as completed
            job.setStatus(DeleteJobStatus.COMPLETED);
            job.setCompletedAt(Instant.now());
            deleteJobRepository.save(job);

            log.info("Delete job completed - JobID: {}, UserID: {}, Duration: {}ms",
                jobId, userId,
                java.time.Duration.between(job.getCreatedAt(), job.getCompletedAt()).toMillis());

        } catch (Exception e) {
            log.error("Failed to delete user - JobID: {}, UserID: {}",
                jobId, job.getUserId(), e);

            job.setStatus(DeleteJobStatus.FAILED);
            job.setErrorMessage(e.getMessage());
            job.setCompletedAt(Instant.now());
            deleteJobRepository.save(job);

            throw new RuntimeException("Delete operation failed for job: " + jobId, e);
        }

        return CompletableFuture.completedFuture(null);
    }

    /**
     * Synchronous user deletion (for admin/batch operations)
     * Blocks until completion - use only when necessary
     *
     * @param userId ID of user to delete
     */
    @Transactional
    public void deleteUserSync(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new IllegalArgumentException("User not found with id: " + userId);
        }

        log.info("Starting synchronous delete for user: {}", userId);

        // Database CASCADE handles all related entities automatically
        // (posts, favorites, resources, resource_details)
        userRepository.deleteById(userId);

        // Clear cache for all affected entities
        if (cacheManager.getCache("users") != null) {
            cacheManager.getCache("users").evict(userId);
        }
        if (cacheManager.getCache("posts") != null) {
            cacheManager.getCache("posts").clear();
        }
        if (cacheManager.getCache("favorites") != null) {
            cacheManager.getCache("favorites").clear();
        }
        if (cacheManager.getCache("resources") != null) {
            cacheManager.getCache("resources").clear();
        }

        log.info("Synchronous delete completed for user: {}", userId);
    }

    /**
     * Get status of a delete job
     *
     * @param jobId Job ID
     * @return DeleteJob with current status
     * @throws IllegalArgumentException if job not found
     */
    public DeleteJob getJobStatus(String jobId) {
        return deleteJobRepository.findById(jobId)
            .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));
    }
}
