package com.werkflow.engine.action;

import com.werkflow.engine.action.notification.*;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.common.engine.api.delegate.Expression;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationDelegateTest {

    @Mock private NotificationChannelFactory channelFactory;
    @Mock private NotificationTemplateService templateService;
    @Mock private NotificationChannel emailChannel;
    @Mock private DelegateExecution execution;
    @Mock private Expression recipientExpr, templateKeyExpr, channelExpr, conditionExpr;

    private NotificationDelegate delegate;

    @BeforeEach
    void setUp() {
        delegate = new NotificationDelegate(channelFactory, templateService);
        delegate.setRecipient(recipientExpr);
        delegate.setTemplateKey(templateKeyExpr);
        delegate.setChannel(channelExpr);
    }

    @Test
    void execute_sendsEmailOnValidInput() {
        when(recipientExpr.getValue(execution)).thenReturn("alice@example.com");
        when(templateKeyExpr.getValue(execution)).thenReturn("welcome");
        when(channelExpr.getValue(execution)).thenReturn("email");
        when(execution.getVariables()).thenReturn(Map.of("name", "Alice"));
        when(templateService.render(eq("welcome"), anyMap(), eq(false)))
            .thenReturn(new NotificationTemplateService.RenderedTemplate("Hi Alice", "Body"));
        when(channelFactory.getChannel("email")).thenReturn(emailChannel);

        delegate.execute(execution);

        ArgumentCaptor<ActionBlockNotificationRequest> captor =
            ArgumentCaptor.forClass(ActionBlockNotificationRequest.class);
        verify(emailChannel).send(captor.capture());
        assertThat(captor.getValue().recipient()).isEqualTo("alice@example.com");
    }

    @Test
    void execute_skipsWhenConditionFalse() {
        delegate.setCondition(conditionExpr);
        when(conditionExpr.getValue(execution)).thenReturn(false);

        delegate.execute(execution);

        verifyNoInteractions(channelFactory, templateService);
    }

    @Test
    void execute_stripsCarriageReturnFromRecipient() {
        when(recipientExpr.getValue(execution)).thenReturn("alice@example.com\r\n");
        when(templateKeyExpr.getValue(execution)).thenReturn("welcome");
        when(channelExpr.getValue(execution)).thenReturn("email");
        when(execution.getVariables()).thenReturn(Map.of());
        when(templateService.render(eq("welcome"), anyMap(), eq(false)))
            .thenReturn(new NotificationTemplateService.RenderedTemplate("Subj", "Body"));
        when(channelFactory.getChannel("email")).thenReturn(emailChannel);

        delegate.execute(execution);

        ArgumentCaptor<ActionBlockNotificationRequest> captor =
            ArgumentCaptor.forClass(ActionBlockNotificationRequest.class);
        verify(emailChannel).send(captor.capture());
        assertThat(captor.getValue().recipient()).isEqualTo("alice@example.com");
    }

    @Test
    void execute_throwsOnInvalidEmailAddress() {
        when(recipientExpr.getValue(execution)).thenReturn("not-an-email");

        assertThatThrownBy(() -> delegate.execute(execution))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("recipient");
    }

    @Test
    void execute_stripsCarriageReturnFromSubject() {
        when(recipientExpr.getValue(execution)).thenReturn("alice@example.com");
        when(templateKeyExpr.getValue(execution)).thenReturn("welcome");
        when(channelExpr.getValue(execution)).thenReturn("email");
        when(execution.getVariables()).thenReturn(Map.of());
        when(templateService.render(eq("welcome"), anyMap(), eq(false)))
            .thenReturn(new NotificationTemplateService.RenderedTemplate(
                "Hello\r\nInjected", "Body"));
        when(channelFactory.getChannel("email")).thenReturn(emailChannel);

        delegate.execute(execution);

        ArgumentCaptor<ActionBlockNotificationRequest> captor =
            ArgumentCaptor.forClass(ActionBlockNotificationRequest.class);
        verify(emailChannel).send(captor.capture());
        assertThat(captor.getValue().subject()).isEqualTo("HelloInjected");
    }
}
