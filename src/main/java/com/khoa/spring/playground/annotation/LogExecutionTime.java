package com.khoa.spring.playground.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to log method execution time and request details
 * Apply this annotation to controller methods to automatically log:
 * - Request method and endpoint
 * - Request parameters
 * - Execution time
 * - Response status
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LogExecutionTime {
    /**
     * Custom description for the operation (optional)
     */
    String value() default "";
}
