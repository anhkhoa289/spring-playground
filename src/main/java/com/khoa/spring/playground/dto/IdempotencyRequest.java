package com.khoa.spring.playground.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

/**
 * DTO to store idempotency request information in Hazelcast cache
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IdempotencyRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * Idempotency key from request header
     */
    private String idempotencyKey;

    /**
     * Response body that was returned
     */
    private Object response;

    /**
     * HTTP status code
     */
    private int statusCode;

    /**
     * Timestamp when request was processed
     */
    private Instant processedAt;

    /**
     * Request hash for additional validation (optional)
     */
    private String requestHash;
}
