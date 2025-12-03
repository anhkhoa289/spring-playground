package com.khoa.spring.playground.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark methods as idempotent.
 * When applied, the method will cache its result based on an idempotency key
 * and return the cached result for duplicate requests within the TTL period.
 *
 * <p>The idempotency key can be generated in two ways:
 * <ul>
 *   <li>Using {@link #key()} with SpEL expression (recommended for flexibility)</li>
 *   <li>Using {@link #keyHeader()} to extract from HTTP header (simple use case)</li>
 * </ul>
 *
 * <p>Examples:
 * <pre>
 * // Using method parameters
 * {@code @Idempotent(key = "#userId + '-' + #orderId")}
 * public ResponseEntity<?> createOrder(String userId, String orderId)
 *
 * // Using object properties
 * {@code @Idempotent(key = "#request.userId + '-' + #request.action")}
 * public ResponseEntity<?> processRequest(RequestDto request)
 *
 * // Using HTTP header (backward compatible)
 * {@code @Idempotent(keyHeader = "X-Request-ID")}
 * public ResponseEntity<?> legacy(@RequestHeader("X-Request-ID") String requestId)
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {

	/**
	 * SpEL expression to generate the idempotency key from method parameters.
	 * Takes precedence over {@link #keyHeader()} if specified.
	 * <p>
	 * The expression can reference method parameters by name (e.g., #userId, #request.id).
	 * If empty, falls back to {@link #keyHeader()}.
	 * <p>
	 * Example: {@code key = "#userId + '-' + #action"}
	 *
	 * @return SpEL expression for key generation
	 */
	String key() default "";

	/**
	 * The name of the HTTP header that contains the idempotency key.
	 * Only used when {@link #key()} is not specified.
	 * Default is "X-Request-ID"
	 *
	 * @return HTTP header name
	 */
	String keyHeader() default "X-Request-ID";

	/**
	 * Time-to-live in seconds for the idempotency cache entry.
	 * Default is 86400 seconds (24 hours)
	 *
	 * @return TTL in seconds
	 */
	int ttl() default 86400;

}
