package com.werkflow.engine.service;

import com.werkflow.engine.exception.ProcessNotFoundException;
import org.flowable.dmn.api.DmnDeployment;
import org.flowable.dmn.api.DmnDeploymentQuery;
import org.flowable.dmn.api.DmnHistoricDecisionExecutionQuery;
import org.flowable.dmn.api.DmnHistoryService;
import org.flowable.dmn.api.DmnRepositoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DmnDecisionServiceTest {

    @Mock private DmnRepositoryService dmnRepositoryService;
    @Mock private org.flowable.dmn.api.DmnDecisionService flowableDmnDecisionService;
    @Mock private DmnHistoryService dmnHistoryService;

    private DmnDecisionService service;

    @BeforeEach
    void setUp() {
        service = new DmnDecisionService(dmnRepositoryService, flowableDmnDecisionService, dmnHistoryService);
    }

    // --- F-DMN-1 tests ---

    @Test
    void deleteDeployment_ownTenantDeployment_deletes() {
        DmnDeployment deployment = mock(DmnDeployment.class);
        when(deployment.getTenantId()).thenReturn("acme");

        DmnDeploymentQuery query = mock(DmnDeploymentQuery.class);
        when(dmnRepositoryService.createDeploymentQuery()).thenReturn(query);
        when(query.deploymentId("dep-1")).thenReturn(query);
        when(query.singleResult()).thenReturn(deployment);

        service.deleteDeployment("dep-1", "acme");

        verify(dmnRepositoryService).deleteDeployment("dep-1");
    }

    @Test
    void deleteDeployment_crossTenantDeployment_throwsProcessNotFound() {
        DmnDeployment deployment = mock(DmnDeployment.class);
        when(deployment.getTenantId()).thenReturn("other-tenant");

        DmnDeploymentQuery query = mock(DmnDeploymentQuery.class);
        when(dmnRepositoryService.createDeploymentQuery()).thenReturn(query);
        when(query.deploymentId("dep-1")).thenReturn(query);
        when(query.singleResult()).thenReturn(deployment);

        assertThatThrownBy(() -> service.deleteDeployment("dep-1", "acme"))
                .isInstanceOf(ProcessNotFoundException.class)
                .hasMessageContaining("dep-1");

        verify(dmnRepositoryService, never()).deleteDeployment(anyString());
    }

    // --- F-DMN-2 test ---

    @Test
    void getExecutionHistory_filtersOnTenantId() {
        DmnHistoricDecisionExecutionQuery countQuery = mock(DmnHistoricDecisionExecutionQuery.class);
        DmnHistoricDecisionExecutionQuery listQuery = mock(DmnHistoricDecisionExecutionQuery.class);

        when(dmnHistoryService.createHistoricDecisionExecutionQuery())
                .thenReturn(countQuery)
                .thenReturn(listQuery);

        when(countQuery.decisionKey("approval")).thenReturn(countQuery);
        when(countQuery.tenantId("acme")).thenReturn(countQuery);
        when(countQuery.count()).thenReturn(0L);

        when(listQuery.decisionKey("approval")).thenReturn(listQuery);
        when(listQuery.tenantId("acme")).thenReturn(listQuery);
        when(listQuery.orderByEndTime()).thenReturn(listQuery);
        when(listQuery.desc()).thenReturn(listQuery);
        when(listQuery.listPage(0, 20)).thenReturn(java.util.List.of());

        Pageable pageable = PageRequest.of(0, 20);
        service.getExecutionHistory("approval", "acme", pageable);

        verify(countQuery).tenantId("acme");
        verify(listQuery).tenantId("acme");
    }
}
