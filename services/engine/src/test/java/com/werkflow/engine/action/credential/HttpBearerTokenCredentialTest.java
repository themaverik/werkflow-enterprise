package com.werkflow.engine.action.credential;

import com.werkflow.engine.action.credential.CredentialField.FieldType;
import com.werkflow.engine.action.credential.types.HttpBearerTokenCredential;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HttpBearerTokenCredentialTest {

    private HttpBearerTokenCredential credential;

    @BeforeEach
    void setUp() {
        credential = new HttpBearerTokenCredential();
    }

    // -- field shape --

    @Test
    @DisplayName("fields() returns exactly 1 entry")
    void fields_returnsExactlyOneField() {
        assertThat(credential.fields()).hasSize(1);
    }

    @Test
    @DisplayName("fields() has correct name")
    void fields_correctName() {
        assertThat(credential.fields().get(0).name()).isEqualTo("token");
    }

    @Test
    @DisplayName("token field is SECRET type and required")
    void fields_correctTypeAndRequired() {
        assertThat(credential.fields().get(0).type()).isEqualTo(FieldType.SECRET);
        assertThat(credential.fields().get(0).required()).isTrue();
    }

    @Test
    @DisplayName("name() returns 'http-bearer-token'")
    void name_returnsHttpBearerToken() {
        assertThat(credential.name()).isEqualTo("http-bearer-token");
    }

    @Test
    @DisplayName("displayName() returns 'HTTP Bearer Token'")
    void displayName_returnsLabel() {
        assertThat(credential.displayName()).isEqualTo("HTTP Bearer Token");
    }

    // -- validate --

    @Test
    @DisplayName("validate() returns ok when token is present")
    void validate_tokenPresent_returnsOk() {
        var values = CredentialValues.of(Map.of("token", "abc123"));
        TestResult result = credential.validate(values);
        assertThat(result.success()).isTrue();
        assertThat(result.message()).isEqualTo("OK");
    }

    @Test
    @DisplayName("validate() returns error when token is missing")
    void validate_missingToken_returnsError() {
        var values = CredentialValues.of(Map.of());
        TestResult result = credential.validate(values);
        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("token");
    }

    @Test
    @DisplayName("validate() returns error when token is blank")
    void validate_blankToken_returnsError() {
        var values = CredentialValues.of(Map.of("token", ""));
        TestResult result = credential.validate(values);
        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("token");
    }

    // -- applyTo --

    @Test
    @DisplayName("applyTo() sets Authorization: Bearer header with the token")
    void applyTo_setsBearerHeader() {
        var values = CredentialValues.of(Map.of("token", "abc123"));
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create("https://example.test/")).GET();
        credential.applyTo(builder, values);
        HttpRequest request = builder.build();

        assertThat(request.headers().firstValue("Authorization")).hasValue("Bearer abc123");
    }

    @Test
    @DisplayName("applyTo() sets the Authorization header exactly once and injects no extras")
    void applyTo_setsHeaderOnceNoExtras() {
        var values = CredentialValues.of(Map.of("token", "abc123"));
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create("https://example.test/")).GET();
        credential.applyTo(builder, values);
        HttpRequest request = builder.build();

        assertThat(request.headers().allValues("Authorization")).hasSize(1);
        assertThat(request.headers().map().keySet()).containsExactlyInAnyOrder("Authorization");
    }
}
