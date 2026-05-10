package com.werkflow.engine.action;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers a second bean alias {@code restConnectorDelegate} pointing to the same
 * {@link RestConnectorDelegate} instance as {@code externalApiCallDelegate}.
 *
 * <p>This allows new BPMNs to reference {@code restConnectorDelegate} while
 * deployed processes using the original {@code externalApiCallDelegate} name
 * continue working without modification.</p>
 */
@Configuration
class RestConnectorDelegateAlias {

    @Bean("restConnectorDelegate")
    RestConnectorDelegate restConnectorDelegateAlias(RestConnectorDelegate externalApiCallDelegate) {
        return externalApiCallDelegate;
    }
}
