package com.werkflow.admin.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.time.Duration;

/**
 * RestTemplate beans for the admin-service.
 *
 * <p>The {@code @Primary} bean is unauthenticated — used for legacy engine calls
 * (DMN XML lookup, Tier 1 role mappings) that hit permitAll endpoints.
 *
 * <p>The {@code serviceRestTemplate} bean attaches a Keycloak client-credentials
 * Bearer token (registration id {@code werkflow-admin}) on every request and is
 * required for any admin → engine call into a role-gated internal endpoint
 * (M4.12 B.2 credential test wiring).
 */
@Configuration
public class RestTemplateConfig {

    /**
     * Default RestTemplate — used for unauthenticated or token-forwarding calls.
     */
    @Bean
    @Primary
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
            .setConnectTimeout(Duration.ofSeconds(10))
            .setReadTimeout(Duration.ofSeconds(30))
            .build();
    }

    /**
     * RestTemplate for internal service-to-service calls.
     * Automatically obtains a client-credentials token from Keycloak as the
     * {@code werkflow-admin} service account and attaches it as a Bearer header.
     */
    @Bean("serviceRestTemplate")
    public RestTemplate serviceRestTemplate(RestTemplateBuilder builder,
                                            OAuth2AuthorizedClientManager authorizedClientManager) {
        return builder
            .setConnectTimeout(Duration.ofSeconds(5))
            .setReadTimeout(Duration.ofSeconds(30))
            .additionalInterceptors(new ClientCredentialsInterceptor(authorizedClientManager, "werkflow-admin"))
            .build();
    }

    @Bean
    public OAuth2AuthorizedClientManager authorizedClientManager(
            ClientRegistrationRepository clientRegistrationRepository,
            OAuth2AuthorizedClientService authorizedClientService) {
        return new AuthorizedClientServiceOAuth2AuthorizedClientManager(
                clientRegistrationRepository, authorizedClientService);
    }

    private static class ClientCredentialsInterceptor implements ClientHttpRequestInterceptor {

        private final OAuth2AuthorizedClientManager manager;
        private final String registrationId;

        ClientCredentialsInterceptor(OAuth2AuthorizedClientManager manager, String registrationId) {
            this.manager = manager;
            this.registrationId = registrationId;
        }

        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                            ClientHttpRequestExecution execution) throws IOException {
            OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest
                    .withClientRegistrationId(registrationId)
                    .principal(registrationId)
                    .build();
            OAuth2AuthorizedClient client = manager.authorize(authorizeRequest);
            if (client == null || client.getAccessToken() == null) {
                throw new IllegalStateException(
                    "Failed to obtain service access token for client_credentials registration: "
                        + registrationId);
            }
            request.getHeaders().setBearerAuth(client.getAccessToken().getTokenValue());
            return execution.execute(request, body);
        }
    }
}
