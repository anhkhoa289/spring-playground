package com.khoa.spring.playground.service;

import java.util.List;
import java.util.Map;

/**
 * Interface for Manager Identity and Access Management (IAM) operations
 * Provides abstraction for manager/admin management, roles, and authorization
 * Connected to Manager Keycloak instance
 */
public interface IIAMManagerService {

	/**
	 * Create a new manager in the IAM system
	 *
	 * @param username Username for the new manager
	 * @param email Manager email address
	 * @param firstName Manager first name
	 * @param lastName Manager last name
	 * @param password Initial password
	 * @return Manager ID in the IAM system
	 */
	String createUser(String username, String email, String firstName, String lastName, String password);

	/**
	 * Update manager attributes
	 *
	 * @param userId Manager ID
	 * @param attributes Map of attributes to update
	 */
	void updateUser(String userId, Map<String, String> attributes);

	/**
	 * Delete a manager from the IAM system
	 *
	 * @param userId Manager ID to delete
	 */
	void deleteUser(String userId);

	/**
	 * Get manager details by ID
	 *
	 * @param userId Manager ID
	 * @return Map containing manager details
	 */
	Map<String, Object> getUserById(String userId);

	/**
	 * Get manager details by username
	 *
	 * @param username Username
	 * @return Map containing manager details
	 */
	Map<String, Object> getUserByUsername(String username);

	/**
	 * Assign roles to a manager
	 *
	 * @param userId Manager ID
	 * @param roleNames List of role names to assign
	 */
	void assignRoles(String userId, List<String> roleNames);

	/**
	 * Remove roles from a manager
	 *
	 * @param userId Manager ID
	 * @param roleNames List of role names to remove
	 */
	void removeRoles(String userId, List<String> roleNames);

	/**
	 * Get manager roles
	 *
	 * @param userId Manager ID
	 * @return List of role names
	 */
	List<String> getUserRoles(String userId);

	/**
	 * Check if manager has a specific role
	 *
	 * @param userId Manager ID
	 * @param roleName Role name to check
	 * @return true if manager has the role
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
	 * Get manager permissions
	 *
	 * @param userId Manager ID
	 * @return List of permissions
	 */
	List<String> getUserPermissions(String userId);

	/**
	 * Check if manager has a specific permission
	 *
	 * @param userId Manager ID
	 * @param permission Permission to check
	 * @return true if manager has the permission
	 */
	boolean hasPermission(String userId, String permission);

	/**
	 * Enable or disable manager account
	 *
	 * @param userId Manager ID
	 * @param enabled true to enable, false to disable
	 */
	void setUserEnabled(String userId, boolean enabled);

	/**
	 * Reset manager password
	 *
	 * @param userId Manager ID
	 * @param newPassword New password
	 */
	void resetPassword(String userId, String newPassword);

}
