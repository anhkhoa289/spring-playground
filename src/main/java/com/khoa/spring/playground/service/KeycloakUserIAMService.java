package com.khoa.spring.playground.service;

import com.khoa.spring.playground.config.UserKeycloakConfig;
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
 * Keycloak implementation of User IAM service
 * Handles user management, roles, and authorization using User Keycloak instance
 */
@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "keycloak.user.enabled", havingValue = "true")
@ConditionalOnBean(UserKeycloakConfig.class)
public class KeycloakUserIAMService implements IIAMUserService {

	@Qualifier("userKeycloakAdminClient")
	private final Keycloak keycloakAdminClient;

	@Qualifier("userAuthzClient")
	private final AuthzClient authzClient;

	private final UserKeycloakConfig userConfig;

	/**
	 * Get realm resource for the configured realm
	 */
	private RealmResource getRealmResource() {
		return keycloakAdminClient.realm(userConfig.getRealm());
	}

	/**
	 * Get users resource for the configured realm
	 */
	private UsersResource getUsersResource() {
		return getRealmResource().users();
	}

	@Override
	public String createUser(String username, String email, String firstName, String lastName, String password) {
		log.info("Creating user: {}", username);

		UserRepresentation user = new UserRepresentation();
		user.setUsername(username);
		user.setEmail(email);
		user.setFirstName(firstName);
		user.setLastName(lastName);
		user.setEnabled(true);
		user.setEmailVerified(false);

		try (Response response = getUsersResource().create(user)) {
			if (response.getStatus() != 201) {
				throw new RuntimeException("Failed to create user. Status: " + response.getStatus());
			}

			String userId = extractUserIdFromLocation(response.getLocation().getPath());

			// Set password
			if (password != null && !password.isEmpty()) {
				resetPassword(userId, password);
			}

			log.info("User created successfully: {} with ID: {}", username, userId);
			return userId;
		}
	}

	@Override
	public void updateUser(String userId, Map<String, String> attributes) {
		log.info("Updating user: {}", userId);

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
		log.info("User updated successfully: {}", userId);
	}

	@Override
	public void deleteUser(String userId) {
		log.info("Deleting user: {}", userId);
		getUsersResource().get(userId).remove();
		log.info("User deleted successfully: {}", userId);
	}

	@Override
	public Map<String, Object> getUserById(String userId) {
		log.debug("Getting user by ID: {}", userId);
		UserRepresentation user = getUsersResource().get(userId).toRepresentation();
		return userToMap(user);
	}

	@Override
	public Map<String, Object> getUserByUsername(String username) {
		log.debug("Getting user by username: {}", username);
		List<UserRepresentation> users = getUsersResource().search(username, true);
		if (users.isEmpty()) {
			throw new RuntimeException("User not found: " + username);
		}
		return userToMap(users.get(0));
	}

	@Override
	public void assignRoles(String userId, List<String> roleNames) {
		log.info("Assigning roles to user {}: {}", userId, roleNames);

		UserResource userResource = getUsersResource().get(userId);
		List<RoleRepresentation> roles = roleNames.stream()
			.map(roleName -> getRealmResource().roles().get(roleName).toRepresentation())
			.collect(Collectors.toList());

		userResource.roles().realmLevel().add(roles);
		log.info("Roles assigned successfully to user: {}", userId);
	}

	@Override
	public void removeRoles(String userId, List<String> roleNames) {
		log.info("Removing roles from user {}: {}", userId, roleNames);

		UserResource userResource = getUsersResource().get(userId);
		List<RoleRepresentation> roles = roleNames.stream()
			.map(roleName -> getRealmResource().roles().get(roleName).toRepresentation())
			.collect(Collectors.toList());

		userResource.roles().realmLevel().remove(roles);
		log.info("Roles removed successfully from user: {}", userId);
	}

	@Override
	public List<String> getUserRoles(String userId) {
		log.debug("Getting roles for user: {}", userId);
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
		log.debug("Checking if user {} has role: {}", userId, roleName);
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
		log.debug("Getting permissions for user: {}", userId);
		// This would typically involve evaluating policies and permissions
		// For now, returning roles as permissions
		return getUserRoles(userId);
	}

	@Override
	public boolean hasPermission(String userId, String permission) {
		log.debug("Checking if user {} has permission: {}", userId, permission);
		return getUserPermissions(userId).contains(permission);
	}

	@Override
	public void setUserEnabled(String userId, boolean enabled) {
		log.info("Setting user {} enabled status to: {}", userId, enabled);

		UserResource userResource = getUsersResource().get(userId);
		UserRepresentation user = userResource.toRepresentation();
		user.setEnabled(enabled);
		userResource.update(user);

		log.info("User enabled status updated successfully: {}", userId);
	}

	@Override
	public void resetPassword(String userId, String newPassword) {
		log.info("Resetting password for user: {}", userId);

		CredentialRepresentation credential = new CredentialRepresentation();
		credential.setType(CredentialRepresentation.PASSWORD);
		credential.setValue(newPassword);
		credential.setTemporary(false);

		getUsersResource().get(userId).resetPassword(credential);
		log.info("Password reset successfully for user: {}", userId);
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
