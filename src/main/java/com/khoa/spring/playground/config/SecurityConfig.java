package com.khoa.spring.playground.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for JWT token verification
 * Configures endpoint protection and OAuth2 resource server
 * JWT decoders are configured in UserKeycloakConfig and ManagerKeycloakConfig
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

	/**
	 * Configure security filter chain
	 * - Public endpoints: /api/auth/**, /actuator/health, /actuator/prometheus
	 * - Protected endpoints: All other /api/** endpoints require authentication
	 */
	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
			// Disable CSRF for stateless REST API
			.csrf(AbstractHttpConfigurer::disable)

			// Configure session management as stateless (JWT-based)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

			// Configure authorization rules
			.authorizeHttpRequests(auth -> auth
				// Public endpoints - authentication not required
				.requestMatchers("/api/auth/**").permitAll()
				.requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
				.requestMatchers("/actuator/prometheus").permitAll()

				// All other /api/** endpoints require authentication
				.requestMatchers("/api/**").authenticated()

				// Permit all other requests (e.g., error pages)
				.anyRequest().permitAll())

			// Configure OAuth2 Resource Server with JWT
			.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));

		return http.build();
	}

	/**
	 * Convert JWT claims to Spring Security authorities
	 * Extracts roles from realm_access.roles and resource_access claims
	 */
	@Bean
	public JwtAuthenticationConverter jwtAuthenticationConverter() {
		JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();

		// Configure authority prefix (default is SCOPE_)
		grantedAuthoritiesConverter.setAuthorityPrefix("ROLE_");

		// Extract authorities from realm_access.roles claim
		grantedAuthoritiesConverter.setAuthoritiesClaimName("realm_access.roles");

		JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
		jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);

		return jwtAuthenticationConverter;
	}

}
