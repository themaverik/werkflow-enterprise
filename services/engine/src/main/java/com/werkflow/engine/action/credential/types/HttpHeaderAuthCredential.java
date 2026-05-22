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
 * Credential schema for arbitrary HTTP header-based authentication (ADR-020 Phase B.4).
 *
 * <p>Injects a single named header (e.g. {@code X-Api-Key}, {@code Authorization}) with
 * the supplied value on every HTTP connector request. Useful for any API that uses a
 * custom header for authentication rather than the standard Basic/Bearer schemes.
 */
@Component
public class HttpHeaderAuthCredential implements HttpCredentialType {

    private static final List<CredentialField> FIELDS = List.of(
        new CredentialField("headerName",  "Header Name",  FieldType.STRING, true, null),
        new CredentialField("headerValue", "Header Value", FieldType.SECRET, true, null)
    );

    @Override
    public String name() {
        return "http-header-auth";
    }

    @Override
    public String displayName() {
        return "HTTP Header Auth";
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
        String headerName  = values.getString("headerName");
        String headerValue = values.getString("headerValue");
        builder.header(headerName, headerValue);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
