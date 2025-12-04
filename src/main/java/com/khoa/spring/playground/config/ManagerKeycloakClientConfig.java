package com.khoa.spring.playground.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.authorization.client.AuthzClient;
import org.keycloak.authorization.client.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import java.util.Collections;

/**
 * Keycloak client configuration for Manager instance
 * Provides AuthzClient and Admin Client for manager Keycloak
 * Only enabled when keycloak.manager.enabled=true
 */
@org.springframework.context.annotation.Configuration
@ConditionalOnProperty(name = "keycloak.manager.enabled", havingValue = "true")
@ConditionalOnBean(ManagerKeycloakConfig.class)
@RequiredArgsConstructor
@Slf4j
public class ManagerKeycloakClientConfig {

	private final ManagerKeycloakConfig managerConfig;

	/**
	 * AuthzClient for manager policy enforcement and authorization decisions
	 * Used to evaluate permissions and policies defined in Manager Keycloak
	 */
	@Bean(name = "managerAuthzClient")
	public AuthzClient managerAuthzClient() {
		log.debug("Creating Manager Keycloak AuthzClient");
		Configuration configuration = new Configuration(managerConfig.getAuthServerUrl(), managerConfig.getRealm(),
				managerConfig.getClientId(),
				Collections.singletonMap("secret", managerConfig.getCredentials().getSecret()), null);

		return AuthzClient.create(configuration);
	}

	/**
	 * Keycloak Admin Client for managing manager realm resources
	 * Requires admin credentials to access Keycloak Admin REST API
	 */
	@Bean(name = "managerKeycloakAdminClient")
	public Keycloak managerKeycloakAdminClient() {
		log.debug("Creating Manager Keycloak Admin Client");
		return KeycloakBuilder.builder()
			.serverUrl(managerConfig.getAuthServerUrl())
			.realm("master")
			.grantType(OAuth2Constants.PASSWORD)
			.clientId("admin-cli")
			.username(managerConfig.getAdmin().getUsername())
			.password(managerConfig.getAdmin().getPassword())
			.build();
	}

}
