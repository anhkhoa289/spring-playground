package com.khoa.spring.playground.config;

import lombok.extern.slf4j.Slf4j;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.authorization.client.AuthzClient;
import org.keycloak.authorization.client.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

import java.util.Collections;

/**
 * Keycloak configuration for authorization and admin operations
 * Provides AuthzClient for policy enforcement and Keycloak admin client
 */
@Slf4j
@org.springframework.context.annotation.Configuration
public class KeycloakConfig {

	@Value("${keycloak.auth-server-url}")
	private String authServerUrl;

	@Value("${keycloak.realm}")
	private String realm;

	@Value("${keycloak.client-id}")
	private String clientId;

	@Value("${keycloak.credentials.secret}")
	private String clientSecret;

	/**
	 * AuthzClient for policy enforcement and authorization decisions
	 * Used to evaluate permissions and policies defined in Keycloak
	 */
	@Bean
	public AuthzClient authzClient() {
        log.debug("Creating Keycloak AuthzClient");
		Configuration configuration = new Configuration(
                authServerUrl,
                realm,
                clientId,
                Collections.singletonMap("secret", clientSecret),
                null
        );

		return AuthzClient.create(configuration);
	}

	/**
	 * Keycloak Admin Client for managing realm resources
	 * Requires admin credentials to access Keycloak Admin REST API
	 */
	@Bean
	public Keycloak keycloakAdminClient(
            @Value("${keycloak.admin.username}") String adminUsername,
			@Value("${keycloak.admin.password}") String adminPassword
    ) {
        log.debug("Creating Keycloak Admin Client");
		return KeycloakBuilder.builder()
			.serverUrl(authServerUrl)
			.realm("master")
			.grantType(OAuth2Constants.PASSWORD)
			.clientId("admin-cli")
			.username(adminUsername)
			.password(adminPassword)
			.build();
	}

}
