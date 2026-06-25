package com.werkflow.engine.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestTemplateConfig {

    /**
     * Default RestTemplate — used for unauthenticated / externally-authed calls
     * (e.g. service registry lookups forwarding a user token).
     */
    @Bean
    @Primary
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * RestTemplate for internal service-to-service calls.
     * Automatically obtains a client-credentials token from Keycloak and
     * attaches it as a Bearer header on every request.
     */
    @Bean("serviceRestTemplate")
    public RestTemplate serviceRestTemplate(RestTemplateBuilder builder,
                                            OAuth2AuthorizedClientManager authorizedClientManager,
                                            OAuth2AuthorizedClientService authorizedClientService) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(30))
                .additionalInterceptors(new ClientCredentialsInterceptor(
                        authorizedClientManager, authorizedClientService, "werkflow-engine"))
                .build();
    }

    @Bean
    public OAuth2AuthorizedClientManager authorizedClientManager(
            ClientRegistrationRepository clientRegistrationRepository,
            OAuth2AuthorizedClientService authorizedClientService) {
        return new AuthorizedClientServiceOAuth2AuthorizedClientManager(
                clientRegistrationRepository, authorizedClientService);
    }

}
