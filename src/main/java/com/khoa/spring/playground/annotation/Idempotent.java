package com.khoa.spring.playground.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark methods that should be idempotent.
 * The method will check for X-Idempotency-Key header and return cached response if exists.
 *
 * Usage:
 * @Idempotent
 * public ResponseEntity<User> createUser(@RequestBody User user) {
 *     // This method will only execute once per unique idempotency key
 * }
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {
    /**
     * TTL in seconds for idempotency cache. Default is 24 hours (86400 seconds).
     */
    long ttl() default 86400L;

    /**
     * Whether to include request body in hash validation. Default is true.
     * When true, the same idempotency key with different body will be rejected.
     */
    boolean validateRequestBody() default true;
}
