package com.khoa.spring.playground.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for user registration
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

	/**
	 * Username for the new user
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
	 * Password for the account
	 */
	private String password;

}
