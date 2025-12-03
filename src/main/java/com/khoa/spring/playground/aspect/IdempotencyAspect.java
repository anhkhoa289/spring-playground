package com.khoa.spring.playground.aspect;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.khoa.spring.playground.annotation.Idempotent;
import com.khoa.spring.playground.dto.IdempotencyResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * Aspect to handle idempotent API operations.
 * Uses Hazelcast distributed cache to store responses and prevent duplicate processing.
 * Supports both SpEL-based key generation and HTTP header-based keys.
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class IdempotencyAspect {

	private static final String IDEMPOTENCY_CACHE_NAME = "idempotency";

	private final HazelcastInstance hazelcastInstance;

	private final ExpressionParser parser = new SpelExpressionParser();

	private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

	/**
	 * Around advice for methods annotated with @Idempotent.
	 * Checks cache for existing response before proceeding with method execution.
	 */
	@Around("@annotation(idempotent)")
	public Object handleIdempotency(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {

		// Generate idempotency key
		String idempotencyKey = generateIdempotencyKey(joinPoint, idempotent);

		// If no idempotency key could be generated, proceed without caching
		if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
			log.debug("No idempotency key generated, proceeding without cache");
			return joinPoint.proceed();
		}

		// Get or create Hazelcast map for idempotency
		IMap<String, IdempotencyResponse> cache = hazelcastInstance.getMap(IDEMPOTENCY_CACHE_NAME);

		// Check if response exists in cache
		IdempotencyResponse cachedResponse = cache.get(idempotencyKey);
		if (cachedResponse != null) {
			log.info("Returning cached response for idempotency key: {}", idempotencyKey);
			cachedResponse.setFromCache(true);

			// Return cached response with same status code
			return ResponseEntity.status(cachedResponse.getStatusCode())
				.header("X-Idempotent-Replayed", "true")
				.body(cachedResponse.getResponseBody());
		}

		// Execute the actual method
		log.debug("No cached response found for key: {}, executing method", idempotencyKey);
		Object result = joinPoint.proceed();

		// Cache the response if it's a ResponseEntity
		if (result instanceof ResponseEntity<?> responseEntity) {
			IdempotencyResponse responseToCache = new IdempotencyResponse(responseEntity.getBody(),
					responseEntity.getStatusCode().value(), LocalDateTime.now(), false);

			// Store in cache with TTL
			cache.put(idempotencyKey, responseToCache, idempotent.ttl(), TimeUnit.SECONDS);

			log.info("Cached response for idempotency key: {} with TTL: {}s", idempotencyKey, idempotent.ttl());
		}
		else {
			log.warn("Method return type is not ResponseEntity, skipping cache. Method: {}",
					((MethodSignature) joinPoint.getSignature()).getMethod().getName());
		}

		return result;
	}

	/**
	 * Generates the idempotency key based on annotation configuration.
	 * Prioritizes SpEL key expression over HTTP header extraction.
	 *
	 * @param joinPoint The join point containing method information
	 * @param idempotent The idempotent annotation
	 * @return The generated idempotency key, or null if none could be generated
	 */
	private String generateIdempotencyKey(ProceedingJoinPoint joinPoint, Idempotent idempotent) {
		// Priority 1: Use SpEL expression if provided
		if (!idempotent.key().isEmpty()) {
			return generateKeyFromSpEL(joinPoint, idempotent.key());
		}

		// Priority 2: Fall back to HTTP header
		return generateKeyFromHeader(idempotent.keyHeader());
	}

	/**
	 * Generates idempotency key from SpEL expression using method parameters.
	 *
	 * @param joinPoint The join point containing method arguments
	 * @param keyExpression The SpEL expression
	 * @return The evaluated key, or null if evaluation fails
	 */
	private String generateKeyFromSpEL(ProceedingJoinPoint joinPoint, String keyExpression) {
		try {
			MethodSignature signature = (MethodSignature) joinPoint.getSignature();
			Method method = signature.getMethod();
			Object[] args = joinPoint.getArgs();

			// Create evaluation context
			EvaluationContext context = new StandardEvaluationContext();

			// Get parameter names
			String[] parameterNames = parameterNameDiscoverer.getParameterNames(method);
			if (parameterNames != null) {
				for (int i = 0; i < parameterNames.length; i++) {
					context.setVariable(parameterNames[i], args[i]);
				}
			}

			// Evaluate the expression
			Object keyValue = parser.parseExpression(keyExpression).getValue(context);
			String key = keyValue != null ? keyValue.toString() : null;

			log.debug("Generated idempotency key from SpEL '{}': {}", keyExpression, key);
			return key;
		}
		catch (Exception e) {
			log.error("Failed to generate idempotency key from SpEL expression: {}", keyExpression, e);
			return null;
		}
	}

	/**
	 * Generates idempotency key from HTTP request header.
	 *
	 * @param headerName The name of the header to extract
	 * @return The header value, or null if not available
	 */
	private String generateKeyFromHeader(String headerName) {
		try {
			ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder
				.getRequestAttributes();
			if (attributes == null) {
				log.warn("No request context available for header extraction");
				return null;
			}

			HttpServletRequest request = attributes.getRequest();
			String key = request.getHeader(headerName);

			log.debug("Generated idempotency key from header '{}': {}", headerName, key);
			return key;
		}
		catch (Exception e) {
			log.error("Failed to extract idempotency key from header: {}", headerName, e);
			return null;
		}
	}

}
