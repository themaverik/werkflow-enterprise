package com.werkflow.engine.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.context.ApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping({"/api/delegates", "/delegates"})
@RequiredArgsConstructor
@Tag(name = "Delegates", description = "JavaDelegate bean introspection for the BPMN designer")
public class DelegateController {

    private final ApplicationContext applicationContext;

    @GetMapping
    @PreAuthorize("hasPermission(null, 'WORKFLOW:DESIGN')")
    @Operation(
        summary = "List registered JavaDelegate beans",
        description = "Returns the Spring bean names of all registered JavaDelegate implementations. Used to populate the delegate expression dropdown in the BPMN designer."
    )
    public ResponseEntity<List<String>> listDelegates() {
        List<String> names = new ArrayList<>(
            applicationContext.getBeansOfType(JavaDelegate.class).keySet()
        );
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return ResponseEntity.ok(names);
    }
}
