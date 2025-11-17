package com.khoa.spring.playground.entity;

/**
 * Status enum for delete job tracking
 */
public enum DeleteJobStatus {
    /**
     * Job is created but not yet started
     */
    PENDING,

    /**
     * Job is currently being processed
     */
    IN_PROGRESS,

    /**
     * Job completed successfully
     */
    COMPLETED,

    /**
     * Job failed with errors
     */
    FAILED
}
