package com.khoa.spring.playground.config;

import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.text.ParseException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Security configuration for JWT token verification
 * Configures endpoint protection and OAuth2 resource server
 * JWT decoders are configured in UserKeycloakConfig and ManagerKeycloakConfig
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@Slf4j
public class SecurityConfig {

	/**
	 * Configure security filter chain
	 * - Public endpoints: /api/auth/**, /actuator/health, /actuator/prometheus
	 * - Protected endpoints: All other /api/** endpoints require authentication
	 * - Uses authenticationManagerResolver for dynamic JWT decoder selection
	 */
	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http,
			AuthenticationManagerResolver<HttpServletRequest> authenticationManagerResolver) throws Exception {
		http
			// Disable CSRF for stateless REST API
			.csrf(AbstractHttpConfigurer::disable)

			// Configure session management as stateless (JWT-based)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

			// Configure authorization rules
			.authorizeHttpRequests(auth -> auth
				// Public endpoints - authentication not required
				.requestMatchers("/api/auth/**").permitAll()
				.requestMatchers("/api/manager-auth/**").permitAll()
				.requestMatchers("/api/role-test/public").permitAll()
				.requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
				.requestMatchers("/actuator/prometheus").permitAll()

				// All other /api/** endpoints require authentication
				.requestMatchers("/api/**").authenticated()

				// Permit all other requests (e.g., error pages)
				.anyRequest().permitAll())

			// Configure OAuth2 Resource Server with dynamic authentication manager resolver
			.oauth2ResourceServer(oauth2 -> oauth2.authenticationManagerResolver(authenticationManagerResolver));

		return http.build();
	}

	/**
	 * Authentication Manager Resolver that dynamically selects the appropriate JwtDecoder
	 * and JwtAuthenticationConverter based on the token's issuer claim.
	 * <p>
	 * This allows the application to support multiple Keycloak instances:
	 * - User Keycloak (port 8090) for regular users - adds ROLE_USER
	 * - Manager Keycloak (port 8091) for managers/admins - adds ROLE_MANAGER
	 * <p>
	 * The resolver extracts the issuer from the JWT token and matches it against
	 * configured issuer URIs to select the correct decoder and authentication provider.
	 * Each provider uses an issuer-specific converter that automatically adds the appropriate role.
	 */
	@Bean
	public AuthenticationManagerResolver<HttpServletRequest> authenticationManagerResolver(
			@Value("${keycloak.user.issuer-uri}") String userIssuerUri,
			@Value("${keycloak.manager.issuer-uri:}") String managerIssuerUri,
			@Qualifier("userJwtDecoder") @Autowired(required = false) JwtDecoder userJwtDecoder,
			@Qualifier("managerJwtDecoder") @Autowired(required = false) JwtDecoder managerJwtDecoder,
			@Qualifier("userJwtAuthenticationConverter") JwtAuthenticationConverter userJwtAuthenticationConverter,
			@Qualifier("managerJwtAuthenticationConverter") JwtAuthenticationConverter managerJwtAuthenticationConverter) {
		return request -> {
			String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
			String token = authHeader != null ? authHeader.replace("Bearer ", "") : "";

			JwtDecoder jwtDecoder;
			JwtAuthenticationConverter jwtAuthenticationConverter;
			try {
				String issuer = SignedJWT.parse(token).getJWTClaimsSet().getIssuer();
				log.debug("Token issuer: {}", issuer);
				log.debug("User issuer URI: {}", userIssuerUri);
				log.debug("Manager issuer URI: {}", managerIssuerUri);

				if (Objects.equals(issuer, userIssuerUri)) {
					log.debug("Using User JwtDecoder and adding ROLE_USER");
					jwtDecoder = userJwtDecoder;
					jwtAuthenticationConverter = userJwtAuthenticationConverter;
				}
				else if (!managerIssuerUri.isEmpty() && managerJwtDecoder != null
						&& Objects.equals(issuer, managerIssuerUri)) {
					log.debug("Using Manager JwtDecoder and adding ROLE_MANAGER");
					jwtDecoder = managerJwtDecoder;
					jwtAuthenticationConverter = managerJwtAuthenticationConverter;
				}
				else {
					log.error("Invalid JWT Token Issuer: {}", issuer);
					throw new AuthenticationException("Invalid JWT Token Issuer: " + issuer) {
					};
				}
			}
			catch (ParseException e) {
				log.error("Failed to parse JWT token", e);
				throw new AuthenticationException("Invalid JWT Token") {
				};
			}

			JwtAuthenticationProvider authenticationProvider = new JwtAuthenticationProvider(jwtDecoder);
			authenticationProvider.setJwtAuthenticationConverter(jwtAuthenticationConverter);
			return authenticationProvider::authenticate;
		};
	}

	/**
	 * Convert JWT claims to Spring Security authorities for User Keycloak tokens
	 * Extracts roles from realm_access.roles and adds ROLE_USER
	 */
	@Bean(name = "userJwtAuthenticationConverter")
	public JwtAuthenticationConverter userJwtAuthenticationConverter() {
		return createJwtAuthenticationConverter("USER");
	}

	/**
	 * Convert JWT claims to Spring Security authorities for Manager Keycloak tokens
	 * Extracts roles from realm_access.roles and adds ROLE_MANAGER
	 */
	@Bean(name = "managerJwtAuthenticationConverter")
	public JwtAuthenticationConverter managerJwtAuthenticationConverter() {
		return createJwtAuthenticationConverter("MANAGER");
	}

	/**
	 * Create a custom JwtAuthenticationConverter that:
	 * 1. Extracts roles from realm_access.roles claim
	 * 2. Adds an additional role based on the issuer (USER or MANAGER)
	 *
	 * @param additionalRole The role to add (USER or MANAGER)
	 * @return Configured JwtAuthenticationConverter
	 */
	private JwtAuthenticationConverter createJwtAuthenticationConverter(String additionalRole) {
		JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
		grantedAuthoritiesConverter.setAuthorityPrefix("ROLE_");
		grantedAuthoritiesConverter.setAuthoritiesClaimName("realm_access.roles");

		JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();

		// Custom converter that adds the additional role
		jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(jwt -> {
			// Get authorities from realm_access.roles
			Collection<GrantedAuthority> authorities = grantedAuthoritiesConverter.convert(jwt);

			// Create a mutable set and add existing authorities
			Set<GrantedAuthority> allAuthorities = new HashSet<>();
			if (authorities != null) {
				allAuthorities.addAll(authorities);
			}

			// Add the additional role based on issuer
			allAuthorities.add(new SimpleGrantedAuthority("ROLE_" + additionalRole));

			return allAuthorities;
		});

		return jwtAuthenticationConverter;
	}

}
