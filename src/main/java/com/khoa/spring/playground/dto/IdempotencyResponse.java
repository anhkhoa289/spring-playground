package com.khoa.spring.playground.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * DTO to store idempotent response data in Hazelcast cache.
 * Includes the actual response body, HTTP status code, and timestamp.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IdempotencyResponse implements Serializable {

	private static final long serialVersionUID = 1L;

	/**
	 * The cached response body
	 */
	private Object responseBody;

	/**
	 * The HTTP status code of the original response
	 */
	private int statusCode;

	/**
	 * Timestamp when the response was cached
	 */
	private LocalDateTime timestamp;

	/**
	 * Flag to indicate if this is a cached response
	 */
	private boolean fromCache;

}
