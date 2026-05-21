package com.werkflow.engine.action.credential;

import com.werkflow.engine.action.credential.CredentialField.FieldType;
import com.werkflow.engine.action.credential.types.WhatsAppBusinessCredential;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WhatsAppBusinessCredentialTest {

    private WhatsAppBusinessCredential whatsApp;

    @BeforeEach
    void setUp() {
        whatsApp = new WhatsAppBusinessCredential();
    }

    // -- field shape --

    @Test
    @DisplayName("fields() returns exactly 3 entries")
    void fields_returnsExactlyThreeFields() {
        assertThat(whatsApp.fields()).hasSize(3);
    }

    @Test
    @DisplayName("fields() has correct names in order")
    void fields_correctNames() {
        var names = whatsApp.fields().stream().map(CredentialField::name).toList();
        assertThat(names).containsExactly("accessToken", "phoneNumberId", "apiVersion");
    }

    @Test
    @DisplayName("fields() has correct types")
    void fields_correctTypes() {
        var types = whatsApp.fields().stream().map(CredentialField::type).toList();
        assertThat(types).containsExactly(FieldType.SECRET, FieldType.STRING, FieldType.STRING);
    }

    @Test
    @DisplayName("all 3 fields are required")
    void fields_allRequired() {
        assertThat(whatsApp.fields()).allMatch(CredentialField::required);
    }

    @Test
    @DisplayName("apiVersion default is 'v18.0'")
    void fields_apiVersionDefault() {
        var apiVersion = whatsApp.fields().stream()
            .filter(f -> "apiVersion".equals(f.name()))
            .findFirst().orElseThrow();
        assertThat(apiVersion.defaultValue()).isEqualTo("v18.0");
    }

    @Test
    @DisplayName("name() returns 'whatsapp-cloud-api'")
    void name_returnsWhatsappCloudApi() {
        assertThat(whatsApp.name()).isEqualTo("whatsapp-cloud-api");
    }

    @Test
    @DisplayName("displayName() returns 'WhatsApp Business Cloud API'")
    void displayName_returnsLabel() {
        assertThat(whatsApp.displayName()).isEqualTo("WhatsApp Business Cloud API");
    }

    // -- validate: happy path --

    @Test
    @DisplayName("validate() returns ok when all required fields present")
    void validate_allFieldsPresent_returnsOk() {
        var values = CredentialValues.of(Map.of(
            "accessToken",   "EAABxxx",
            "phoneNumberId", "12345678",
            "apiVersion",    "v18.0"
        ));
        TestResult result = whatsApp.validate(values);
        assertThat(result.success()).isTrue();
        assertThat(result.message()).isEqualTo("OK");
    }

    // -- validate: each missing required field --

    @Test
    @DisplayName("validate() returns error when 'accessToken' is missing")
    void validate_missingAccessToken_returnsError() {
        var values = CredentialValues.of(Map.of(
            "phoneNumberId", "12345678",
            "apiVersion",    "v18.0"
        ));
        TestResult result = whatsApp.validate(values);
        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("accessToken");
    }

    @Test
    @DisplayName("validate() returns error when 'phoneNumberId' is missing")
    void validate_missingPhoneNumberId_returnsError() {
        var values = CredentialValues.of(Map.of(
            "accessToken", "EAABxxx",
            "apiVersion",  "v18.0"
        ));
        TestResult result = whatsApp.validate(values);
        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("phoneNumberId");
    }

    @Test
    @DisplayName("validate() returns error when 'apiVersion' is missing")
    void validate_missingApiVersion_returnsError() {
        var values = CredentialValues.of(Map.of(
            "accessToken",   "EAABxxx",
            "phoneNumberId", "12345678"
        ));
        TestResult result = whatsApp.validate(values);
        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("apiVersion");
    }
}
