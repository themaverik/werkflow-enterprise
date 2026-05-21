package com.werkflow.engine.action.credential;

import com.werkflow.engine.action.credential.CredentialField.FieldType;
import com.werkflow.engine.action.credential.types.SlackBotTokenCredential;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SlackBotTokenCredentialTest {

    private SlackBotTokenCredential slack;

    @BeforeEach
    void setUp() {
        slack = new SlackBotTokenCredential();
    }

    // -- field shape --

    @Test
    @DisplayName("fields() returns exactly 2 entries")
    void fields_returnsExactlyTwoFields() {
        assertThat(slack.fields()).hasSize(2);
    }

    @Test
    @DisplayName("fields() has correct names in order")
    void fields_correctNames() {
        var names = slack.fields().stream().map(CredentialField::name).toList();
        assertThat(names).containsExactly("botToken", "signingSecret");
    }

    @Test
    @DisplayName("both fields are SECRET type")
    void fields_bothSecret() {
        assertThat(slack.fields()).allMatch(f -> f.type() == FieldType.SECRET);
    }

    @Test
    @DisplayName("both fields are required")
    void fields_bothRequired() {
        assertThat(slack.fields()).allMatch(CredentialField::required);
    }

    @Test
    @DisplayName("name() returns 'slack-bot-token'")
    void name_returnsSlackBotToken() {
        assertThat(slack.name()).isEqualTo("slack-bot-token");
    }

    @Test
    @DisplayName("displayName() returns 'Slack Bot Token'")
    void displayName_returnsLabel() {
        assertThat(slack.displayName()).isEqualTo("Slack Bot Token");
    }

    // -- validate: happy path --

    @Test
    @DisplayName("validate() returns ok when all required fields present")
    void validate_allFieldsPresent_returnsOk() {
        var values = CredentialValues.of(Map.of(
            "botToken",      "xoxb-12345",
            "signingSecret", "abc123xyz"
        ));
        TestResult result = slack.validate(values);
        assertThat(result.success()).isTrue();
        assertThat(result.message()).isEqualTo("OK");
    }

    // -- validate: each missing required field --

    @Test
    @DisplayName("validate() returns error when 'botToken' is missing")
    void validate_missingBotToken_returnsError() {
        var values = CredentialValues.of(Map.of("signingSecret", "abc123xyz"));
        TestResult result = slack.validate(values);
        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("botToken");
    }

    @Test
    @DisplayName("validate() returns error when 'signingSecret' is missing")
    void validate_missingSigningSecret_returnsError() {
        var values = CredentialValues.of(Map.of("botToken", "xoxb-12345"));
        TestResult result = slack.validate(values);
        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("signingSecret");
    }
}
