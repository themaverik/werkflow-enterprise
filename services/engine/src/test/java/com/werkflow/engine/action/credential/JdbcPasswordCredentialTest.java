package com.werkflow.engine.action.credential;

import com.werkflow.engine.action.credential.CredentialField.FieldType;
import com.werkflow.engine.action.credential.types.JdbcPasswordCredential;
import com.zaxxer.hikari.HikariConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcPasswordCredentialTest {

    private JdbcPasswordCredential credential;

    @BeforeEach
    void setUp() {
        credential = new JdbcPasswordCredential();
    }

    @Test
    @DisplayName("name() is 'jdbc-password'")
    void name_isJdbcPassword() {
        assertThat(credential.name()).isEqualTo("jdbc-password");
    }

    @Test
    @DisplayName("displayName() is 'JDBC Username/Password'")
    void displayName_isLabel() {
        assertThat(credential.displayName()).isEqualTo("JDBC Username/Password");
    }

    @Test
    @DisplayName("fields() are username(STRING) then password(SECRET), both required")
    void fields_shape() {
        assertThat(credential.fields()).hasSize(2);
        assertThat(credential.fields().get(0).name()).isEqualTo("username");
        assertThat(credential.fields().get(0).type()).isEqualTo(FieldType.STRING);
        assertThat(credential.fields().get(1).name()).isEqualTo("password");
        assertThat(credential.fields().get(1).type()).isEqualTo(FieldType.SECRET);
        assertThat(credential.fields()).allMatch(CredentialField::required);
    }

    @Test
    @DisplayName("validate() ok when both present")
    void validate_ok() {
        var values = CredentialValues.of(Map.of("username", "sa", "password", "pw"));
        assertThat(credential.validate(values).success()).isTrue();
    }

    @Test
    @DisplayName("validate() errors naming missing username")
    void validate_missingUsername() {
        var values = CredentialValues.of(Map.of("password", "pw"));
        var result = credential.validate(values);
        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("username");
    }

    @Test
    @DisplayName("validate() errors naming blank password")
    void validate_blankPassword() {
        var values = CredentialValues.of(Map.of("username", "sa", "password", ""));
        var result = credential.validate(values);
        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("password");
    }

    @Test
    @DisplayName("applyCredentials() sets username and password on the HikariConfig")
    void applyCredentials_setsUserAndPassword() {
        var values = CredentialValues.of(Map.of("username", "sa", "password", "pw"));
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:t");

        credential.applyCredentials(config, values);

        assertThat(config.getUsername()).isEqualTo("sa");
        assertThat(config.getPassword()).isEqualTo("pw");
    }
}
