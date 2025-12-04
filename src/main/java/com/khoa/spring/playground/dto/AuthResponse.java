package com.khoa.spring.playground.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO after successful authentication
 * Contains access token and token metadata
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

	/**
	 * Access token for API calls
	 */
	private String accessToken;

	/**
	 * Token type (usually "Bearer")
	 */
	private String tokenType;

	/**
	 * Token expiration time in seconds
	 */
	private Long expiresIn;

	/**
	 * Refresh token for obtaining new access tokens
	 */
	private String refreshToken;

	/**
	 * User ID in the IAM system
	 */
	private String userId;

	public AuthResponse(String accessToken, String refreshToken, Long expiresIn) {
		this.accessToken = accessToken;
		this.refreshToken = refreshToken;
		this.expiresIn = expiresIn;
		this.tokenType = "Bearer";
	}

}
