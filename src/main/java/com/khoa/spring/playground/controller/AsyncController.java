package com.khoa.spring.playground.controller;

import com.khoa.spring.playground.service.AsyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Controller demonstrating async operations
 * Returns response immediately, then executes background task
 */
@RestController
@RequestMapping("/api/async")
@RequiredArgsConstructor
public class AsyncController {

    private final AsyncService asyncService;

    /**
     * Endpoint that returns response immediately, then runs async task
     *
     * How it works:
     * 1. Generates task ID
     * 2. Returns HTTP 202 Accepted with task ID immediately
     * 3. Triggers async background task (runs for ~5 seconds in separate thread)
     * 4. Client receives response while background task is still running
     *
     * Use case: Long-running operations that don't need immediate results
     * (e.g., sending emails, processing files, generating reports)
     *
     * @param message Optional message to include in the response
     * @return ResponseEntity with task ID and status
     */
    @PostMapping("/process")
    public ResponseEntity<Map<String, String>> processAsync(
            @RequestParam(defaultValue = "Processing started") String message) {

        // Generate unique task ID
        String taskId = UUID.randomUUID().toString();

        // Trigger async background task (non-blocking)
        // This method returns immediately, task runs in background thread pool
        asyncService.executeBackgroundTask(taskId);

        // Build response
        Map<String, String> response = new HashMap<>();
        response.put("taskId", taskId);
        response.put("status", "ACCEPTED");
        response.put("message", message);

        // Return response immediately (before background task completes)
        // HTTP 202 Accepted indicates request accepted for processing
        return ResponseEntity.accepted().body(response);
    }

    /**
     * Simple GET endpoint for testing async behavior
     *
     * @return ResponseEntity with task information
     */
    @GetMapping("/test")
    public ResponseEntity<Map<String, String>> testAsync() {
        String taskId = UUID.randomUUID().toString();

        // Trigger async task
        asyncService.executeBackgroundTask(taskId);

        Map<String, String> response = new HashMap<>();
        response.put("taskId", taskId);
        response.put("status", "ACCEPTED");
        response.put("info", "Background task started. Check logs for task completion.");

        return ResponseEntity.accepted().body(response);
    }
}
