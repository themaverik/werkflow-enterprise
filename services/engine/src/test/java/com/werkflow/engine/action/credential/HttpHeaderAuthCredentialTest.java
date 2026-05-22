package com.werkflow.engine.action.credential;

import com.werkflow.engine.action.credential.CredentialField.FieldType;
import com.werkflow.engine.action.credential.types.HttpHeaderAuthCredential;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HttpHeaderAuthCredentialTest {

    private HttpHeaderAuthCredential credential;

    @BeforeEach
    void setUp() {
        credential = new HttpHeaderAuthCredential();
    }

    // -- field shape --

    @Test
    @DisplayName("fields() returns exactly 2 entries")
    void fields_returnsExactlyTwoFields() {
        assertThat(credential.fields()).hasSize(2);
    }

    @Test
    @DisplayName("fields() has correct names in order")
    void fields_correctNames() {
        var names = credential.fields().stream().map(CredentialField::name).toList();
        assertThat(names).containsExactly("headerName", "headerValue");
    }

    @Test
    @DisplayName("headerName field is STRING type and headerValue field is SECRET type")
    void fields_correctTypes() {
        assertThat(credential.fields().get(0).type()).isEqualTo(FieldType.STRING);
        assertThat(credential.fields().get(1).type()).isEqualTo(FieldType.SECRET);
    }

    @Test
    @DisplayName("both fields are required")
    void fields_bothRequired() {
        assertThat(credential.fields()).allMatch(CredentialField::required);
    }

    @Test
    @DisplayName("name() returns 'http-header-auth'")
    void name_returnsHttpHeaderAuth() {
        assertThat(credential.name()).isEqualTo("http-header-auth");
    }

    @Test
    @DisplayName("displayName() returns 'HTTP Header Auth'")
    void displayName_returnsLabel() {
        assertThat(credential.displayName()).isEqualTo("HTTP Header Auth");
    }

    // -- validate: happy path --

    @Test
    @DisplayName("validate() returns ok when both headerName and headerValue are present")
    void validate_allFieldsPresent_returnsOk() {
        var values = CredentialValues.of(Map.of(
            "headerName",  "X-Api-Key",
            "headerValue", "sk-test-123"
        ));
        TestResult result = credential.validate(values);
        assertThat(result.success()).isTrue();
        assertThat(result.message()).isEqualTo("OK");
    }

    // -- validate: each missing required field --

    @Test
    @DisplayName("validate() returns error when 'headerName' is missing")
    void validate_missingHeaderName_returnsError() {
        var values = CredentialValues.of(Map.of("headerValue", "sk-test-123"));
        TestResult result = credential.validate(values);
        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("headerName");
    }

    @Test
    @DisplayName("validate() returns error when 'headerValue' is missing")
    void validate_missingHeaderValue_returnsError() {
        var values = CredentialValues.of(Map.of("headerName", "X-Api-Key"));
        TestResult result = credential.validate(values);
        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("headerValue");
    }

    // -- applyTo --

    @Test
    @DisplayName("applyTo() injects X-Api-Key header with the supplied value")
    void applyTo_injectsCustomApiKeyHeader() {
        var values = CredentialValues.of(Map.of(
            "headerName",  "X-Api-Key",
            "headerValue", "sk-test-123"
        ));
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create("https://example.test/")).GET();
        credential.applyTo(builder, values);
        HttpRequest request = builder.build();

        assertThat(request.headers().firstValue("X-Api-Key")).hasValue("sk-test-123");
        assertThat(request.headers().allValues("X-Api-Key")).hasSize(1);
    }

    @Test
    @DisplayName("applyTo() injects Authorization header when headerName is 'Authorization'")
    void applyTo_injectsAuthorizationHeaderWhenSpecified() {
        var values = CredentialValues.of(Map.of(
            "headerName",  "Authorization",
            "headerValue", "Bearer abc"
        ));
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create("https://example.test/")).GET();
        credential.applyTo(builder, values);
        HttpRequest request = builder.build();

        assertThat(request.headers().firstValue("Authorization")).hasValue("Bearer abc");
        assertThat(request.headers().allValues("Authorization")).hasSize(1);
    }

    @Test
    @DisplayName("applyTo() does not inject an Authorization header when headerName is something else")
    void applyTo_doesNotHardcodeAuthorizationHeader() {
        var values = CredentialValues.of(Map.of(
            "headerName",  "X-Api-Key",
            "headerValue", "sk-test-123"
        ));
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create("https://example.test/")).GET();
        credential.applyTo(builder, values);
        HttpRequest request = builder.build();

        assertThat(request.headers().allValues("Authorization")).isEmpty();
    }
}
