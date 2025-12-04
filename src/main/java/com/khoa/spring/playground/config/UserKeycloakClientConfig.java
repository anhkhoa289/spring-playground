package com.khoa.spring.playground.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.authorization.client.AuthzClient;
import org.keycloak.authorization.client.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.Collections;

/**
 * Keycloak client configuration for User instance
 * Provides AuthzClient and Admin Client for user Keycloak
 */
@org.springframework.context.annotation.Configuration
@RequiredArgsConstructor
@Slf4j
public class UserKeycloakClientConfig {

	private final UserKeycloakConfig userConfig;

	/**
	 * AuthzClient for user policy enforcement and authorization decisions
	 * Used to evaluate permissions and policies defined in User Keycloak
	 */
	@Bean(name = "userAuthzClient")
	@Primary
	public AuthzClient userAuthzClient() {
		log.debug("Creating User Keycloak AuthzClient");
		Configuration configuration = new Configuration(userConfig.getAuthServerUrl(), userConfig.getRealm(),
				userConfig.getClientId(), Collections.singletonMap("secret", userConfig.getCredentials().getSecret()),
				null);

		return AuthzClient.create(configuration);
	}

	/**
	 * Keycloak Admin Client for managing user realm resources
	 * Requires admin credentials to access Keycloak Admin REST API
	 */
	@Bean(name = "userKeycloakAdminClient")
	@Primary
	public Keycloak userKeycloakAdminClient() {
		log.debug("Creating User Keycloak Admin Client");
		return KeycloakBuilder.builder()
			.serverUrl(userConfig.getAuthServerUrl())
			.realm("master")
			.grantType(OAuth2Constants.PASSWORD)
			.clientId("admin-cli")
			.username(userConfig.getAdmin().getUsername())
			.password(userConfig.getAdmin().getPassword())
			.build();
	}

}
