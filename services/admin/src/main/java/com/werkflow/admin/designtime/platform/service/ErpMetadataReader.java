package com.werkflow.admin.designtime.platform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.werkflow.admin.dto.connector.ConnectorTestResponse;
import com.werkflow.admin.service.ConnectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

/**
 * Single channel for design-time platform reads of ERP metadata (departments, custody mappings).
 *
 * <p>Per ADR-023: all external access — including platform→ERP — flows through the governed
 * connector abstraction; bespoke {@code RestTemplate} clients to ERP are prohibited. ERP exposes
 * one REST base ({@code /api/v1}); these design-time reads route through the tenant's canonical
 * {@code hr-service} connector. Every read degrades to an empty list when the connector is
 * unregistered, ERP is unreachable, or the payload is unparseable (OSS mode / unconfigured tenant).
 */
@Slf4j
@Service
@RequiredArgsConstructor
class ErpMetadataReader {

    static final String ERP_CONNECTOR_KEY = "hr-service";

    private final ConnectorService connectorService;
    private final ObjectMapper objectMapper;

    /**
     * Reads an ERP collection via the {@code hr-service} connector and returns its items.
     * ERP returns a Spring {@code Page} ({@code {"content":[...]}}); a bare array is also tolerated.
     *
     * @param tenantCode tenant whose connector + credential are used
     * @param path       ERP path relative to the connector base, e.g. {@code /departments}
     * @return the collection items, or an empty list on any failure
     */
    List<JsonNode> readCollection(String tenantCode, String path) {
        try {
            ConnectorTestResponse response = connectorService.callConnector(
                    tenantCode, ERP_CONNECTOR_KEY, path, "GET", null);
            if (response.getStatusCode() < 200 || response.getStatusCode() >= 300) {
                log.warn("ErpMetadataReader: {} returned status {} for tenant {}",
                        path, response.getStatusCode(), tenantCode);
                return List.of();
            }
            String body = response.getBody();
            if (body == null || body.isBlank()) {
                return List.of();
            }
            JsonNode root = objectMapper.readTree(body);
            JsonNode items = root.isArray() ? root : root.path("content");
            List<JsonNode> result = new ArrayList<>();
            items.forEach(result::add);
            return List.copyOf(result);
        } catch (ResponseStatusException rse) {
            // e.g. no credential registered for the connector — surface the reason, not an opaque message.
            log.warn("ErpMetadataReader: {} unavailable for tenant {} — {} {}",
                    path, tenantCode, rse.getStatusCode(), rse.getReason());
            return List.of();
        } catch (Exception e) {
            log.warn("ErpMetadataReader: {} unavailable for tenant {} — {}", path, tenantCode, e.getMessage());
            return List.of();
        }
    }
}
