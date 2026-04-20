package com.werkflow.common.security;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SsrfGuardTest {

    private final SsrfGuard guard = new SsrfGuard();

    @Test
    void validate_acceptsPublicHttpsUrl() {
        // Uses a literal public IP to avoid DNS resolution issues in CI
        assertThatNoException().isThrownBy(() -> guard.validate("https://1.1.1.1/"));
    }

    @Test
    void validate_rejectsHttp() {
        assertThatThrownBy(() -> guard.validate("http://api.example.com/data"))
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("https");
    }

    @Test
    void validate_rejectsLoopback() {
        assertThatThrownBy(() -> guard.validate("https://127.0.0.1/api"))
            .isInstanceOf(SecurityException.class);
    }

    @Test
    void validate_rejectsPrivateRange10() {
        assertThatThrownBy(() -> guard.validate("https://10.0.0.1/api"))
            .isInstanceOf(SecurityException.class);
    }

    @Test
    void validate_rejectsPrivateRange192() {
        assertThatThrownBy(() -> guard.validate("https://192.168.1.1/api"))
            .isInstanceOf(SecurityException.class);
    }

    @Test
    void validate_rejectsLinkLocal() {
        assertThatThrownBy(() -> guard.validate("https://169.254.169.254/latest/meta-data"))
            .isInstanceOf(SecurityException.class);
    }

    @Test
    void validate_rejectsFileScheme() {
        assertThatThrownBy(() -> guard.validate("file:///etc/passwd"))
            .isInstanceOf(SecurityException.class);
    }

    @Test
    void validate_rejectsMalformedUrl() {
        assertThatThrownBy(() -> guard.validate("not-a-url"))
            .isInstanceOf(SecurityException.class);
    }

    @Test
    void validateExternal_allowsPrivateIp() {
        // Registry-controlled internal service on private IP range — must not throw
        assertThatCode(() -> guard.validateExternal("http://10.0.0.5:8085/api/create-po"))
            .doesNotThrowAnyException();
    }

    @Test
    void validateExternal_allowsSiteLocalRange() {
        assertThatCode(() -> guard.validateExternal("http://192.168.1.100/api"))
            .doesNotThrowAnyException();
    }

    @Test
    void validateExternal_allowsHttpScheme() {
        // Uses a literal public IP to verify HTTP scheme is permitted without relying on DNS
        assertThatCode(() -> guard.validateExternal("http://1.1.1.1/api/v1"))
            .doesNotThrowAnyException();
    }

    @Test
    void validateExternal_stillBlocksLoopback() {
        assertThatThrownBy(() -> guard.validateExternal("http://127.0.0.1/api"))
            .isInstanceOf(SecurityException.class);
    }

    @Test
    void validateExternal_stillBlocksLinkLocal() {
        assertThatThrownBy(() -> guard.validateExternal("http://169.254.1.1/api"))
            .isInstanceOf(SecurityException.class);
    }

    @Test
    void validateExternal_rejectsMalformedUrl() {
        assertThatThrownBy(() -> guard.validateExternal("not-a-url"))
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("Invalid URL");
    }
}
