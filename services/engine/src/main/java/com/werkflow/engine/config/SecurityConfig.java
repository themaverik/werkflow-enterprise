package com.werkflow.engine.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import com.werkflow.engine.security.PermissionConfig;
import com.werkflow.engine.security.WerkflowPermissionEvaluator;
import org.springframework.beans.factory.annotation.Value;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Security configuration for the Engine Service
 * Configures OAuth2 resource server with JWT token validation
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@EnableConfigurationProperties(PermissionConfig.class)
public class SecurityConfig {

    @Value("${werkflow.security.expose-management-endpoints:false}")
    private boolean exposeManagementEndpoints;

    @Bean
    public MethodSecurityExpressionHandler methodSecurityExpressionHandler(
            WerkflowPermissionEvaluator evaluator) {
        DefaultMethodSecurityExpressionHandler handler =
            new DefaultMethodSecurityExpressionHandler();
        handler.setPermissionEvaluator(evaluator);
        return handler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> {
                if (exposeManagementEndpoints) {
                    auth
                        .requestMatchers(new AntPathRequestMatcher("/actuator/**")).permitAll()
                        .requestMatchers(new AntPathRequestMatcher("/v3/api-docs/**")).permitAll()
                        .requestMatchers(new AntPathRequestMatcher("/swagger-ui/**")).permitAll()
                        .requestMatchers(new AntPathRequestMatcher("/swagger-ui.html")).permitAll();
                } else {
                    auth
                        .requestMatchers(new AntPathRequestMatcher("/actuator/**")).authenticated()
                        .requestMatchers(new AntPathRequestMatcher("/v3/api-docs/**")).authenticated()
                        .requestMatchers(new AntPathRequestMatcher("/swagger-ui/**")).authenticated()
                        .requestMatchers(new AntPathRequestMatcher("/swagger-ui.html")).authenticated();
                }
                auth
                    .requestMatchers(new AntPathRequestMatcher("/process-definitions/**")).authenticated()
                    .requestMatchers(new AntPathRequestMatcher("/api/process-definitions/**")).authenticated()
                    .requestMatchers(new AntPathRequestMatcher("/api/process-instances/**")).authenticated()
                    .requestMatchers(new AntPathRequestMatcher("/api/tasks/**")).authenticated()
                    .requestMatchers(new AntPathRequestMatcher("/api/v1/tasks/**")).authenticated()
                    .requestMatchers(new AntPathRequestMatcher("/workflows/tasks/**")).authenticated()
                    .requestMatchers(new AntPathRequestMatcher("/workflows/activity/**")).authenticated()
                    .requestMatchers(new AntPathRequestMatcher("/workflows/processes/**")).authenticated()
                    .requestMatchers(new AntPathRequestMatcher("/api/history/**")).authenticated()
                    .requestMatchers(new AntPathRequestMatcher("/forms/**")).authenticated()
                    .requestMatchers(new AntPathRequestMatcher("/api/forms/**")).authenticated()
                    .requestMatchers(new AntPathRequestMatcher("/api/services/**")).authenticated()
                    .requestMatchers(new AntPathRequestMatcher("/werkflow/api/**")).authenticated()
                    .anyRequest().authenticated();
            })
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
            );

        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            // Extract realm roles
            Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
            Collection<String> realmRoles = realmAccess != null
                ? (Collection<String>) realmAccess.get("roles")
                : List.of();

            // Extract resource roles
            Map<String, Object> resourceAccess = jwt.getClaimAsMap("resource_access");
            Collection<String> resourceRoles = List.of();
            if (resourceAccess != null) {
                Map<String, Object> resource = (Map<String, Object>) resourceAccess.get("werkflow-engine");
                if (resource != null) {
                    resourceRoles = (Collection<String>) resource.get("roles");
                }
            }

            // Combine all roles and add ROLE_ prefix
            return realmRoles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                .collect(Collectors.toSet());
        });

        return converter;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(
            "http://localhost:4000",  // Admin Portal
            "http://localhost:4001"   // HR Portal
        ));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
