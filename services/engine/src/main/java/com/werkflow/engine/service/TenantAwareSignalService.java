package com.werkflow.engine.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RuntimeService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Tenant-scoped wrapper around Flowable signal APIs (ADR-008).
 *
 * All signal dispatches MUST go through this service so signals are scoped to the correct tenant.
 * Never call {@code runtimeService.signalEventReceived()} directly — it broadcasts to all tenants.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantAwareSignalService {

    private final RuntimeService runtimeService;

    /**
     * Dispatch a signal synchronously, scoped to the given tenant.
     * Only process instances belonging to {@code tenantId} receive the signal.
     *
     * @param signalName name of the signal catch event (e.g. "approvalGranted")
     * @param tenantId   tenant that owns the target process instances
     * @param variables  variables to attach to the signal (may be empty)
     */
    @Transactional
    public void sendSignal(String signalName, String tenantId, Map<String, Object> variables) {
        validateInputs(signalName, tenantId);
        log.info("Sending signal '{}' to tenant '{}'", signalName, tenantId);
        runtimeService.signalEventReceivedWithTenantId(signalName, variables, tenantId);
    }

    /**
     * Dispatch a signal synchronously with no additional variables.
     */
    @Transactional
    public void sendSignal(String signalName, String tenantId) {
        sendSignal(signalName, tenantId, Map.of());
    }

    /**
     * Dispatch a signal asynchronously, scoped to the given tenant.
     * Each matching execution receives the signal in a separate transaction via the job executor.
     *
     * @param signalName name of the signal catch event
     * @param tenantId   tenant that owns the target process instances
     * @param variables  variables to attach to the signal
     */
    @Async
    @Transactional
    public void sendSignalAsync(String signalName, String tenantId, Map<String, Object> variables) {
        validateInputs(signalName, tenantId);
        log.info("Sending async signal '{}' to tenant '{}'", signalName, tenantId);
        runtimeService.signalEventReceivedAsyncWithTenantId(signalName, tenantId);
    }

    /**
     * Dispatch a signal asynchronously with no additional variables.
     */
    @Async
    @Transactional
    public void sendSignalAsync(String signalName, String tenantId) {
        sendSignalAsync(signalName, tenantId, Map.of());
    }

    private void validateInputs(String signalName, String tenantId) {
        if (signalName == null || signalName.isBlank()) {
            throw new IllegalArgumentException("signalName must not be blank");
        }
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank — signals must be tenant-scoped");
        }
    }
}
