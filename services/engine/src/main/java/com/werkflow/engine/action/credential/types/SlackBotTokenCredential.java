package com.werkflow.engine.action.credential.types;

import com.werkflow.engine.action.credential.CredentialField;
import com.werkflow.engine.action.credential.CredentialField.FieldType;
import com.werkflow.engine.action.credential.CredentialType;
import com.werkflow.engine.action.credential.CredentialValues;
import com.werkflow.engine.action.credential.TestResult;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Credential schema for a Slack Bot Token integration (ADR-020).
 *
 * <p>Corresponds to the {@code app.notification.slack.*} properties.
 * The Slack adapter is currently a stub ({@code UnsupportedOperationException}),
 * so this credential type is introduced now to unblock Phase B.2/B.3 admin UI
 * without modifying the adapter.
 */
@Component
public class SlackBotTokenCredential implements CredentialType {

    private static final List<CredentialField> FIELDS = List.of(
        new CredentialField("botToken",      "Bot Token",      FieldType.SECRET, true, null),
        new CredentialField("signingSecret", "Signing Secret", FieldType.SECRET, true, null)
    );

    @Override
    public String name() {
        return "slack-bot-token";
    }

    @Override
    public String displayName() {
        return "Slack Bot Token";
    }

    @Override
    public List<CredentialField> fields() {
        return FIELDS;
    }

    /**
     * Validates that all required fields are present and non-blank.
     *
     * @return {@link TestResult#ok()} if all required fields are present;
     *         {@link TestResult#error} naming the first missing field otherwise
     */
    @Override
    public TestResult validate(CredentialValues values) {
        for (CredentialField field : FIELDS) {
            if (field.required() && isBlank(values.getString(field.name()))) {
                return TestResult.error("Missing required field: " + field.name());
            }
        }
        return TestResult.ok();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
