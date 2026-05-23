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
 * Credential schema for HTTP Bearer-token authentication (ADR-020 Phase B.6).
 *
 * <p>Injects an {@code Authorization: Bearer <token>} header on every HTTP connector
 * request. Suitable for REST APIs that accept a static bearer token (RFC 6750), e.g.
 * personal access tokens or pre-issued API tokens. Unlike {@link HttpHeaderAuthCredential}
 * the header name and {@code Bearer } prefix are fixed; the user supplies only the token.
 */
@Component
public class HttpBearerTokenCredential implements HttpCredentialType {

    private static final List<CredentialField> FIELDS = List.of(
        new CredentialField("token", "Token", FieldType.SECRET, true, null)
    );

    @Override
    public String name() {
        return "http-bearer-token";
    }

    @Override
    public String displayName() {
        return "HTTP Bearer Token";
    }

    @Override
    public List<CredentialField> fields() {
        return FIELDS;
    }

    /**
     * Validates that the token field is present and non-blank.
     *
     * @return {@link TestResult#ok()} if the token is present;
     *         {@link TestResult#error} naming the missing field otherwise
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
        builder.header("Authorization", "Bearer " + values.getString("token"));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
