package com.werkflow.engine.action.credential;

import com.werkflow.engine.action.credential.CredentialField.FieldType;
import com.werkflow.engine.action.credential.types.HttpBasicAuthCredential;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HttpBasicAuthCredentialTest {

    private HttpBasicAuthCredential credential;

    @BeforeEach
    void setUp() {
        credential = new HttpBasicAuthCredential();
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
        assertThat(names).containsExactly("username", "password");
    }

    @Test
    @DisplayName("username field is STRING type and password field is SECRET type")
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
    @DisplayName("name() returns 'http-basic-auth'")
    void name_returnsHttpBasicAuth() {
        assertThat(credential.name()).isEqualTo("http-basic-auth");
    }

    @Test
    @DisplayName("displayName() returns 'HTTP Basic Auth'")
    void displayName_returnsLabel() {
        assertThat(credential.displayName()).isEqualTo("HTTP Basic Auth");
    }

    // -- validate: happy path --

    @Test
    @DisplayName("validate() returns ok when both username and password are present")
    void validate_allFieldsPresent_returnsOk() {
        var values = CredentialValues.of(Map.of(
            "username", "alice",
            "password", "s3cret"
        ));
        TestResult result = credential.validate(values);
        assertThat(result.success()).isTrue();
        assertThat(result.message()).isEqualTo("OK");
    }

    // -- validate: each missing required field --

    @Test
    @DisplayName("validate() returns error when 'username' is missing")
    void validate_missingUsername_returnsError() {
        var values = CredentialValues.of(Map.of("password", "s3cret"));
        TestResult result = credential.validate(values);
        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("username");
    }

    @Test
    @DisplayName("validate() returns error when 'password' is missing")
    void validate_missingPassword_returnsError() {
        var values = CredentialValues.of(Map.of("username", "alice"));
        TestResult result = credential.validate(values);
        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("password");
    }

    @Test
    @DisplayName("validate() returns error when 'username' is blank")
    void validate_blankUsername_returnsError() {
        var values = CredentialValues.of(Map.of(
            "username", "",
            "password", "s3cret"
        ));
        TestResult result = credential.validate(values);
        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("username");
    }

    @Test
    @DisplayName("validate() returns error when 'password' is blank")
    void validate_blankPassword_returnsError() {
        var values = CredentialValues.of(Map.of(
            "username", "alice",
            "password", ""
        ));
        TestResult result = credential.validate(values);
        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("password");
    }

    // -- applyTo --

    @Test
    @DisplayName("applyTo() sets Authorization: Basic header with correct Base64 encoding")
    void applyTo_setsCorrectBasicAuthHeader() {
        var values = CredentialValues.of(Map.of(
            "username", "alice",
            "password", "s3cret"
        ));
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create("https://example.test/")).GET();
        credential.applyTo(builder, values);
        HttpRequest request = builder.build();

        assertThat(request.headers().firstValue("Authorization"))
            .hasValue("Basic YWxpY2U6czNjcmV0");
    }

    @Test
    @DisplayName("applyTo() sets the Authorization header exactly once")
    void applyTo_setsHeaderExactlyOnce() {
        var values = CredentialValues.of(Map.of(
            "username", "alice",
            "password", "s3cret"
        ));
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create("https://example.test/")).GET();
        credential.applyTo(builder, values);
        HttpRequest request = builder.build();

        assertThat(request.headers().allValues("Authorization")).hasSize(1);
    }

    @Test
    @DisplayName("applyTo() does not inject any X-Api-Key or other extraneous headers")
    void applyTo_doesNotInjectExtraneousHeaders() {
        var values = CredentialValues.of(Map.of(
            "username", "alice",
            "password", "s3cret"
        ));
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create("https://example.test/")).GET();
        credential.applyTo(builder, values);
        HttpRequest request = builder.build();

        assertThat(request.headers().allValues("X-Api-Key")).isEmpty();
        assertThat(request.headers().map().keySet())
            .containsExactlyInAnyOrder("Authorization");
    }
}
