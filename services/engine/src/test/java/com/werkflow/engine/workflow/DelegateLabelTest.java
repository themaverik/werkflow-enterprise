package com.werkflow.engine.workflow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class DelegateLabelTest {

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
        "externalApiCallDelegate,   External Api Call",
        "databaseConnectorDelegate, Database Connector",
        "connectorWebhookDelegate,  Connector Webhook",
        "restConnectorDelegate,     Rest Connector",
        "notificationDelegate,      Notification"
    })
    @DisplayName("toHuman converts real-world bean names correctly")
    void toHuman_realWorldBeanNames(String beanName, String expected) {
        assertThat(DelegateLabel.toHuman(beanName.trim())).isEqualTo(expected.trim());
    }

    @Test
    @DisplayName("null input → empty string")
    void toHuman_null_returnsEmpty() {
        assertThat(DelegateLabel.toHuman(null)).isEmpty();
    }

    @Test
    @DisplayName("blank input → empty string")
    void toHuman_blank_returnsEmpty() {
        assertThat(DelegateLabel.toHuman("   ")).isEmpty();
    }
}
