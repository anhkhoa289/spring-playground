package com.khoa.spring.playground.aspect;

import com.khoa.spring.playground.annotation.Idempotent;
import com.khoa.spring.playground.dto.IdempotencyRequest;
import com.khoa.spring.playground.service.IdempotencyService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Aspect to handle idempotency logic for methods annotated with @Idempotent
 */
@Aspect
@Component
@Slf4j
public class IdempotencyAspect {

    private static final String IDEMPOTENCY_KEY_HEADER = "X-Idempotency-Key";

    private final IdempotencyService idempotencyService;

    @Autowired
    public IdempotencyAspect(IdempotencyService idempotencyService) {
        this.idempotencyService = idempotencyService;
    }

    /**
     * Intercept methods annotated with @Idempotent
     */
    @Around("@annotation(com.khoa.spring.playground.annotation.Idempotent)")
    public Object handleIdempotency(ProceedingJoinPoint joinPoint) throws Throwable {
        // Get current HTTP request
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            log.warn("No HTTP request context found, skipping idempotency check");
            return joinPoint.proceed();
        }

        HttpServletRequest request = attributes.getRequest();
        String idempotencyKey = request.getHeader(IDEMPOTENCY_KEY_HEADER);

        // If no idempotency key provided, proceed normally without caching
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            log.debug("No idempotency key provided for request: {} {}", request.getMethod(), request.getRequestURI());
            return joinPoint.proceed();
        }

        // Get annotation parameters
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Idempotent idempotentAnnotation = method.getAnnotation(Idempotent.class);
        long ttl = idempotentAnnotation.ttl();
        boolean validateRequestBody = idempotentAnnotation.validateRequestBody();

        log.info("Processing idempotent request with key: {} for method: {}",
                idempotencyKey, method.getName());

        // Check if request already processed
        Optional<IdempotencyRequest> cachedRequest = idempotencyService.get(idempotencyKey);

        if (cachedRequest.isPresent()) {
            IdempotencyRequest cached = cachedRequest.get();

            // Validate request body if enabled
            if (validateRequestBody) {
                String currentRequestHash = getRequestBodyHash(joinPoint);
                if (!idempotencyService.validateRequestHash(cached, currentRequestHash)) {
                    log.error("Request hash mismatch for idempotency key: {}", idempotencyKey);
                    return ResponseEntity
                        .status(HttpStatus.CONFLICT)
                        .body("Idempotency key already used with different request body");
                }
            }

            log.info("Returning cached response for idempotency key: {}", idempotencyKey);

            // Return cached response with original status code
            return ResponseEntity
                .status(cached.getStatusCode())
                .body(cached.getResponse());
        }

        // Execute the method
        log.debug("Executing method for idempotency key: {}", idempotencyKey);
        Object result = joinPoint.proceed();

        // Cache the response
        if (result instanceof ResponseEntity) {
            ResponseEntity<?> responseEntity = (ResponseEntity<?>) result;
            int statusCode = responseEntity.getStatusCode().value();
            Object responseBody = responseEntity.getBody();

            String requestHash = validateRequestBody ? getRequestBodyHash(joinPoint) : null;

            idempotencyService.store(idempotencyKey, responseBody, statusCode, requestHash, ttl);
            log.info("Cached response for idempotency key: {} with status: {}", idempotencyKey, statusCode);
        } else {
            log.warn("Method return type is not ResponseEntity, cannot cache response for key: {}",
                    idempotencyKey);
        }

        return result;
    }

    /**
     * Extract request body from method arguments and generate hash
     */
    private String getRequestBodyHash(ProceedingJoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();

        // Find the first non-primitive argument as request body
        for (Object arg : args) {
            if (arg != null && !isPrimitiveOrWrapper(arg.getClass())) {
                return idempotencyService.generateRequestHash(arg);
            }
        }

        return "";
    }

    /**
     * Check if class is primitive or wrapper type
     */
    private boolean isPrimitiveOrWrapper(Class<?> type) {
        return type.isPrimitive()
            || type == String.class
            || type == Boolean.class
            || type == Integer.class
            || type == Long.class
            || type == Double.class
            || type == Float.class
            || type == Short.class
            || type == Byte.class
            || type == Character.class;
    }
}
