package com.khoa.spring.playground.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO when initiating a user delete operation
 * Returns job ID for tracking progress
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeleteJobResponse {

    /**
     * Unique job identifier for tracking
     */
    private String jobId;

    /**
     * Initial job status (usually PENDING)
     */
    private String status;

    /**
     * Message for user
     */
    private String message;

    public DeleteJobResponse(String jobId, String status) {
        this.jobId = jobId;
        this.status = status;
        this.message = "Delete operation has been scheduled. Use the jobId to track progress.";
    }
}
