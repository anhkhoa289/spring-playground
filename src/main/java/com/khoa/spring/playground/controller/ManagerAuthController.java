package com.khoa.spring.playground.controller;

import com.khoa.spring.playground.dto.AuthResponse;
import com.khoa.spring.playground.dto.LoginRequest;
import com.khoa.spring.playground.dto.RegisterRequest;
import com.khoa.spring.playground.dto.UserResponse;
import com.khoa.spring.playground.service.ManagerAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for manager authentication operations
 * Handles manager registration, login, and current manager information using Manager Keycloak instance
 */
@RestController
@RequestMapping("/api/manager-auth")
@RequiredArgsConstructor
@Slf4j
@ConditionalOnBean(ManagerAuthService.class)
public class ManagerAuthController {

	private final ManagerAuthService managerAuthService;

	/**
	 * Register a new manager
	 * POST /api/manager-auth/register
	 *
	 * @param request RegisterRequest containing manager details
	 * @return AuthResponse with access token
	 */
	@PostMapping("/register")
	public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {
		log.info("Manager registration request received for username: {}", request.getUsername());

		try {
			if (request.getPassword() == null || request.getPassword().isEmpty()) {
				return ResponseEntity.badRequest().build();
			}
			if (request.getEmail() == null || request.getEmail().isEmpty()) {
				return ResponseEntity.badRequest().build();
			}

			AuthResponse response = managerAuthService.register(request.getEmail(), request.getEmail(),
					request.getFirstName(), request.getLastName(), request.getPassword());

			return ResponseEntity.status(HttpStatus.CREATED).body(response);
		}
		catch (Exception e) {
			log.error("Manager registration failed for username: {}", request.getUsername(), e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
		}
	}

	/**
	 * Login manager and get access token
	 * POST /api/manager-auth/login
	 *
	 * @param request LoginRequest containing username and password
	 * @return AuthResponse with access token
	 */
	@PostMapping("/login")
	public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
		log.info("Manager login request received for username: {}", request.getEmail());

		try {
			// Validate request
			if (request.getEmail() == null || request.getEmail().isEmpty()) {
				return ResponseEntity.badRequest().build();
			}
			if (request.getPassword() == null || request.getPassword().isEmpty()) {
				return ResponseEntity.badRequest().build();
			}

			AuthResponse response = managerAuthService.login(request.getEmail(), request.getPassword());

			return ResponseEntity.ok(response);
		}
		catch (Exception e) {
			log.error("Manager login failed for username: {}", request.getEmail(), e);
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
	}

	/**
	 * Get current authenticated manager information
	 * GET /api/manager-auth/me
	 *
	 * @param authorization Authorization header with Bearer token
	 * @return UserResponse with current manager information
	 */
	@GetMapping("/me")
	public ResponseEntity<UserResponse> getCurrentUser(@RequestHeader("Authorization") String authorization) {
		log.debug("Get current manager request received");

		try {
			// Validate authorization header
			if (authorization == null || !authorization.startsWith("Bearer ")) {
				return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
			}

			String token = authorization.substring(7);
			UserResponse response = managerAuthService.getCurrentUser(token);

			return ResponseEntity.ok(response);
		}
		catch (Exception e) {
			log.error("Failed to get current manager", e);
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
	}

}
