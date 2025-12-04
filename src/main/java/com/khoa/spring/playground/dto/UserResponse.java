package com.khoa.spring.playground.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Response DTO for user information
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

	/**
	 * User ID in the IAM system
	 */
	private String id;

	/**
	 * Username
	 */
	private String username;

	/**
	 * Email address
	 */
	private String email;

	/**
	 * First name
	 */
	private String firstName;

	/**
	 * Last name
	 */
	private String lastName;

	/**
	 * Whether the account is enabled
	 */
	private Boolean enabled;

	/**
	 * Whether email is verified
	 */
	private Boolean emailVerified;

	/**
	 * User roles
	 */
	private List<String> roles;

	/**
	 * Custom user attributes
	 */
	private Map<String, Object> attributes;

}
