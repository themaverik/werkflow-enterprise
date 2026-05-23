package com.werkflow.engine.action.credential.types;

import com.werkflow.engine.action.credential.CredentialField;
import com.werkflow.engine.action.credential.CredentialField.FieldType;
import com.werkflow.engine.action.credential.CredentialValues;
import com.werkflow.engine.action.credential.DatabaseCredentialType;
import com.werkflow.engine.action.credential.TestResult;
import com.zaxxer.hikari.HikariConfig;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Credential schema for a JDBC username/password login (ADR-020 Phase B.5).
 *
 * <p>The single generic database credential type: dialect, driver, JDBC URL, and pool
 * sizing are non-secret datasource config owned by the datasource registration, not the
 * credential. This type carries only the login pair, rotated together in OpenBao.
 */
@Component
public class JdbcPasswordCredential implements DatabaseCredentialType {

    private static final List<CredentialField> FIELDS = List.of(
        new CredentialField("username", "Username", FieldType.STRING, true, null),
        new CredentialField("password", "Password", FieldType.SECRET, true, null)
    );

    @Override
    public String name() {
        return "jdbc-password";
    }

    @Override
    public String displayName() {
        return "JDBC Username/Password";
    }

    @Override
    public List<CredentialField> fields() {
        return FIELDS;
    }

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
    public void applyCredentials(HikariConfig config, CredentialValues values) {
        config.setUsername(values.getString("username"));
        config.setPassword(values.getString("password"));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
