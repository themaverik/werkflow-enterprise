package com.werkflow.admin.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationEventPublisher;
import org.springframework.security.authentication.DefaultAuthenticationEventPublisher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwkSetUri;

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    @Value("${app.cors.allowed-origins:http://localhost:4000,http://localhost:4001}")
    private String[] allowedOrigins;

    /** C-2: when false (default), actuator/Swagger require ADMIN/SUPER_ADMIN — never public. */
    @Value("${werkflow.security.expose-management-endpoints:false}")
    private boolean exposeManagementEndpoints;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable()) // stateless JWT API — no session cookies, CSRF not applicable
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> {
                // C-2: actuator/health is always public; all other actuator + Swagger endpoints
                // require authentication when exposeManagementEndpoints=false (the default).
                auth.requestMatchers("/actuator/health", "/actuator/health/**").permitAll();
                if (exposeManagementEndpoints) {
                    auth.requestMatchers("/actuator/**", "/api-docs/**", "/swagger-ui/**",
                            "/swagger-ui.html", "/v3/api-docs/**").permitAll();
                } else {
                    auth.requestMatchers("/actuator/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
                        .requestMatchers("/api-docs/**", "/swagger-ui/**",
                            "/swagger-ui.html", "/v3/api-docs/**").authenticated();
                }
                auth.requestMatchers("/health", "/health/**").permitAll()

                // Route configuration - authenticated users can read
                    .requestMatchers(HttpMethod.GET, "/api/routes/**").authenticated()

                // Service Registry - authenticated users can read, admins can write
                    .requestMatchers(HttpMethod.GET, "/api/services/**").authenticated()
                    .requestMatchers(HttpMethod.POST, "/api/services/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
                    .requestMatchers(HttpMethod.PUT, "/api/services/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
                    .requestMatchers(HttpMethod.DELETE, "/api/services/**").hasRole("SUPER_ADMIN")

                // User management - ADMIN only
                    .requestMatchers(HttpMethod.POST, "/api/v1/users/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
                    .requestMatchers(HttpMethod.PUT, "/api/v1/users/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/users/**").hasRole("SUPER_ADMIN")

                // Organization management - SUPER_ADMIN only
                    .requestMatchers(HttpMethod.POST, "/api/v1/organizations/**").hasRole("SUPER_ADMIN")
                    .requestMatchers(HttpMethod.PUT, "/api/v1/organizations/**").hasRole("SUPER_ADMIN")
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/organizations/**").hasRole("SUPER_ADMIN")

                // Role management - SUPER_ADMIN only
                    .requestMatchers(HttpMethod.POST, "/api/roles/**").hasRole("SUPER_ADMIN")
                    .requestMatchers(HttpMethod.PUT, "/api/roles/**").hasRole("SUPER_ADMIN")
                    .requestMatchers(HttpMethod.DELETE, "/api/roles/**").hasRole("SUPER_ADMIN")

                // Read operations - authenticated users
                    .requestMatchers(HttpMethod.GET, "/api/**").authenticated()

                // All other requests require authentication
                    .anyRequest().authenticated();
            })
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
                )
            );

        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        // HIGH-01: add issuer validation so tokens from foreign issuers are rejected
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuerUri));
        return decoder;
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRoleConverter());
        return converter;
    }

    @Bean
    public AuthenticationEventPublisher authenticationEventPublisher(
            ApplicationEventPublisher applicationEventPublisher) {
        return new DefaultAuthenticationEventPublisher(applicationEventPublisher);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(allowedOrigins));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    static class KeycloakRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {
        @Override
        public Collection<GrantedAuthority> convert(Jwt jwt) {
            List<String> allRoles = new java.util.ArrayList<>();

            // 1. Check realm_access.roles (Keycloak realm roles)
            Map<String, Object> realmAccess = jwt.getClaim("realm_access");
            if (realmAccess != null && realmAccess.containsKey("roles")) {
                @SuppressWarnings("unchecked")
                List<String> realmRoles = (List<String>) realmAccess.get("roles");
                allRoles.addAll(realmRoles);
            }

            // 2. Check resource_access[client_id].roles (Client-specific roles)
            Map<String, Object> resourceAccess = jwt.getClaim("resource_access");
            if (resourceAccess != null) {
                for (Object clientRolesObj : resourceAccess.values()) {
                    if (clientRolesObj instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> clientRoles = (Map<String, Object>) clientRolesObj;
                        if (clientRoles.containsKey("roles")) {
                            @SuppressWarnings("unchecked")
                            List<String> roles = (List<String>) clientRoles.get("roles");
                            allRoles.addAll(roles);
                        }
                    }
                }
            }

            // 3. Check custom roles claim (if present)
            List<String> customRoles = jwt.getClaimAsStringList("roles");
            if (customRoles != null) {
                allRoles.addAll(customRoles);
            }

            // Remove duplicates and convert to authorities
            return allRoles.stream()
                .distinct()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                .collect(Collectors.toList());
        }
    }
}
