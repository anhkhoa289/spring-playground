package com.khoa.spring.playground.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark methods as idempotent.
 * When applied, the method will cache its result based on an idempotency key
 * (typically from the X-Request-ID header) and return the cached result
 * for duplicate requests within the TTL period.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {

	/**
	 * The name of the HTTP header that contains the idempotency key.
	 * Default is "X-Request-ID"
	 */
	String keyHeader() default "X-Request-ID";

	/**
	 * Time-to-live in seconds for the idempotency cache entry.
	 * Default is 86400 seconds (24 hours)
	 */
	int ttl() default 86400;

}
