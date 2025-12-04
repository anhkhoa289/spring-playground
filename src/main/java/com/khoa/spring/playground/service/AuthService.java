package com.khoa.spring.playground.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.khoa.spring.playground.dto.AuthResponse;
import com.khoa.spring.playground.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Service for authentication operations
 * Handles user registration, login, and token management
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {

	private final IIAMService iamService;

	private final RestTemplate restTemplate = new RestTemplate();

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Value("${keycloak.auth-server-url}")
	private String authServerUrl;

	@Value("${keycloak.realm}")
	private String realm;

	@Value("${keycloak.client-id}")
	private String clientId;

	@Value("${keycloak.credentials.secret}")
	private String clientSecret;

	/**
	 * Register a new user
	 *
	 * @param username Username
	 * @param email Email
	 * @param firstName First name
	 * @param lastName Last name
	 * @param password Password
	 * @return AuthResponse with access token
	 */
	public AuthResponse register(String username, String email, String firstName, String lastName, String password) {
		log.info("Registering new user: {}", username);

		// Create user in Keycloak
		String userId = iamService.createUser(username, email, firstName, lastName, password);

		// Login to get token
		AuthResponse authResponse = login(username, password);
		authResponse.setUserId(userId);

		log.info("User registered successfully: {}", username);
		return authResponse;
	}

	/**
	 * Login user and get access token
	 *
	 * @param username Username
	 * @param password Password
	 * @return AuthResponse with access token
	 */
	public AuthResponse login(String username, String password) {
		log.info("User login attempt: {}", username);

		String tokenUrl = String.format("%s/realms/%s/protocol/openid-connect/token", authServerUrl, realm);

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

		MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
		body.add("grant_type", "password");
		body.add("client_id", clientId);
		body.add("client_secret", clientSecret);
		body.add("username", username);
		body.add("password", password);

		HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

		try {
			ResponseEntity<String> response = restTemplate.postForEntity(tokenUrl, request, String.class);

			JsonNode jsonNode = objectMapper.readTree(response.getBody());
			String accessToken = jsonNode.get("access_token").asText();
			String refreshToken = jsonNode.has("refresh_token") ? jsonNode.get("refresh_token").asText() : null;
			Long expiresIn = jsonNode.get("expires_in").asLong();

			log.info("User logged in successfully: {}", username);
			return new AuthResponse(accessToken, refreshToken, expiresIn);
		}
		catch (Exception e) {
			log.error("Login failed for user: {}", username, e);
			throw new RuntimeException("Invalid username or password", e);
		}
	}

	/**
	 * Get current user information from access token
	 *
	 * @param token Access token
	 * @return UserResponse with user information
	 */
	public UserResponse getCurrentUser(String token) {
		log.debug("Getting current user from token");

		try {
			// Parse JWT token to get user ID
			String userId = getUserIdFromToken(token);

			// Get user details from Keycloak
			Map<String, Object> userDetails = iamService.getUserById(userId);
			List<String> roles = iamService.getUserRoles(userId);

			UserResponse userResponse = new UserResponse();
			userResponse.setId((String) userDetails.get("id"));
			userResponse.setUsername((String) userDetails.get("username"));
			userResponse.setEmail((String) userDetails.get("email"));
			userResponse.setFirstName((String) userDetails.get("firstName"));
			userResponse.setLastName((String) userDetails.get("lastName"));
			userResponse.setEnabled((Boolean) userDetails.get("enabled"));
			userResponse.setEmailVerified((Boolean) userDetails.get("emailVerified"));
			userResponse.setRoles(roles);
			userResponse.setAttributes((Map<String, Object>) userDetails.get("attributes"));

			return userResponse;
		}
		catch (Exception e) {
			log.error("Failed to get current user", e);
			throw new RuntimeException("Invalid or expired token", e);
		}
	}

	/**
	 * Extract user ID from JWT token
	 */
	private String getUserIdFromToken(String token) {
		try {
			// Remove "Bearer " prefix if present
			if (token.startsWith("Bearer ")) {
				token = token.substring(7);
			}

			// Parse JWT token (format: header.payload.signature)
			String[] parts = token.split("\\.");
			if (parts.length != 3) {
				throw new RuntimeException("Invalid JWT token format");
			}

			// Decode payload (base64url)
			String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
			JsonNode jsonNode = objectMapper.readTree(payload);

			// Extract subject (user ID)
			return jsonNode.get("sub").asText();
		}
		catch (Exception e) {
			log.error("Failed to parse token", e);
			throw new RuntimeException("Failed to parse token", e);
		}
	}

}
