package com.werkflow.engine.action.notification;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationTemplateServiceTest {

    @Mock
    private NotificationTemplateRepository repository;

    @InjectMocks
    private NotificationTemplateService service;

    @Test
    void render_substitutesVariables() {
        NotificationTemplate template = new NotificationTemplate();
        template.setTemplateKey("welcome");
        template.setChannel("email");
        template.setSubject("Hello ${name}");
        template.setBody("Dear ${name}, welcome to ${company}.");

        when(repository.findByTemplateKeyAndDeletedAtIsNull("welcome"))
            .thenReturn(Optional.of(template));

        NotificationTemplateService.RenderedTemplate result = service.render(
            "welcome", Map.of("name", "Alice", "company", "Acme"), false);

        assertThat(result.subject()).isEqualTo("Hello Alice");
        assertThat(result.body()).isEqualTo("Dear Alice, welcome to Acme.");
    }

    @Test
    void render_throwsWhenTemplateNotFound() {
        when(repository.findByTemplateKeyAndDeletedAtIsNull("missing"))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.render("missing", Map.of(), false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("missing");
    }

    @Test
    void render_sanitizesHtmlWhenRequested() {
        NotificationTemplate template = new NotificationTemplate();
        template.setTemplateKey("html-tmpl");
        template.setChannel("email");
        template.setSubject("Hi");
        template.setBody("<b>Hello</b><script>alert('xss')</script>");

        when(repository.findByTemplateKeyAndDeletedAtIsNull("html-tmpl"))
            .thenReturn(Optional.of(template));

        NotificationTemplateService.RenderedTemplate result = service.render(
            "html-tmpl", Map.of(), true);

        assertThat(result.body()).contains("<b>Hello</b>");
        assertThat(result.body()).doesNotContain("<script>");
    }
}
