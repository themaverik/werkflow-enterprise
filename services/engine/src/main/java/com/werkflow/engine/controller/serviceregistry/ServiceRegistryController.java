package com.werkflow.engine.controller.serviceregistry;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Lightweight Service Registry Controller for Engine Service
 * This acts as a proxy to the Admin Service for service registry operations.
 * For complete service registry management, the Admin Service is the source of truth.
 */
@RestController
@RequestMapping("/api/services")
@Slf4j
@Tag(name = "Service Registry", description = "Service registry proxy (delegates to Admin Service)")
public class ServiceRegistryController {

    @Value("${spring.application.name:werkflow-engine}")
    private String serviceName;

    @Value("${server.port:8081}")
    private String serverPort;

    @Value("${admin.service.url:http://admin-service:8083}")
    private String adminServiceUrl;

    private final RestTemplate restTemplate;

    public ServiceRegistryController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Get current service information
     * This is a lightweight endpoint that returns basic service metadata
     */
    @GetMapping("/current")
    @Operation(summary = "Get current service info", description = "Returns basic information about this service instance")
    public ResponseEntity<Map<String, Object>> getCurrentServiceInfo() {
        log.info("Fetching current service information");

        Map<String, Object> serviceInfo = Map.of(
                "serviceName", serviceName,
                "serviceType", "WORKFLOW_ENGINE",
                "port", serverPort,
                "status", "RUNNING",
                "capabilities", List.of(
                        "BPMN Process Execution",
                        "Task Management",
                        "Form Processing",
                        "Process Monitoring"
                )
        );

        return ResponseEntity.ok(serviceInfo);
    }

    /**
     * Get all services - proxies request to Admin Service
     * For full service registry management, the Admin Service is the source of truth
     */
    @GetMapping
    @Operation(summary = "Get all services", description = "Proxies request to Admin Service for complete registry")
    public ResponseEntity<?> getAllServices(
            @PageableDefault(size = 20, sort = "serviceName", direction = Sort.Direction.ASC)
            Pageable pageable) {
        log.info("Service registry list requested - proxying to Admin Service at {}", adminServiceUrl);

        try {
            String adminUrl = adminServiceUrl + "/api/services?page=" + pageable.getPageNumber() +
                    "&size=" + pageable.getPageSize() + "&sort=serviceName,asc";
            log.debug("Proxying to: {}", adminUrl);
            ResponseEntity<?> response = restTemplate.getForEntity(adminUrl, Object.class);
            log.info("Successfully proxied request to Admin Service");
            return response;
        } catch (Exception e) {
            log.warn("Failed to proxy request to Admin Service: {}", e.getMessage());
            log.warn("Admin Service URL: {}", adminServiceUrl);
            // Return empty page if admin service is not available
            return ResponseEntity.ok(new PageImpl<>(List.of(), pageable, 0));
        }
    }

    /**
     * Get service by name - provides basic hardcoded mappings
     */
    @GetMapping("/by-name/{serviceName}")
    @Operation(summary = "Get service by name", description = "Returns basic service information by name")
    public ResponseEntity<Map<String, Object>> getServiceByName(@PathVariable String serviceName) {
        log.info("Service lookup requested for: {}", serviceName);

        // Provide basic service mappings for known services
        Map<String, Object> serviceInfo = switch (serviceName.toLowerCase()) {
            case "werkflow-engine", "engine" -> Map.of(
                    "serviceName", "werkflow-engine",
                    "serviceType", "WORKFLOW_ENGINE",
                    "defaultPort", "8081",
                    "baseUrl", "http://engine-service:8081"
            );
            case "werkflow-admin", "admin" -> Map.of(
                    "serviceName", "werkflow-admin",
                    "serviceType", "ADMIN",
                    "defaultPort", "8083",
                    "baseUrl", adminServiceUrl,
                    "note", "Use Admin Service for full service registry management"
            );
            case "werkflow-notification", "notification" -> Map.of(
                    "serviceName", "werkflow-notification",
                    "serviceType", "NOTIFICATION",
                    "defaultPort", "8084",
                    "baseUrl", "http://notification-service:8084"
            );
            default -> Map.of(
                    "message", "Service not found in basic registry",
                    "note", "For complete service registry, use Admin Service: " + adminServiceUrl + "/api/services",
                    "requestedService", serviceName
            );
        };

        return ResponseEntity.ok(serviceInfo);
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    @Operation(summary = "Service registry health", description = "Check if service registry endpoint is available")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "message", "Service registry endpoints available",
                "fullRegistry", "Available at Admin Service: " + adminServiceUrl
        ));
    }
}
