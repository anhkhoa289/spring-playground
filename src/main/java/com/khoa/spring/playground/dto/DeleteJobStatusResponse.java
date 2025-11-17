package com.khoa.spring.playground.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for checking delete job status
 * Provides detailed progress information
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeleteJobStatusResponse {

    /**
     * Job identifier
     */
    private String jobId;

    /**
     * Current status: PENDING, IN_PROGRESS, COMPLETED, FAILED
     */
    private String status;

    /**
     * Progress percentage (0-100)
     */
    private Integer progress;

    /**
     * Number of records processed
     */
    private Long processedRecords;

    /**
     * Total number of records to process
     */
    private Long totalRecords;

    /**
     * Error message if status is FAILED
     */
    private String errorMessage;

    /**
     * Human-readable message
     */
    private String message;

    public DeleteJobStatusResponse(String jobId, String status, Integer progress,
                                   Long processedRecords, Long totalRecords, String errorMessage) {
        this.jobId = jobId;
        this.status = status;
        this.progress = progress;
        this.processedRecords = processedRecords;
        this.totalRecords = totalRecords;
        this.errorMessage = errorMessage;
        this.message = buildMessage(status, progress);
    }

    private String buildMessage(String status, Integer progress) {
        switch (status) {
            case "PENDING":
                return "Delete operation is queued and will start shortly.";
            case "IN_PROGRESS":
                return String.format("Delete operation is in progress (%d%% complete).", progress);
            case "COMPLETED":
                return "Delete operation completed successfully.";
            case "FAILED":
                return "Delete operation failed. See errorMessage for details.";
            default:
                return "Unknown status.";
        }
    }
}
