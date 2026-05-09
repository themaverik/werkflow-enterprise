package com.werkflow.admin.designtime.platform.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collections;
import java.util.List;

/**
 * HTTP client for the ERP service — fetches departments for PSS visibility metadata.
 * Degrades gracefully when ERP is unreachable (returns empty list).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ErpClient {

    private final RestTemplate restTemplate;

    @Value("${app.erp-service.url:http://werkflow-business:8084}")
    private String erpBaseUrl;

    private static final ParameterizedTypeReference<List<ErpDepartmentResponse>> DEPT_LIST =
            new ParameterizedTypeReference<>() {};

    /**
     * Returns departments for the given tenant. Empty list on any ERP failure.
     */
    public List<ErpDepartmentResponse> getDepartments(String tenantCode) {
        try {
            String url = UriComponentsBuilder
                    .fromHttpUrl(erpBaseUrl + "/api/v1/departments")
                    .queryParam("tenantCode", tenantCode)
                    .toUriString();
            List<ErpDepartmentResponse> body = restTemplate
                    .exchange(url, HttpMethod.GET, null, DEPT_LIST)
                    .getBody();
            return body != null ? body : Collections.emptyList();
        } catch (Exception e) {
            log.warn("ErpClient: departments unavailable for tenant {} — {}", tenantCode, e.getMessage());
            return Collections.emptyList();
        }
    }
}
