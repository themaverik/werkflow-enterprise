package com.werkflow.engine.action.credential.types;

import com.werkflow.engine.action.credential.CredentialField;
import com.werkflow.engine.action.credential.CredentialField.FieldType;
import com.werkflow.engine.action.credential.CredentialValues;
import com.werkflow.engine.action.credential.HttpCredentialType;
import com.werkflow.engine.action.credential.TestResult;
import org.springframework.stereotype.Component;

import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/**
 * Credential schema for HTTP Basic Authentication (ADR-020 Phase B.4).
 *
 * <p>Encodes a {@code username:password} pair as a Base64 token and injects it
 * as an {@code Authorization: Basic <token>} header on every HTTP connector request.
 * Suitable for REST APIs that follow RFC 7617.
 */
@Component
public class HttpBasicAuthCredential implements HttpCredentialType {

    private static final List<CredentialField> FIELDS = List.of(
        new CredentialField("username", "Username", FieldType.STRING, true, null),
        new CredentialField("password", "Password", FieldType.SECRET, true, null)
    );

    @Override
    public String name() {
        return "http-basic-auth";
    }

    @Override
    public String displayName() {
        return "HTTP Basic Auth";
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
        String username = values.getString("username");
        String password = values.getString("password");
        String token = Base64.getEncoder().encodeToString(
            (username + ":" + password).getBytes(StandardCharsets.UTF_8));
        builder.header("Authorization", "Basic " + token);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
