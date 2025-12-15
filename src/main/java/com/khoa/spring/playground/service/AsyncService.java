package com.khoa.spring.playground.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Service for demonstrating async operations
 * Shows how to execute background tasks after returning HTTP response
 */
@Service
@Slf4j
public class AsyncService {

    /**
     * Asynchronous task that runs after the response is returned
     * Uses the deleteUserExecutor thread pool configured in AsyncConfig
     *
     * @param taskId Unique identifier for the task
     * @return CompletableFuture for async execution
     */
    @Async("deleteUserExecutor")
    public CompletableFuture<Void> executeBackgroundTask(String taskId) {
        log.info("Starting background task - TaskID: {}", taskId);

        try {
            // Simulate some background processing
            Thread.sleep(5000); // Sleep for 5 seconds

            log.info("Background task processing - TaskID: {}", taskId);

            // Perform actual background work here
            // For example: send email, process data, update database, etc.

            log.info("Background task completed - TaskID: {}", taskId);

        } catch (InterruptedException e) {
            log.error("Background task interrupted - TaskID: {}", taskId, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Background task failed for task: " + taskId, e);
        } catch (Exception e) {
            log.error("Background task failed - TaskID: {}", taskId, e);
            throw new RuntimeException("Background task failed for task: " + taskId, e);
        }

        return CompletableFuture.completedFuture(null);
    }
}
