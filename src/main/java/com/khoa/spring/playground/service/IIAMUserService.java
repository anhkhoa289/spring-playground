package com.khoa.spring.playground.service;

import java.util.List;
import java.util.Map;

/**
 * Interface for User Identity and Access Management (IAM) operations
 * Provides abstraction for regular user management, roles, and authorization
 * Connected to User Keycloak instance
 */
public interface IIAMUserService {

	/**
	 * Create a new user in the IAM system
	 *
	 * @param username Username for the new user
	 * @param email User email address
	 * @param firstName User first name
	 * @param lastName User last name
	 * @param password Initial password
	 * @return User ID in the IAM system
	 */
	String createUser(String username, String email, String firstName, String lastName, String password);

	/**
	 * Update user attributes
	 *
	 * @param userId User ID
	 * @param attributes Map of attributes to update
	 */
	void updateUser(String userId, Map<String, String> attributes);

	/**
	 * Delete a user from the IAM system
	 *
	 * @param userId User ID to delete
	 */
	void deleteUser(String userId);

	/**
	 * Get user details by ID
	 *
	 * @param userId User ID
	 * @return Map containing user details
	 */
	Map<String, Object> getUserById(String userId);

	/**
	 * Get user details by username
	 *
	 * @param username Username
	 * @return Map containing user details
	 */
	Map<String, Object> getUserByUsername(String username);

	/**
	 * Assign roles to a user
	 *
	 * @param userId User ID
	 * @param roleNames List of role names to assign
	 */
	void assignRoles(String userId, List<String> roleNames);

	/**
	 * Remove roles from a user
	 *
	 * @param userId User ID
	 * @param roleNames List of role names to remove
	 */
	void removeRoles(String userId, List<String> roleNames);

	/**
	 * Get user roles
	 *
	 * @param userId User ID
	 * @return List of role names
	 */
	List<String> getUserRoles(String userId);

	/**
	 * Check if user has a specific role
	 *
	 * @param userId User ID
	 * @param roleName Role name to check
	 * @return true if user has the role
	 */
	boolean hasRole(String userId, String roleName);

	/**
	 * Validate access token
	 *
	 * @param token Access token
	 * @return true if token is valid
	 */
	boolean validateToken(String token);

	/**
	 * Get user permissions
	 *
	 * @param userId User ID
	 * @return List of permissions
	 */
	List<String> getUserPermissions(String userId);

	/**
	 * Check if user has a specific permission
	 *
	 * @param userId User ID
	 * @param permission Permission to check
	 * @return true if user has the permission
	 */
	boolean hasPermission(String userId, String permission);

	/**
	 * Enable or disable user account
	 *
	 * @param userId User ID
	 * @param enabled true to enable, false to disable
	 */
	void setUserEnabled(String userId, boolean enabled);

	/**
	 * Reset user password
	 *
	 * @param userId User ID
	 * @param newPassword New password
	 */
	void resetPassword(String userId, String newPassword);

}
