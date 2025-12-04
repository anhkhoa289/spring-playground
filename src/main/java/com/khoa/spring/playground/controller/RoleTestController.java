package com.khoa.spring.playground.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Test controller to verify role-based access control
 * Demonstrates ROLE_USER and ROLE_MANAGER authorization
 */
@RestController
@RequestMapping("/api/role-test")
@Slf4j
public class RoleTestController {

	/**
	 * Public endpoint - accessible without authentication
	 * GET /api/role-test/public
	 */
	@GetMapping("/public")
	public ResponseEntity<Map<String, Object>> publicEndpoint() {
		log.info("Public endpoint accessed");

		Map<String, Object> response = new HashMap<>();
		response.put("message", "This is a public endpoint accessible to everyone");
		response.put("timestamp", System.currentTimeMillis());

		return ResponseEntity.ok(response);
	}

	/**
	 * Authenticated endpoint - accessible to any authenticated user
	 * GET /api/role-test/authenticated
	 */
	@GetMapping("/authenticated")
	public ResponseEntity<Map<String, Object>> authenticatedEndpoint(Authentication authentication) {
		log.info("Authenticated endpoint accessed by: {}", authentication.getName());

		Map<String, Object> response = new HashMap<>();
		response.put("message", "This endpoint requires authentication");
		response.put("username", authentication.getName());
		response.put("roles", getRoles(authentication));
		response.put("timestamp", System.currentTimeMillis());

		return ResponseEntity.ok(response);
	}

	/**
	 * User-only endpoint - requires ROLE_USER
	 * GET /api/role-test/user-only
	 */
	@GetMapping("/user-only")
	@PreAuthorize("hasRole('USER')")
	public ResponseEntity<Map<String, Object>> userOnlyEndpoint(Authentication authentication) {
		log.info("User-only endpoint accessed by: {}", authentication.getName());

		Map<String, Object> response = new HashMap<>();
		response.put("message", "This endpoint is only accessible to users with ROLE_USER");
		response.put("username", authentication.getName());
		response.put("roles", getRoles(authentication));
		response.put("timestamp", System.currentTimeMillis());

		return ResponseEntity.ok(response);
	}

	/**
	 * Manager-only endpoint - requires ROLE_MANAGER
	 * GET /api/role-test/manager-only
	 */
	@GetMapping("/manager-only")
	@PreAuthorize("hasRole('MANAGER')")
	public ResponseEntity<Map<String, Object>> managerOnlyEndpoint(Authentication authentication) {
		log.info("Manager-only endpoint accessed by: {}", authentication.getName());

		Map<String, Object> response = new HashMap<>();
		response.put("message", "This endpoint is only accessible to users with ROLE_MANAGER");
		response.put("username", authentication.getName());
		response.put("roles", getRoles(authentication));
		response.put("timestamp", System.currentTimeMillis());

		return ResponseEntity.ok(response);
	}

	/**
	 * User or Manager endpoint - requires either ROLE_USER or ROLE_MANAGER
	 * GET /api/role-test/user-or-manager
	 */
	@GetMapping("/user-or-manager")
	@PreAuthorize("hasAnyRole('USER', 'MANAGER')")
	public ResponseEntity<Map<String, Object>> userOrManagerEndpoint(Authentication authentication) {
		log.info("User or Manager endpoint accessed by: {}", authentication.getName());

		Map<String, Object> response = new HashMap<>();
		response.put("message", "This endpoint is accessible to users with ROLE_USER or ROLE_MANAGER");
		response.put("username", authentication.getName());
		response.put("roles", getRoles(authentication));
		response.put("timestamp", System.currentTimeMillis());

		return ResponseEntity.ok(response);
	}

	/**
	 * Admin endpoint - requires both ROLE_USER and ROLE_MANAGER
	 * GET /api/role-test/admin
	 */
	@GetMapping("/admin")
	@PreAuthorize("hasRole('USER') and hasRole('MANAGER')")
	public ResponseEntity<Map<String, Object>> adminEndpoint(Authentication authentication) {
		log.info("Admin endpoint accessed by: {}", authentication.getName());

		Map<String, Object> response = new HashMap<>();
		response.put("message", "This endpoint requires both ROLE_USER and ROLE_MANAGER");
		response.put("username", authentication.getName());
		response.put("roles", getRoles(authentication));
		response.put("timestamp", System.currentTimeMillis());

		return ResponseEntity.ok(response);
	}

	/**
	 * Get all roles endpoint - shows all granted authorities
	 * GET /api/role-test/my-roles
	 */
	@GetMapping("/my-roles")
	public ResponseEntity<Map<String, Object>> myRolesEndpoint(Authentication authentication) {
		log.info("My roles endpoint accessed by: {}", authentication.getName());

		List<String> roles = getRoles(authentication);

		Map<String, Object> response = new HashMap<>();
		response.put("message", "Your current roles and authorities");
		response.put("username", authentication.getName());
		response.put("roles", roles);
		response.put("hasRoleUser", roles.contains("ROLE_USER"));
		response.put("hasRoleManager", roles.contains("ROLE_MANAGER"));
		response.put("timestamp", System.currentTimeMillis());

		return ResponseEntity.ok(response);
	}

	/**
	 * Helper method to extract roles from authentication
	 */
	private List<String> getRoles(Authentication authentication) {
		return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority)
				.collect(Collectors.toList());
	}

}
