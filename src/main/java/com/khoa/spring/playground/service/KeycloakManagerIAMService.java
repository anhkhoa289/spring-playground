package com.khoa.spring.playground.service;

import com.khoa.spring.playground.config.ManagerKeycloakConfig;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.authorization.client.AuthzClient;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Keycloak implementation of Manager IAM service
 * Handles manager management, roles, and authorization using Manager Keycloak instance
 * Only enabled when keycloak.manager.enabled=true
 */
@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "keycloak.manager.enabled", havingValue = "true")
@ConditionalOnBean(ManagerKeycloakConfig.class)
public class KeycloakManagerIAMService implements IIAMManagerService {

	@Qualifier("managerKeycloakAdminClient")
	private final Keycloak keycloakAdminClient;

	@Qualifier("managerAuthzClient")
	private final AuthzClient authzClient;

	private final ManagerKeycloakConfig managerConfig;

	/**
	 * Get realm resource for the configured realm
	 */
	private RealmResource getRealmResource() {
		return keycloakAdminClient.realm(managerConfig.getRealm());
	}

	/**
	 * Get users resource for the configured realm
	 */
	private UsersResource getUsersResource() {
		return getRealmResource().users();
	}

	@Override
	public String createUser(String username, String email, String firstName, String lastName, String password) {
		log.info("Creating manager: {}", username);

		UserRepresentation user = new UserRepresentation();
		user.setUsername(username);
		user.setEmail(email);
		user.setFirstName(firstName);
		user.setLastName(lastName);
		user.setEnabled(true);
		user.setEmailVerified(false);

		try (Response response = getUsersResource().create(user)) {
			if (response.getStatus() != 201) {
				throw new RuntimeException("Failed to create manager. Status: " + response.getStatus());
			}

			String userId = extractUserIdFromLocation(response.getLocation().getPath());

			// Set password
			if (password != null && !password.isEmpty()) {
				resetPassword(userId, password);
			}

			log.info("Manager created successfully: {} with ID: {}", username, userId);
			return userId;
		}
	}

	@Override
	public void updateUser(String userId, Map<String, String> attributes) {
		log.info("Updating manager: {}", userId);

		UserResource userResource = getUsersResource().get(userId);
		UserRepresentation user = userResource.toRepresentation();

		if (attributes.containsKey("email")) {
			user.setEmail(attributes.get("email"));
		}
		if (attributes.containsKey("firstName")) {
			user.setFirstName(attributes.get("firstName"));
		}
		if (attributes.containsKey("lastName")) {
			user.setLastName(attributes.get("lastName"));
		}

		// Update custom attributes
		Map<String, List<String>> userAttributes = user.getAttributes();
		if (userAttributes == null) {
			userAttributes = new HashMap<>();
		}
		for (Map.Entry<String, String> entry : attributes.entrySet()) {
			if (!entry.getKey().equals("email") && !entry.getKey().equals("firstName")
					&& !entry.getKey().equals("lastName")) {
				userAttributes.put(entry.getKey(), Collections.singletonList(entry.getValue()));
			}
		}
		user.setAttributes(userAttributes);

		userResource.update(user);
		log.info("Manager updated successfully: {}", userId);
	}

	@Override
	public void deleteUser(String userId) {
		log.info("Deleting manager: {}", userId);
		getUsersResource().get(userId).remove();
		log.info("Manager deleted successfully: {}", userId);
	}

	@Override
	public Map<String, Object> getUserById(String userId) {
		log.debug("Getting manager by ID: {}", userId);
		UserRepresentation user = getUsersResource().get(userId).toRepresentation();
		return userToMap(user);
	}

	@Override
	public Map<String, Object> getUserByUsername(String username) {
		log.debug("Getting manager by username: {}", username);
		List<UserRepresentation> users = getUsersResource().search(username, true);
		if (users.isEmpty()) {
			throw new RuntimeException("Manager not found: " + username);
		}
		return userToMap(users.get(0));
	}

