package com.werkflow.engine.config;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;

import java.io.IOException;
import java.time.Duration;

/**
 * {@link ClientHttpRequestInterceptor} that obtains a client-credentials Bearer token from
 * Keycloak and attaches it to every outgoing service-to-service request.
 *
 * <p>Resilience behaviour:
 * <ul>
 *   <li><b>Token acquisition</b> — retried up to {@value #MAX_TOKEN_ATTEMPTS} times with
 *       linear backoff ({@code backoff * attempt}) on {@link OAuth2AuthorizationException}
 *       or a null client / null access token.</li>
 *   <li><b>Downstream 401</b> — the cached client is evicted, a fresh token is acquired,
 *       and the request is re-executed exactly once. Subsequent 401 responses are returned
 *       as-is to the caller.</li>
 * </ul>
 */
class ClientCredentialsInterceptor implements ClientHttpRequestInterceptor {

    private static final int MAX_TOKEN_ATTEMPTS = 3;
    private static final long BACKOFF_BASE_MS = 200L;

    private final OAuth2AuthorizedClientManager manager;
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final String registrationId;
    private final Duration backoff;

    /**
     * Creates an interceptor with the default 200 ms linear-backoff base.
     *
     * @param manager              the OAuth2 client manager used to acquire tokens
     * @param authorizedClientService the service used to evict cached clients on 401
     * @param registrationId       the Spring Security client registration ID
     */
    ClientCredentialsInterceptor(OAuth2AuthorizedClientManager manager,
                                 OAuth2AuthorizedClientService authorizedClientService,
                                 String registrationId) {
        this(manager, authorizedClientService, registrationId, Duration.ofMillis(BACKOFF_BASE_MS));
    }

    /**
     * Creates an interceptor with a custom backoff duration (useful in tests to pass
     * {@link Duration#ZERO} and avoid real sleeps).
     *
     * @param manager              the OAuth2 client manager used to acquire tokens
     * @param authorizedClientService the service used to evict cached clients on 401
     * @param registrationId       the Spring Security client registration ID
     * @param backoff              base duration for linear backoff between token-acquisition retries
     */
    ClientCredentialsInterceptor(OAuth2AuthorizedClientManager manager,
                                 OAuth2AuthorizedClientService authorizedClientService,
                                 String registrationId,
                                 Duration backoff) {
        this.manager = manager;
        this.authorizedClientService = authorizedClientService;
        this.registrationId = registrationId;
        this.backoff = backoff;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        OAuth2AuthorizedClient client = acquireToken();
        request.getHeaders().setBearerAuth(client.getAccessToken().getTokenValue());

        ClientHttpResponse response = execution.execute(request, body);

        int status;
        try {
            status = response.getStatusCode().value();
        } catch (IOException | RuntimeException ex) {
            // getStatusCode() declares IOException — release the connection before propagating.
            response.close();
            throw ex;
        }

        if (status == 401) {
            // The cached token was rejected (e.g. a Keycloak key-rotation invalidated a
            // still-unexpired token). Evict it, mint a fresh one, and retry exactly once.
            response.close();
            authorizedClientService.removeAuthorizedClient(registrationId, registrationId);
            // setBearerAuth uses HttpHeaders.set() (replace, not add), so this overwrites the prior token.
            request.getHeaders().setBearerAuth(acquireToken().getAccessToken().getTokenValue());
            response = execution.execute(request, body);
        }

        return response;
    }

    /**
     * Acquires a valid {@link OAuth2AuthorizedClient} from the manager, retrying up to
     * {@value #MAX_TOKEN_ATTEMPTS} times with linear backoff on transient failures.
     *
     * <p>If the thread is interrupted during a backoff, the interrupt flag is restored
     * and the remaining attempts proceed without sleeping.
     *
     * @return a non-null {@link OAuth2AuthorizedClient} with a non-null access token
     * @throws IllegalStateException if all attempts fail
     */
    private OAuth2AuthorizedClient acquireToken() {
        OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest
                .withClientRegistrationId(registrationId)
                .principal(registrationId)
                .build();

        RuntimeException lastCause = null;
        for (int attempt = 1; attempt <= MAX_TOKEN_ATTEMPTS; attempt++) {
            try {
                OAuth2AuthorizedClient client = manager.authorize(authorizeRequest);
                if (client != null && client.getAccessToken() != null) {
                    return client;
                }
                lastCause = new IllegalStateException(
                        "manager.authorize returned null client or null access token for registration: "
                                + registrationId);
            } catch (OAuth2AuthorizationException ex) {
                lastCause = ex;
            }

            if (attempt < MAX_TOKEN_ATTEMPTS) {
                sleep(backoff.toMillis() * attempt);
            }
        }

        throw new IllegalStateException(
                "Failed to obtain service access token after " + MAX_TOKEN_ATTEMPTS
                        + " attempts for client_credentials registration: " + registrationId,
                lastCause);
    }

    private void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
