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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * Aspect to handle idempotent API operations.
 * Uses Hazelcast distributed cache to store responses and prevent duplicate processing.
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class IdempotencyAspect {

	private static final String IDEMPOTENCY_CACHE_NAME = "idempotency";

	private final HazelcastInstance hazelcastInstance;

	/**
	 * Around advice for methods annotated with @Idempotent.
	 * Checks cache for existing response before proceeding with method execution.
	 */
	@Around("@annotation(idempotent)")
	public Object handleIdempotency(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {

		// Get current HTTP request
		ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder
			.getRequestAttributes();
		if (attributes == null) {
			log.warn("No request context available, skipping idempotency check");
			return joinPoint.proceed();
		}

		HttpServletRequest request = attributes.getRequest();
		String idempotencyKey = request.getHeader(idempotent.keyHeader());

		// If no idempotency key provided, proceed without caching
		if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
			log.debug("No idempotency key provided in header '{}', proceeding without cache",
					idempotent.keyHeader());
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

}
