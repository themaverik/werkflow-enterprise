package com.werkflow.engine.action.credential;

import com.werkflow.engine.action.credential.CredentialField.FieldType;
import com.werkflow.engine.action.credential.types.SmtpCredential;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SmtpCredentialTest {

    private SmtpCredential smtp;

    @BeforeEach
    void setUp() {
        smtp = new SmtpCredential();
    }

    // -- field shape --

    @Test
    @DisplayName("fields() returns exactly 5 entries")
    void fields_returnsExactlyFiveFields() {
        assertThat(smtp.fields()).hasSize(5);
    }

    @Test
    @DisplayName("fields() has correct names in order")
    void fields_correctNames() {
        var names = smtp.fields().stream().map(CredentialField::name).toList();
        assertThat(names).containsExactly("host", "port", "username", "password", "useTls");
    }

    @Test
    @DisplayName("fields() has correct types")
    void fields_correctTypes() {
        var types = smtp.fields().stream().map(CredentialField::type).toList();
        assertThat(types).containsExactly(
            FieldType.STRING,
            FieldType.INT,
            FieldType.STRING,
            FieldType.SECRET,
            FieldType.BOOL
        );
    }

    @Test
    @DisplayName("all 5 fields are required")
    void fields_allRequired() {
        assertThat(smtp.fields()).allMatch(CredentialField::required);
    }

    @Test
    @DisplayName("port default is 587")
    void fields_portDefault587() {
        var port = smtp.fields().stream()
            .filter(f -> "port".equals(f.name()))
            .findFirst().orElseThrow();
        assertThat(port.defaultValue()).isEqualTo(587);
    }

    @Test
    @DisplayName("useTls default is true")
    void fields_usetlsDefaultTrue() {
        var useTls = smtp.fields().stream()
            .filter(f -> "useTls".equals(f.name()))
            .findFirst().orElseThrow();
        assertThat(useTls.defaultValue()).isEqualTo(true);
    }

    @Test
    @DisplayName("name() returns 'smtp'")
    void name_returnsSmtp() {
        assertThat(smtp.name()).isEqualTo("smtp");
    }

    @Test
    @DisplayName("displayName() returns 'SMTP Server'")
    void displayName_returnsLabel() {
        assertThat(smtp.displayName()).isEqualTo("SMTP Server");
    }

    // -- validate: happy path --

    @Test
    @DisplayName("validate() returns ok when all required fields present")
    void validate_allFieldsPresent_returnsOk() {
        var values = CredentialValues.of(Map.of(
            "host",     "mail.example.com",
            "port",     "587",
            "username", "user@example.com",
            "password", "s3cr3t",
            "useTls",   "true"
        ));
        TestResult result = smtp.validate(values);
        assertThat(result.success()).isTrue();
        assertThat(result.message()).isEqualTo("OK");
    }

    // -- validate: each missing required field --

    @Test
    @DisplayName("validate() returns error when 'host' is missing")
    void validate_missingHost_returnsError() {
        var values = CredentialValues.of(Map.of(
            "port",     "587",
            "username", "user@example.com",
            "password", "s3cr3t",
            "useTls",   "true"
        ));
        TestResult result = smtp.validate(values);
        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("host");
    }

    @Test
    @DisplayName("validate() returns error when 'port' is missing")
    void validate_missingPort_returnsError() {
        var values = CredentialValues.of(Map.of(
            "host",     "mail.example.com",
            "username", "user@example.com",
            "password", "s3cr3t",
            "useTls",   "true"
        ));
        TestResult result = smtp.validate(values);
        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("port");
    }

    @Test
    @DisplayName("validate() returns error when 'username' is missing")
    void validate_missingUsername_returnsError() {
        var values = CredentialValues.of(Map.of(
            "host",     "mail.example.com",
            "port",     "587",
            "password", "s3cr3t",
            "useTls",   "true"
        ));
        TestResult result = smtp.validate(values);
        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("username");
    }

    @Test
    @DisplayName("validate() returns error when 'password' is missing")
    void validate_missingPassword_returnsError() {
        var values = CredentialValues.of(Map.of(
            "host",     "mail.example.com",
            "port",     "587",
            "username", "user@example.com",
            "useTls",   "true"
        ));
        TestResult result = smtp.validate(values);
        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("password");
    }

    @Test
    @DisplayName("validate() returns error when 'useTls' is missing")
    void validate_missingUseTls_returnsError() {
        var values = CredentialValues.of(Map.of(
            "host",     "mail.example.com",
            "port",     "587",
            "username", "user@example.com",
            "password", "s3cr3t"
        ));
        TestResult result = smtp.validate(values);
        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("useTls");
    }
}
