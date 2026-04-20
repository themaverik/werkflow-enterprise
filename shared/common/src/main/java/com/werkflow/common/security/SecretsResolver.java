package com.werkflow.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class SecretsResolver {

    private static final String ALLOWED_PREFIX = "werkflow.secrets.";

    private final Environment environment;
    private final Set<String> allowedKeys;

    public SecretsResolver(Environment environment,
                           @Value("${werkflow.secrets.allowed-keys:}") String allowedKeysCsv) {
        this.environment = environment;
        this.allowedKeys = allowedKeysCsv == null || allowedKeysCsv.isBlank()
            ? Set.of()
            : Set.of(allowedKeysCsv.split("\\s*,\\s*"));
    }

    public String resolve(String key) {
        if (key == null || key.isBlank()) return null;
        if (!allowedKeys.isEmpty() && !allowedKeys.contains(key)) {
            throw new SecurityException("Secret reference is not permitted.");
        }
        String value = environment.getProperty(ALLOWED_PREFIX + key);
        if (value == null) {
            throw new IllegalArgumentException("Secret not found.");
        }
        return value;
    }

    public boolean isKeyAllowed(String key) {
        if (key == null || key.isBlank()) return false;
        return allowedKeys.isEmpty() || allowedKeys.contains(key);
    }
}
