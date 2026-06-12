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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationEventPublisher;
import org.springframework.security.authentication.DefaultAuthenticationEventPublisher;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    @Value("${werkflow.security.cors.allowed-origins:http://localhost:4000,http://localhost:4001}")
    private String[] corsAllowedOrigins;

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
            .csrf(csrf -> csrf.disable()) // stateless JWT API — no session cookies, CSRF not applicable
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
                        // /actuator/health/** is a public liveness/readiness probe — no token required.
                        // Covers /actuator/health, /actuator/health/liveness, /actuator/health/readiness.
                        // show-details=when-authorized ensures sensitive internals are withheld for
                        // unauthenticated callers; the portal health route relies on this returning 200.
                        .requestMatchers(new AntPathRequestMatcher("/actuator/health/**")).permitAll()
                        .requestMatchers(new AntPathRequestMatcher("/actuator/**")).authenticated()
                        .requestMatchers(new AntPathRequestMatcher("/v3/api-docs/**")).authenticated()
                        .requestMatchers(new AntPathRequestMatcher("/swagger-ui/**")).authenticated()
                        .requestMatchers(new AntPathRequestMatcher("/swagger-ui.html")).authenticated();
                }
                auth
                    // Webhook endpoints are secured by HMAC signature, not JWT
                    .requestMatchers(new AntPathRequestMatcher("/api/v1/webhooks/**")).permitAll()
                    // Internal service-to-service: read-only YAML config, no tenant data
                    .requestMatchers(new AntPathRequestMatcher("/api/v1/config/flowable-role-mappings")).permitAll()
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
                    // Internal S2S endpoints — restrict at filter-chain level as defence-in-depth
                    .requestMatchers(new AntPathRequestMatcher("/api/internal/**"))
                        .hasAnyRole("ADMIN_SERVICE", "SUPER_ADMIN")
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

            // HIGH-02: combine BOTH realm roles AND resource roles into the security context
            return Stream.concat(realmRoles.stream(), resourceRoles.stream())
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                .collect(Collectors.toSet());
        });

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
        // M-8: CORS origins driven from config (werkflow.security.cors.allowed-origins)
        configuration.setAllowedOrigins(Arrays.asList(corsAllowedOrigins));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
