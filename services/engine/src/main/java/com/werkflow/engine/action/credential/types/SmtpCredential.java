package com.werkflow.engine.action.credential.types;

import com.werkflow.engine.action.credential.CredentialField;
import com.werkflow.engine.action.credential.CredentialField.FieldType;
import com.werkflow.engine.action.credential.CredentialType;
import com.werkflow.engine.action.credential.CredentialValues;
import com.werkflow.engine.action.credential.TestResult;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Credential schema for an SMTP mail server (ADR-020).
 *
 * <p>Corresponds to the {@code spring.mail.*} Spring Boot auto-configuration properties.
 * The {@code validate} method performs field-shape checks only — no network connectivity.
 */
@Component
public class SmtpCredential implements CredentialType {

    private static final List<CredentialField> FIELDS = List.of(
        new CredentialField("host",     "Host",          FieldType.STRING, true,  null),
        new CredentialField("port",     "Port",          FieldType.INT,    true,  587),
        new CredentialField("username", "Username",      FieldType.STRING, true,  null),
        new CredentialField("password", "Password",      FieldType.SECRET, true,  null),
        new CredentialField("useTls",   "Enable TLS",    FieldType.BOOL,   true,  true)
    );

    @Override
    public String name() {
        return "smtp";
    }

    @Override
    public String displayName() {
        return "SMTP Server";
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
