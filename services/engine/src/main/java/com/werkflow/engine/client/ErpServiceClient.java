package com.werkflow.engine.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Slf4j
public class ErpServiceClient {

    private final RestTemplate restTemplate;
    private final String erpServiceUrl;

    public ErpServiceClient(
            @Qualifier("serviceRestTemplate") RestTemplate restTemplate,
            @Value("${app.erp-service.url:http://localhost:8084}") String erpServiceUrl) {
        this.restTemplate = restTemplate;
        this.erpServiceUrl = erpServiceUrl;
    }

    /**
     * Returns custody mappings for a tenant as a map of custodyOwner → candidateGroups.
     * Cached for 5 minutes per tenant (ADR-004).
     */
    @Cacheable(value = "custodyMappings", key = "#tenantId")
    public Map<String, List<String>> getCustodyMappings(String tenantId) {
        String url = UriComponentsBuilder.fromHttpUrl(erpServiceUrl)
                .path("/api/v1/custody-mappings")
                .queryParam("size", 1000)
                .toUriString();
        try {
            ResponseEntity<PageResponse<CustodyMappingDto>> resp = restTemplate.exchange(
                    url, HttpMethod.GET, null,
                    new ParameterizedTypeReference<PageResponse<CustodyMappingDto>>() {});
            PageResponse<CustodyMappingDto> body = resp.getBody();
            if (body == null || body.content() == null) {
                return Map.of();
            }
            return body.content().stream()
                    .collect(Collectors.toMap(
                            CustodyMappingDto::custodyOwner,
                            CustodyMappingDto::candidateGroups,
                            (a, b) -> a));
        } catch (Exception e) {
            log.warn("ErpServiceClient: failed to fetch custody mappings for {} — {}", tenantId, e.getMessage());
            return Map.of();
        }
    }

    public record CustodyMappingDto(Long id, String tenantId, String custodyOwner, List<String> candidateGroups) {}

    public record PageResponse<T>(List<T> content, int totalElements, int totalPages) {}
}