	@Override
	public void assignRoles(String userId, List<String> roleNames) {
		log.info("Assigning roles to manager {}: {}", userId, roleNames);

		UserResource userResource = getUsersResource().get(userId);
		List<RoleRepresentation> roles = roleNames.stream()
			.map(roleName -> getRealmResource().roles().get(roleName).toRepresentation())
			.collect(Collectors.toList());

		userResource.roles().realmLevel().add(roles);
		log.info("Roles assigned successfully to manager: {}", userId);
	}

	@Override
	public void removeRoles(String userId, List<String> roleNames) {
		log.info("Removing roles from manager {}: {}", userId, roleNames);

		UserResource userResource = getUsersResource().get(userId);
		List<RoleRepresentation> roles = roleNames.stream()
			.map(roleName -> getRealmResource().roles().get(roleName).toRepresentation())
			.collect(Collectors.toList());

		userResource.roles().realmLevel().remove(roles);
		log.info("Roles removed successfully from manager: {}", userId);
	}

	@Override
	public List<String> getUserRoles(String userId) {
		log.debug("Getting roles for manager: {}", userId);
		return getUsersResource().get(userId)
			.roles()
			.realmLevel()
			.listEffective()
			.stream()
			.map(RoleRepresentation::getName)
			.collect(Collectors.toList());
	}

	@Override
	public boolean hasRole(String userId, String roleName) {
		log.debug("Checking if manager {} has role: {}", userId, roleName);
		return getUserRoles(userId).contains(roleName);
	}

	@Override
	public boolean validateToken(String token) {
		log.debug("Validating token");
		try {
			// Use AuthzClient to introspect token
			// This is a simplified version - in production you'd want to verify
			// signature, expiration, etc.
			return token != null && !token.isEmpty();
		}
		catch (Exception e) {
			log.error("Token validation failed", e);
			return false;
		}
	}

	@Override
	public List<String> getUserPermissions(String userId) {
		log.debug("Getting permissions for manager: {}", userId);
		// This would typically involve evaluating policies and permissions
		// For now, returning roles as permissions
		return getUserRoles(userId);
	}

	@Override
	public boolean hasPermission(String userId, String permission) {
		log.debug("Checking if manager {} has permission: {}", userId, permission);
		return getUserPermissions(userId).contains(permission);
	}

	@Override
	public void setUserEnabled(String userId, boolean enabled) {
		log.info("Setting manager {} enabled status to: {}", userId, enabled);

		UserResource userResource = getUsersResource().get(userId);
		UserRepresentation user = userResource.toRepresentation();
		user.setEnabled(enabled);
		userResource.update(user);

		log.info("Manager enabled status updated successfully: {}", userId);
	}

	@Override
	public void resetPassword(String userId, String newPassword) {
		log.info("Resetting password for manager: {}", userId);

		CredentialRepresentation credential = new CredentialRepresentation();
		credential.setType(CredentialRepresentation.PASSWORD);
		credential.setValue(newPassword);
		credential.setTemporary(false);

		getUsersResource().get(userId).resetPassword(credential);
		log.info("Password reset successfully for manager: {}", userId);
	}

	/**
	 * Convert UserRepresentation to Map
	 */
	private Map<String, Object> userToMap(UserRepresentation user) {
		Map<String, Object> map = new HashMap<>();
		map.put("id", user.getId());
		map.put("username", user.getUsername());
		map.put("email", user.getEmail());
		map.put("firstName", user.getFirstName());
		map.put("lastName", user.getLastName());
		map.put("enabled", user.isEnabled());
		map.put("emailVerified", user.isEmailVerified());
		map.put("attributes", user.getAttributes());
		return map;
	}

	/**
	 * Extract user ID from Location header
	 */
	private String extractUserIdFromLocation(String path) {
		return path.substring(path.lastIndexOf('/') + 1);
	}

}
