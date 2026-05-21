package com.werkflow.engine.action.credential;

import com.werkflow.engine.action.credential.types.SlackBotTokenCredential;
import com.werkflow.engine.action.credential.types.SmtpCredential;
import com.werkflow.engine.action.credential.types.WhatsAppBusinessCredential;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CredentialRegistryTest {

    private Environment env;
    private CredentialRegistry registry;

    @BeforeEach
    void setUp() {
        env = mock(Environment.class);
        registry = new CredentialRegistry(
            List.of(new SmtpCredential(), new SlackBotTokenCredential(), new WhatsAppBusinessCredential()),
            env
        );
    }

    // -- get() --

    @Test
    @DisplayName("get('smtp') returns a SmtpCredential instance")
    void get_smtp_returnsSmtpCredential() {
        CredentialType type = registry.get("smtp");
        assertThat(type).isInstanceOf(SmtpCredential.class);
    }

    @Test
    @DisplayName("get('slack-bot-token') returns a SlackBotTokenCredential instance")
    void get_slack_returnsSlackCredential() {
        CredentialType type = registry.get("slack-bot-token");
        assertThat(type).isInstanceOf(SlackBotTokenCredential.class);
    }

    @Test
    @DisplayName("get('whatsapp-cloud-api') returns a WhatsAppBusinessCredential instance")
    void get_whatsapp_returnsWhatsAppCredential() {
        CredentialType type = registry.get("whatsapp-cloud-api");
        assertThat(type).isInstanceOf(WhatsAppBusinessCredential.class);
    }

    @Test
    @DisplayName("get() throws IllegalArgumentException for unknown type name")
    void get_unknownType_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> registry.get("oracle-db"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("oracle-db");
    }

    // -- registeredTypes() --

    @Test
    @DisplayName("registeredTypes() contains all 3 expected names")
    void registeredTypes_containsAllThree() {
        assertThat(registry.registeredTypes())
            .containsExactlyInAnyOrder("smtp", "slack-bot-token", "whatsapp-cloud-api");
    }

    // -- resolveForTenant() --

    @Test
    @DisplayName("resolveForTenant('smtp') returns CredentialValues with smtp keys from env")
    void resolveForTenant_smtp_readsFromEnvironment() {
        when(env.getProperty("spring.mail.host")).thenReturn("smtp.example.com");
        when(env.getProperty("spring.mail.port")).thenReturn("587");
        when(env.getProperty("spring.mail.username")).thenReturn("user@example.com");
        when(env.getProperty("spring.mail.password")).thenReturn("s3cr3t");
        when(env.getProperty("spring.mail.properties.mail.smtp.starttls.enable")).thenReturn("true");

        CredentialValues values = registry.resolveForTenant("smtp", "tenant-1");

        assertThat(values.getString("host")).isEqualTo("smtp.example.com");
        assertThat(values.getString("port")).isEqualTo("587");
        assertThat(values.getString("username")).isEqualTo("user@example.com");
        assertThat(values.getString("password")).isEqualTo("s3cr3t");
        assertThat(values.getString("useTls")).isEqualTo("true");
    }

    @Test
    @DisplayName("resolveForTenant('smtp') omits keys for absent properties")
    void resolveForTenant_smtp_omitsAbsentProperties() {
        when(env.getProperty("spring.mail.host")).thenReturn(null);
        when(env.getProperty("spring.mail.port")).thenReturn(null);
        when(env.getProperty("spring.mail.username")).thenReturn(null);
        when(env.getProperty("spring.mail.password")).thenReturn(null);
        when(env.getProperty("spring.mail.properties.mail.smtp.starttls.enable")).thenReturn(null);

        CredentialValues values = registry.resolveForTenant("smtp", "tenant-1");

        assertThat(values.getString("host")).isNull();
        assertThat(values.getString("password")).isNull();
    }

    @Test
    @DisplayName("resolveForTenant() throws IllegalArgumentException for unknown type")
    void resolveForTenant_unknownType_throws() {
        assertThatThrownBy(() -> registry.resolveForTenant("ftp", "tenant-1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("ftp");
    }
}
