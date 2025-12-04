package com.khoa.spring.playground.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for JWT token verification with Keycloak
 * Configures endpoint protection and OAuth2 resource server
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

	@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
	private String issuerUri;

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
	 * JWT Decoder bean for validating tokens with Keycloak
	 * Uses JWKS (JSON Web Key Set) endpoint from Keycloak
	 */
	@Bean
	public JwtDecoder jwtDecoder() {
		return NimbusJwtDecoder.withIssuerLocation(issuerUri).build();
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
