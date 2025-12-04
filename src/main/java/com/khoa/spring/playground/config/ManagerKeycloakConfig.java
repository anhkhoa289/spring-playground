package com.khoa.spring.playground.config;

import lombok.Data;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Configuration for Manager Keycloak instance
 * Handles authentication and authorization for managers
 * Only enabled when keycloak.manager.enabled=true
 */
@Configuration
@ConfigurationProperties(prefix = "keycloak.manager")
@ConditionalOnProperty(name = "keycloak.manager.enabled", havingValue = "true")
@Data
public class ManagerKeycloakConfig {

	/**
	 * Enable/disable manager Keycloak
	 */
	private boolean enabled = false;

	/**
	 * Keycloak server URL for managers
	 */
	private String authServerUrl;

	/**
	 * Realm name for managers
	 */
	private String realm;

	/**
	 * Client ID for manager authentication
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
	 * JWT Decoder for manager tokens
	 * Validates tokens from manager Keycloak instance
	 */
	@Bean(name = "managerJwtDecoder")
	public JwtDecoder managerJwtDecoder() {
		return NimbusJwtDecoder.withIssuerLocation(issuerUri).build();
	}

}
