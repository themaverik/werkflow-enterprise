package com.werkflow.delegates.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.flowable.common.engine.api.delegate.Expression;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Generic REST Service Delegate for making HTTP calls to external services
 *
 * Supports TWO configuration modes for maximum flexibility:
 *
 * MODE 1: Field Injection (RECOMMENDED - Type-safe, Clean BPMN)
 * Use flowable:field tags with Expression support. Fields are injected at deployment and evaluated at runtime.
 *
 * MODE 2: Process Variables (Legacy - Backward compatible)
 * Use execution.setVariable() in script tasks or previous delegates.
 *
 * Configuration Parameters:
 * - url: Target endpoint URL (required)
 * - method: HTTP method (GET, POST, PUT, DELETE, PATCH) - default: POST
 * - headers: Map of HTTP headers (optional)
 * - body: Request body object (optional, for POST/PUT/PATCH)
 * - responseVariable: Variable name to store response (default: "restResponse")
 *
 * JWT Propagation:
 * If the process variable "authorizationToken" is set, the delegate automatically
 * forwards it as an Authorization header to downstream services. This enables
 * cross-service JWT propagation without BPMN configuration.
 *
 * Example MODE 1 - Field Injection (RECOMMENDED):
 * <serviceTask id="createCapEx" flowable:delegateExpression="${restServiceDelegate}">
 *   <extensionElements>
 *     <flowable:field name="url">
 *       <flowable:expression>${financeServiceUrl}/api/workflow/capex/create-request</flowable:expression>
 *     </flowable:field>
 *     <flowable:field name="method">
 *       <flowable:string>POST</flowable:string>
 *     </flowable:field>
 *     <flowable:field name="body">
 *       <flowable:expression>#{
 *         {
 *           'title': title,
 *           'amount': requestAmount,
 *           'department': departmentName
 *         }
 *       }</flowable:expression>
 *     </flowable:field>
 *     <flowable:field name="responseVariable">
 *       <flowable:string>createRequestResponse</flowable:string>
 *     </flowable:field>
 *   </extensionElements>
 * </serviceTask>
 */
@Slf4j
@Component("restServiceDelegate")
public class RestServiceDelegate implements JavaDelegate {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    // Field injection support (MODE 1 - RECOMMENDED)
    private Expression url;
    private Expression method;
    private Expression headers;
    private Expression body;
    private Expression responseVariable;

    public RestServiceDelegate(RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    @Override
    public void execute(DelegateExecution execution) {
        log.info("Executing RestServiceDelegate for process: {}", execution.getProcessInstanceId());

        // Get configuration - Fields take priority over variables for explicit control
        String urlValue = getFieldOrVariable(execution, this.url, "url", null);
        if (urlValue == null) {
            throw new IllegalArgumentException("Required field/variable 'url' is not set");
        }

        String methodValue = getFieldOrVariable(execution, this.method, "method", "POST");
        Map<String, String> headersValue = getFieldOrVariable(execution, this.headers, "headers", null);
        Object bodyValue = getFieldOrVariable(execution, this.body, "body", null);
        String responseVariableValue = getFieldOrVariable(execution, this.responseVariable, "responseVariable", "restResponse");

        log.debug("REST call configuration: url={}, method={}, responseVariable={}",
            urlValue, methodValue, responseVariableValue);

        try {
            RestClient.RequestBodySpec requestSpec = restClient
                .method(HttpMethod.valueOf(methodValue.toUpperCase()))
                .uri(urlValue)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(httpHeaders -> {
                    // Forward JWT token if available in process variables
                    Object authToken = execution.getVariable("authorizationToken");
                    if (authToken != null) {
                        String token = authToken.toString();
                        if (!token.toLowerCase().startsWith("bearer ")) {
                            token = "Bearer " + token;
                        }
                        httpHeaders.set("Authorization", token);
                        log.debug("Forwarding Authorization header to downstream service");
                    }
                    // Apply any explicit headers (override auth if provided)
                    if (headersValue != null) {
                        headersValue.forEach(httpHeaders::add);
                    }
                });

            Map<String, Object> response;
            if (bodyValue != null && (methodValue.equalsIgnoreCase("POST") ||
                                 methodValue.equalsIgnoreCase("PUT") ||
                                 methodValue.equalsIgnoreCase("PATCH"))) {
                response = requestSpec.body(bodyValue).retrieve().body(Map.class);
            } else {
                response = requestSpec.retrieve().body(Map.class);
            }

            // Store response in process variable
            execution.setVariable(responseVariableValue, response);

            log.info("REST call successful. Response stored in variable: {}", responseVariableValue);

        } catch (Exception e) {
            log.error("REST call failed: {}", e.getMessage(), e);

            // Store error information
            execution.setVariable(responseVariableValue + "Error", e.getMessage());
            execution.setVariable(responseVariableValue + "Success", false);

            throw new RuntimeException("REST service call failed: " + e.getMessage(), e);
        }
    }

    /**
     * Get value from Expression field first, fall back to process variable
     * This provides flexibility and backward compatibility
     */
    @SuppressWarnings("unchecked")
    private <T> T getFieldOrVariable(DelegateExecution execution, Expression field, String variableName, T defaultValue) {
        Object value = null;

        // Try field first (MODE 1 - Field Injection)
        if (field != null) {
            value = field.getValue(execution);
            log.trace("Field '{}' resolved to: {}", variableName, value);
        }

        // Fall back to variable (MODE 2 - Process Variables)
        if (value == null) {
            value = execution.getVariable(variableName);
            if (value != null) {
                log.trace("Variable '{}' resolved to: {}", variableName, value);
            }
        }

        return value != null ? (T) value : defaultValue;
    }
}
