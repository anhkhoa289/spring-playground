package com.khoa.spring.playground.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for user login
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

	/**
	 * Username or email
	 */
	private String email;

	/**
	 * User password
	 */
	private String password;

}
