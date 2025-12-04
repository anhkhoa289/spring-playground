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
 * <p>The idempotency key is generated using SpEL expression from method parameters.
 *
 * <p>Examples:
 * <pre>
 * // Using single parameter
 * {@code @Idempotent(key = "#requestId")}
 * public ResponseEntity<?> processRequest(String requestId)
 *
 * // Using multiple parameters
 * {@code @Idempotent(key = "#userId + '-' + #orderId")}
 * public ResponseEntity<?> createOrder(String userId, String orderId)
 *
 * // Using object properties
 * {@code @Idempotent(key = "#request.userId + '-' + #request.action")}
 * public ResponseEntity<?> processRequest(RequestDto request)
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {

	/**
	 * SpEL expression to generate the idempotency key from method parameters.
	 * <p>
	 * The expression can reference method parameters by name (e.g., #userId, #request.id).
	 * <p>
	 * Example: {@code key = "#userId + '-' + #action"}
	 *
	 * @return SpEL expression for key generation
	 */
	String key();

	/**
	 * Time-to-live in seconds for the idempotency cache entry.
	 * Default is 86400 seconds (24 hours)
	 *
	 * @return TTL in seconds
	 */
	int ttl() default 86400;

}
