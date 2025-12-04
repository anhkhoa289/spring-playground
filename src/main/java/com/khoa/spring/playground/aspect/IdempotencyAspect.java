package com.khoa.spring.playground.aspect;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.khoa.spring.playground.annotation.Idempotent;
import com.khoa.spring.playground.dto.IdempotencyResponse;
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

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * Aspect to handle idempotent API operations.
 * Uses Hazelcast distributed cache to store responses and prevent duplicate processing.
 * Generates cache keys using SpEL expressions from method parameters.
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

		// Generate idempotency key from SpEL expression
		String idempotencyKey = generateKeyFromSpEL(joinPoint, idempotent.key());

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

}
