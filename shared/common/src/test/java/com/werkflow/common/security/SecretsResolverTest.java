package com.werkflow.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.*;

class SecretsResolverTest {

    @Test
    void resolve_returnsValueForAllowedKey() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("werkflow.secrets.my.api.key", "secret-value");

        SecretsResolver resolver = new SecretsResolver(env, "my.api.key");
        assertThat(resolver.resolve("my.api.key")).isEqualTo("secret-value");
    }

    @Test
    void resolve_throwsSecurityExceptionForDeniedKey() {
        MockEnvironment env = new MockEnvironment();
        SecretsResolver resolver = new SecretsResolver(env, "allowed.key");

        assertThatThrownBy(() -> resolver.resolve("evil.key"))
            .isInstanceOf(SecurityException.class)
            .hasMessage("Secret reference is not permitted.");
    }

    @Test
    void resolve_throwsIllegalArgumentWhenSecretMissing() {
        MockEnvironment env = new MockEnvironment();
        SecretsResolver resolver = new SecretsResolver(env, "my.key");

        assertThatThrownBy(() -> resolver.resolve("my.key"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Secret not found.");
    }

    @Test
    void resolve_allowsAllKeysWhenAllowlistEmpty() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("werkflow.secrets.any.key", "value");
        SecretsResolver resolver = new SecretsResolver(env, "");

        assertThat(resolver.resolve("any.key")).isEqualTo("value");
    }

    @Test
    void resolve_returnsNullForNullInput() {
        SecretsResolver resolver = new SecretsResolver(new MockEnvironment(), "");
        assertThat(resolver.resolve(null)).isNull();
    }

    @Test
    void resolve_errorMessageDoesNotLeakAllowlistContents() {
        MockEnvironment env = new MockEnvironment();
        SecretsResolver resolver = new SecretsResolver(env, "allowed.key");

        assertThatThrownBy(() -> resolver.resolve("evil.key"))
            .hasMessage("Secret reference is not permitted.")
            .hasMessageNotContaining("allowed.key")
            .hasMessageNotContaining("evil.key");
    }

    @Test
    void isKeyAllowed_returnsTrueWhenKeyInAllowedList() {
        MockEnvironment env = new MockEnvironment();
        SecretsResolver resolver = new SecretsResolver(env, "finance.api.key,hr.api.key");
        assertThat(resolver.isKeyAllowed("finance.api.key")).isTrue();
    }

    @Test
    void isKeyAllowed_returnsFalseForKeyNotInAllowedList() {
        MockEnvironment env = new MockEnvironment();
        SecretsResolver resolver = new SecretsResolver(env, "finance.api.key,hr.api.key");
        assertThat(resolver.isKeyAllowed("unknown.key")).isFalse();
    }

    @Test
    void isKeyAllowed_returnsFalseForNullInput() {
        MockEnvironment env = new MockEnvironment();
        SecretsResolver resolver = new SecretsResolver(env, "finance.api.key");
        assertThat(resolver.isKeyAllowed(null)).isFalse();
    }

    @Test
    void isKeyAllowed_returnsFalseForBlankInput() {
        MockEnvironment env = new MockEnvironment();
        SecretsResolver resolver = new SecretsResolver(env, "finance.api.key");
        assertThat(resolver.isKeyAllowed("   ")).isFalse();
    }

    @Test
    void isKeyAllowed_returnsTrueWhenAllowedListEmpty() {
        MockEnvironment env = new MockEnvironment();
        SecretsResolver resolver = new SecretsResolver(env, "");
        assertThat(resolver.isKeyAllowed("any.key")).isTrue();
    }
}
