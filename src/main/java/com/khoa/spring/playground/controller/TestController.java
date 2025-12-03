package com.khoa.spring.playground.controller;

import com.khoa.spring.playground.annotation.Idempotent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

@Slf4j
@RestController
@RequestMapping("/api/test")
public class TestController {

	private final Random random = new Random();

	/**
	 * Test endpoint that generates a random number.
	 * This endpoint is idempotent - if called with the same X-Request-ID header,
	 * it will return the same cached response instead of generating a new random number.
	 *
	 * Uses HTTP header-based idempotency key (backward compatible approach).
	 *
	 * @param requestId Optional idempotency key from X-Request-ID header
	 * @return Response containing the request ID and random number
	 */
	@Idempotent(keyHeader = "X-Request-ID", ttl = 300)
	@GetMapping("/random")
	public ResponseEntity<Map<String, Object>> getRandomNumber(
			@RequestHeader(value = "X-Request-ID", required = false) String requestId) {

		int randomNumber = random.nextInt(1000);

		log.info("Test endpoint called - RequestID: {}, RandomNumber: {}", requestId, randomNumber);

		Map<String, Object> response = new HashMap<>();
		response.put("requestId", requestId);
		response.put("randomNumber", randomNumber);
		response.put("method", "header-based");

		return ResponseEntity.ok(response);
	}

	/**
	 * Test endpoint demonstrating custom key generation using SpEL expression.
	 * The idempotency key is generated from method parameters (userId + action).
	 *
	 * Example: GET /api/test/random/user123?action=generate
	 * Idempotency key will be: "user123-generate"
	 *
	 * @param userId The user ID from path variable
	 * @param action The action from query parameter
	 * @return Response containing user ID, action, and random number
	 */
	@Idempotent(key = "#userId + '-' + #action", ttl = 300)
	@GetMapping("/random/{userId}")
	public ResponseEntity<Map<String, Object>> getRandomNumberWithCustomKey(
			@org.springframework.web.bind.annotation.PathVariable String userId,
			@org.springframework.web.bind.annotation.RequestParam(defaultValue = "generate") String action) {

		int randomNumber = random.nextInt(1000);

		log.info("Custom key endpoint called - UserID: {}, Action: {}, RandomNumber: {}", userId, action,
				randomNumber);

		Map<String, Object> response = new HashMap<>();
		response.put("userId", userId);
		response.put("action", action);
		response.put("randomNumber", randomNumber);
		response.put("method", "SpEL-based");

		return ResponseEntity.ok(response);
	}

}