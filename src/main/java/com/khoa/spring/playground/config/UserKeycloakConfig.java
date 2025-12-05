package com.khoa.spring.playground.config;

import lombok.Data;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Configuration for User Keycloak instance
 * Handles authentication and authorization for regular users
 */
@Configuration
@ConfigurationProperties(prefix = "keycloak.user")
@ConditionalOnProperty(name = "keycloak.user.enabled", havingValue = "true")
@Data
public class UserKeycloakConfig {

	/**
	 * Keycloak server URL for users
	 */
	private String authServerUrl;

	/**
	 * Realm name for users
	 */
	private String realm;

	/**
	 * Client ID for user authentication
	 */
	private String clientId;

	/**
	 * JWT issuer URI
	 */
	private String issuerUri;

	/**
	 * JWKS URI for JWT validation
	 */
	private String jwkSetUri;

	/**
	 * Client credentials
	 */
	private Credentials credentials = new Credentials();

	/**
	 * Admin credentials for Keycloak admin client
	 */
	private Admin admin = new Admin();

	@Data
	public static class Credentials {

		private String secret;

	}

	@Data
	public static class Admin {

		private String username;

		private String password;

	}

	/**
	 * Primary JWT Decoder for user tokens
	 * Validates tokens from user Keycloak instance
	 */
	@Bean(name = "userJwtDecoder")
	@Primary
	public JwtDecoder userJwtDecoder() {
		return NimbusJwtDecoder.withIssuerLocation(issuerUri).build();
	}

}
