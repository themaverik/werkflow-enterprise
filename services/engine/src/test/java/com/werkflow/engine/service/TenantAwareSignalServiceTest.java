package com.werkflow.engine.service;

import org.flowable.engine.RuntimeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantAwareSignalServiceTest {

    @Mock private RuntimeService runtimeService;
    @InjectMocks private TenantAwareSignalService signalService;

    @Test
    void sendSignal_callsSignalEventReceivedWithTenantId() {
        signalService.sendSignal("approvalGranted", "tenant-a", Map.of("amount", 5000));

        verify(runtimeService).signalEventReceivedWithTenantId("approvalGranted", Map.of("amount", 5000), "tenant-a");
    }

    @Test
    void sendSignal_noVars_callsWithEmptyVariables() {
        signalService.sendSignal("orderReady", "tenant-b");

        verify(runtimeService).signalEventReceivedWithTenantId("orderReady", Map.of(), "tenant-b");
    }

    @Test
    void sendSignal_throwsWhenSignalNameBlank() {
        assertThatThrownBy(() -> signalService.sendSignal("", "tenant-a"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("signalName");
    }

    @Test
    void sendSignal_throwsWhenTenantIdNull() {
        assertThatThrownBy(() -> signalService.sendSignal("stockLow", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");
    }

    @Test
    void sendSignal_throwsWhenTenantIdBlank() {
        assertThatThrownBy(() -> signalService.sendSignal("stockLow", "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");
    }

    @Test
    void sendSignalAsync_callsSignalEventReceivedAsyncWithTenantId() {
        signalService.sendSignalAsync("budgetApproved", "tenant-c", Map.of());

        verify(runtimeService).signalEventReceivedAsyncWithTenantId("budgetApproved", "tenant-c");
    }

    @Test
    void sendSignalAsync_throwsWhenTenantIdMissing() {
        assertThatThrownBy(() -> signalService.sendSignalAsync("signal", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void neverCallsNonTenantScopedSignalMethod() {
        signalService.sendSignal("any", "tenant-x");

        verify(runtimeService, never()).signalEventReceived(anyString());
        verify(runtimeService, never()).signalEventReceived(anyString(), anyMap());
    }
}
