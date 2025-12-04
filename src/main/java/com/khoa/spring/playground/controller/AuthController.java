package com.khoa.spring.playground.controller;

import com.khoa.spring.playground.dto.AuthResponse;
import com.khoa.spring.playground.dto.LoginRequest;
import com.khoa.spring.playground.dto.RegisterRequest;
import com.khoa.spring.playground.dto.UserResponse;
import com.khoa.spring.playground.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for authentication operations
 * Handles user registration, login, and current user information
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

	private final AuthService authService;

	/**
	 * Register a new user
	 * POST /api/auth/register
	 *
	 * @param request RegisterRequest containing user details
	 * @return AuthResponse with access token
	 */
	@PostMapping("/register")
	public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {
		log.info("Registration request received for username: {}", request.getUsername());

		try {
			if (request.getPassword() == null || request.getPassword().isEmpty()) {
				return ResponseEntity.badRequest().build();
			}
			if (request.getEmail() == null || request.getEmail().isEmpty()) {
				return ResponseEntity.badRequest().build();
			}

			AuthResponse response = authService.register(
                    request.getEmail(),
                    request.getEmail(),
					request.getFirstName(),
                    request.getLastName(),
                    request.getPassword()
            );

			return ResponseEntity.status(HttpStatus.CREATED).body(response);
		}
		catch (Exception e) {
			log.error("Registration failed for username: {}", request.getUsername(), e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
		}
	}

	/**
	 * Login user and get access token
	 * POST /api/auth/login
	 *
	 * @param request LoginRequest containing username and password
	 * @return AuthResponse with access token
	 */
	@PostMapping("/login")
	public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
		log.info("Login request received for username: {}", request.getEmail());

		try {
			// Validate request
			if (request.getEmail() == null || request.getEmail().isEmpty()) {
				return ResponseEntity.badRequest().build();
			}
			if (request.getPassword() == null || request.getPassword().isEmpty()) {
				return ResponseEntity.badRequest().build();
			}

			AuthResponse response = authService.login(request.getEmail(), request.getPassword());

			return ResponseEntity.ok(response);
		}
		catch (Exception e) {
			log.error("Login failed for username: {}", request.getEmail(), e);
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
	}

	/**
	 * Get current authenticated user information
	 * GET /api/auth/me
	 *
	 * @param authorization Authorization header with Bearer token
	 * @return UserResponse with current user information
	 */
	@GetMapping("/me")
	public ResponseEntity<UserResponse> getCurrentUser(@RequestHeader("Authorization") String authorization) {
		log.debug("Get current user request received");

		try {
			// Validate authorization header
			if (authorization == null || !authorization.startsWith("Bearer ")) {
				return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
			}

			String token = authorization.substring(7);
			UserResponse response = authService.getCurrentUser(token);

			return ResponseEntity.ok(response);
		}
		catch (Exception e) {
			log.error("Failed to get current user", e);
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
	}

}
