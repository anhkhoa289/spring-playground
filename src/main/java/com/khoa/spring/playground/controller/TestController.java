package com.khoa.spring.playground.controller;

import com.khoa.spring.playground.annotation.Idempotent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
	 * This endpoint is idempotent - if called with the same requestId parameter,
	 * it will return the same cached response instead of generating a new random number.
	 * <p>
	 * Uses SpEL expression to generate key from method parameter.
	 *
	 * @param requestId Optional idempotency key from request parameter
	 * @return Response containing the request ID and random number
	 */
	@Idempotent(key = "#requestId", ttl = 300)
	@GetMapping("/random")
	public ResponseEntity<Map<String, Object>> getRandomNumber(
            @RequestParam(required = false) String requestId
    ) {

		int randomNumber = random.nextInt(1000);

		log.info("Test endpoint called - RequestID: {}, RandomNumber: {}", requestId, randomNumber);

		Map<String, Object> response = new HashMap<>();
		response.put("requestId", requestId);
		response.put("randomNumber", randomNumber);

		return ResponseEntity.ok(response);
	}

	/**
	 * Test endpoint demonstrating composite key generation using SpEL expression.
	 * The idempotency key is generated from method parameters (userId + action).
	 * <p>
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
            @PathVariable String userId,
			@RequestParam(defaultValue = "generate") String action
    ) {

		int randomNumber = random.nextInt(1000);

		log.info("Custom key endpoint called - UserID: {}, Action: {}, RandomNumber: {}", userId, action,
				randomNumber);

		Map<String, Object> response = new HashMap<>();
		response.put("userId", userId);
		response.put("action", action);
		response.put("randomNumber", randomNumber);

		return ResponseEntity.ok(response);
	}

}