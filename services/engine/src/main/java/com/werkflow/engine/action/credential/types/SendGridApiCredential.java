package com.werkflow.engine.action.credential.types;

import com.werkflow.engine.action.credential.CredentialField;
import com.werkflow.engine.action.credential.CredentialField.FieldType;
import com.werkflow.engine.action.credential.CredentialValues;
import com.werkflow.engine.action.credential.HttpCredentialType;
import com.werkflow.engine.action.credential.TestResult;
import org.springframework.stereotype.Component;

import java.net.http.HttpRequest;
import java.util.List;

/**
 * Credential schema for the SendGrid Mail Send API (ADR-020 Phase B.4).
 *
 * <p>Injects a {@code Authorization: Bearer <apiKey>} header on every HTTP connector
 * request, as required by the SendGrid v3 API. The API key is stored as a SECRET field
 * and is never logged.
 */
@Component
public class SendGridApiCredential implements HttpCredentialType {

    private static final List<CredentialField> FIELDS = List.of(
        new CredentialField("apiKey", "API Key", FieldType.SECRET, true, null)
    );

    @Override
    public String name() {
        return "sendgrid-api";
    }

    @Override
    public String displayName() {
        return "SendGrid API";
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

    @Override
    public void applyTo(HttpRequest.Builder builder, CredentialValues values) {
        String apiKey = values.getString("apiKey");
        builder.header("Authorization", "Bearer " + apiKey);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
