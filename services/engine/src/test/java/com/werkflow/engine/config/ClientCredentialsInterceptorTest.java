package com.werkflow.engine.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ClientCredentialsInterceptor}.
 * Uses {@link Duration#ZERO} as backoff to avoid real sleeps.
 */
@ExtendWith(MockitoExtension.class)
class ClientCredentialsInterceptorTest {

    private static final String REGISTRATION_ID = "werkflow-engine";

    @Mock
    private OAuth2AuthorizedClientManager manager;

    @Mock
    private OAuth2AuthorizedClientService authorizedClientService;

    @Mock
    private ClientHttpRequestExecution execution;

    @Mock
    private HttpRequest request;

    private ClientCredentialsInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new ClientCredentialsInterceptor(
                manager, authorizedClientService, REGISTRATION_ID, Duration.ZERO);
    }

    // -------------------------------------------------------------------------
    // (a) 401 then 200 — evicts client, re-executes once, returns 200
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("(a) 401 followed by 200: evicts cached client once and returns the 200 response")
    void intercept_401ThenSuccess_evictsClientAndReturns200() throws IOException {
        HttpHeaders headers = new HttpHeaders();
        when(request.getHeaders()).thenReturn(headers);

        OAuth2AuthorizedClient validClient = buildAuthorizedClient("initial-token");
        OAuth2AuthorizedClient freshClient = buildAuthorizedClient("fresh-token");

        when(manager.authorize(any(OAuth2AuthorizeRequest.class)))
                .thenReturn(validClient)
                .thenReturn(freshClient);

        ClientHttpResponse unauthorizedResponse =
                new MockClientHttpResponse(new byte[0], HttpStatus.UNAUTHORIZED);
        ClientHttpResponse okResponse =
                new MockClientHttpResponse(new byte[0], HttpStatus.OK);

        when(execution.execute(eq(request), any(byte[].class)))
                .thenReturn(unauthorizedResponse)
                .thenReturn(okResponse);

        ClientHttpResponse result = interceptor.intercept(request, new byte[0], execution);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        // The retry must carry the freshly-minted token, not the rejected one.
        assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer fresh-token");
        verify(authorizedClientService, times(1))
                .removeAuthorizedClient(REGISTRATION_ID, REGISTRATION_ID);
        verify(execution, times(2)).execute(eq(request), any(byte[].class));
    }

    // -------------------------------------------------------------------------
    // (b) manager throws on first call, succeeds on second — request completes
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("(b) manager throws OAuth2AuthorizationException on first attempt, succeeds on second")
    void acquireToken_firstAttemptThrows_secondAttemptSucceeds() throws IOException {
        when(request.getHeaders()).thenReturn(new HttpHeaders());

        OAuth2AuthorizedClient validClient = buildAuthorizedClient("retry-token");

        when(manager.authorize(any(OAuth2AuthorizeRequest.class)))
                .thenThrow(new OAuth2AuthorizationException(new OAuth2Error("invalid_token_response")))
                .thenReturn(validClient);

        ClientHttpResponse okResponse =
                new MockClientHttpResponse(new byte[0], HttpStatus.OK);
        when(execution.execute(eq(request), any(byte[].class))).thenReturn(okResponse);

        ClientHttpResponse result = interceptor.intercept(request, new byte[0], execution);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        verify(manager, times(2)).authorize(any(OAuth2AuthorizeRequest.class));
        verify(execution, times(1)).execute(eq(request), any(byte[].class));
    }

    // -------------------------------------------------------------------------
    // (c) manager always throws — IllegalStateException after 3 attempts
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("(c) manager always throws OAuth2AuthorizationException — IllegalStateException after 3 attempts")
    void acquireToken_alwaysThrows_throwsIllegalStateExceptionAfter3Attempts() {
        OAuth2AuthorizationException cause =
                new OAuth2AuthorizationException(new OAuth2Error("server_error"));

        when(manager.authorize(any(OAuth2AuthorizeRequest.class))).thenThrow(cause);

        assertThatThrownBy(() -> interceptor.intercept(request, new byte[0], execution))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("3 attempts")
                .hasMessageContaining(REGISTRATION_ID)
                .hasCause(cause);

        verify(manager, times(3)).authorize(any(OAuth2AuthorizeRequest.class));
    }

    // -------------------------------------------------------------------------
    // (d) manager returns null every attempt — IllegalStateException after 3 attempts
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("(d) manager returns null client every attempt — IllegalStateException after 3 attempts")
    void acquireToken_alwaysReturnsNull_throwsIllegalStateExceptionAfter3Attempts() {
        when(manager.authorize(any(OAuth2AuthorizeRequest.class))).thenReturn(null);

        assertThatThrownBy(() -> interceptor.intercept(request, new byte[0], execution))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("3 attempts")
                .hasMessageContaining(REGISTRATION_ID)
                .hasCauseInstanceOf(IllegalStateException.class);

        verify(manager, times(3)).authorize(any(OAuth2AuthorizeRequest.class));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private OAuth2AuthorizedClient buildAuthorizedClient(String tokenValue) {
        ClientRegistration registration = ClientRegistration
                .withRegistrationId(REGISTRATION_ID)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .clientId("werkflow-engine")
                .clientSecret("secret")
                .tokenUri("http://keycloak/token")
                .build();

        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                tokenValue,
                Instant.now(),
                Instant.now().plusSeconds(300));

        return new OAuth2AuthorizedClient(registration, REGISTRATION_ID, accessToken);
    }
}
