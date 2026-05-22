package com.werkflow.engine.action.credential;

import com.werkflow.engine.action.credential.CredentialField.FieldType;
import com.werkflow.engine.action.credential.types.SendGridApiCredential;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SendGridApiCredentialTest {

    private SendGridApiCredential credential;

    @BeforeEach
    void setUp() {
        credential = new SendGridApiCredential();
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
        var names = credential.fields().stream().map(CredentialField::name).toList();
        assertThat(names).containsExactly("apiKey");
    }

    @Test
    @DisplayName("apiKey field is SECRET type")
    void fields_apiKeyIsSecret() {
        assertThat(credential.fields().get(0).type()).isEqualTo(FieldType.SECRET);
    }

    @Test
    @DisplayName("apiKey field is required")
    void fields_apiKeyIsRequired() {
        assertThat(credential.fields()).allMatch(CredentialField::required);
    }

    @Test
    @DisplayName("name() returns 'sendgrid-api'")
    void name_returnsSendgridApi() {
        assertThat(credential.name()).isEqualTo("sendgrid-api");
    }

    @Test
    @DisplayName("displayName() returns 'SendGrid API'")
    void displayName_returnsLabel() {
        assertThat(credential.displayName()).isEqualTo("SendGrid API");
    }

    // -- validate: happy path --

    @Test
    @DisplayName("validate() returns ok when apiKey is present")
    void validate_apiKeyPresent_returnsOk() {
        var values = CredentialValues.of(Map.of("apiKey", "SG.abc123"));
        TestResult result = credential.validate(values);
        assertThat(result.success()).isTrue();
        assertThat(result.message()).isEqualTo("OK");
    }

    // -- validate: missing / blank apiKey --

    @Test
    @DisplayName("validate() returns error when 'apiKey' is missing")
    void validate_missingApiKey_returnsError() {
        var values = CredentialValues.of(Map.of());
        TestResult result = credential.validate(values);
        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("apiKey");
    }

    @Test
    @DisplayName("validate() returns error when 'apiKey' is blank")
    void validate_blankApiKey_returnsError() {
        var values = CredentialValues.of(Map.of("apiKey", ""));
        TestResult result = credential.validate(values);
        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("apiKey");
    }

    // -- applyTo --

    @Test
    @DisplayName("applyTo() sets Authorization: Bearer header with the full apiKey value")
    void applyTo_setsBearerAuthorizationHeader() {
        var values = CredentialValues.of(Map.of("apiKey", "SG.abc123"));
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create("https://api.sendgrid.com/v3/mail/send")).POST(HttpRequest.BodyPublishers.noBody());
        credential.applyTo(builder, values);
        HttpRequest request = builder.build();

        assertThat(request.headers().firstValue("Authorization"))
            .hasValue("Bearer SG.abc123");
    }

    @Test
    @DisplayName("applyTo() sets the Authorization header exactly once")
    void applyTo_setsHeaderExactlyOnce() {
        var values = CredentialValues.of(Map.of("apiKey", "SG.abc123"));
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create("https://api.sendgrid.com/v3/mail/send")).POST(HttpRequest.BodyPublishers.noBody());
        credential.applyTo(builder, values);
        HttpRequest request = builder.build();

        assertThat(request.headers().allValues("Authorization")).hasSize(1);
    }

    @Test
    @DisplayName("applyTo() preserves the full apiKey including the 'SG.' prefix without splitting or stripping")
    void applyTo_preservesFullApiKeyWithPrefix() {
        var values = CredentialValues.of(Map.of("apiKey", "SG.abc123"));
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create("https://api.sendgrid.com/v3/mail/send")).POST(HttpRequest.BodyPublishers.noBody());
        credential.applyTo(builder, values);
        HttpRequest request = builder.build();

        assertThat(request.headers().firstValue("Authorization"))
            .hasValue("Bearer SG.abc123");
    }
}
