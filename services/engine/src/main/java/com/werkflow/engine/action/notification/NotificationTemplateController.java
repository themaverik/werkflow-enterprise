package com.werkflow.engine.action.notification;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping({"/api/notification-templates", "/notification-templates"})
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Notification Templates", description = "Notification template management API")
public class NotificationTemplateController {

    private final NotificationTemplateRepository repository;

    public record TemplateInfo(String key, String name, String channel) {}

    @GetMapping
    @Operation(summary = "List notification templates", description = "Returns all active notification template keys for the BPMN designer dropdown")
    public ResponseEntity<List<TemplateInfo>> listTemplates() {
        List<TemplateInfo> templates = repository.findAllByDeletedAtIsNull()
            .stream()
            .map(t -> new TemplateInfo(
                t.getTemplateKey(),
                t.getTemplateKey(),   // use key as display name until we add a name column
                t.getChannel()
            ))
            .toList();
        return ResponseEntity.ok(templates);
    }
}
