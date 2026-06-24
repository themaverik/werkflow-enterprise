package com.werkflow.engine.service;

import com.werkflow.engine.dto.BundleDeploymentResponse;
import com.werkflow.engine.dto.ProcessDefinitionResponse;
import com.werkflow.engine.dto.dmn.DmnDecisionDto;
import com.werkflow.engine.exception.DanglingReferenceException;
import com.werkflow.engine.exception.ProcessNotFoundException;
import com.werkflow.engine.workflow.BpmnBundleRefExtractor;
import com.werkflow.engine.workflow.BpmnFormKeyPinner;
import com.werkflow.engine.workflow.DeployReferenceValidator;
import com.werkflow.engine.workflow.ProcessBundle;
import com.werkflow.engine.workflow.ProcessBundleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BundleDeploymentServiceTest {

    @Mock private DeployReferenceValidator deployReferenceValidator;
    @Mock private BpmnBundleRefExtractor refExtractor;
    @Mock private BpmnFormKeyPinner formKeyPinner;
    @Mock private ProcessDefinitionService processDefinitionService;
    @Mock private DmnDecisionService dmnDecisionService;
    @Mock private ProcessBundleRepository bundleRepository;

    @InjectMocks private BundleDeploymentService service;

    private static final String TENANT = "acme";
    private static final String BPMN = "<bpmn/>";

    @BeforeEach
    void setUp() {
        // Form pinning is exercised in BpmnFormKeyPinnerTest; here it passes the XML through.
        lenient().when(formKeyPinner.pinFormKeys(any(), any())).thenAnswer(inv -> inv.getArgument(0));
        // Validator is a no-op for happy-path tests; individual tests override when needed.
        lenient().doNothing().when(deployReferenceValidator).validate(any(), any());
    }

    @Test
    @DisplayName("deploys process and each referenced DMN under one shared parentDeploymentId")
    void bundles_process_and_dmns_under_shared_parent() {
        when(refExtractor.extract(BPMN))
                .thenReturn(new BpmnBundleRefExtractor.BundleRefs("capex-approval", Set.of("doa_routing"), Set.of()));
        when(bundleRepository.findMaxBundleVersion(TENANT, "capex-approval")).thenReturn(2);
        when(processDefinitionService.deployProcessDefinition(eq(BPMN), any(), any(), eq(TENANT)))
                .thenReturn(new ProcessDefinitionResponse());
        when(dmnDecisionService.getDecisionXml("doa_routing", TENANT)).thenReturn("<dmn/>");

        BundleDeploymentResponse result = service.deployBundle(BPMN, "Capex Approval", TENANT, "alice");

        String expectedParent = "acme:capex-approval:bundle:3";
        assertThat(result.bundleVersion()).isEqualTo(3);
        assertThat(result.parentDeploymentId()).isEqualTo(expectedParent);
        assertThat(result.bundledDecisions()).containsExactly("doa_routing");

        verify(processDefinitionService)
                .deployProcessDefinition(eq(BPMN), eq("capex-approval.bpmn20.xml"), eq(expectedParent), eq(TENANT));
        verify(dmnDecisionService).deployDecision("<dmn/>", "doa_routing", TENANT, expectedParent);

        ArgumentCaptor<ProcessBundle> saved = ArgumentCaptor.forClass(ProcessBundle.class);
        verify(bundleRepository).save(saved.capture());
        assertThat(saved.getValue().getParentDeploymentId()).isEqualTo(expectedParent);
        assertThat(saved.getValue().getBundleVersion()).isEqualTo(3);
        assertThat(saved.getValue().getCreatedBy()).isEqualTo("alice");
    }

    @Test
    @DisplayName("first bundle for a process starts at version 1")
    void first_bundle_is_version_one() {
        when(refExtractor.extract(BPMN))
                .thenReturn(new BpmnBundleRefExtractor.BundleRefs("simple", Set.of(), Set.of()));
        when(bundleRepository.findMaxBundleVersion(TENANT, "simple")).thenReturn(0);
        when(processDefinitionService.deployProcessDefinition(any(), any(), any(), any()))
                .thenReturn(new ProcessDefinitionResponse());

        BundleDeploymentResponse result = service.deployBundle(BPMN, "Simple", TENANT, "bob");

        assertThat(result.bundleVersion()).isEqualTo(1);
        assertThat(result.parentDeploymentId()).isEqualTo("acme:simple:bundle:1");
    }

    @Test
    @DisplayName("deploy aborts immediately when validator throws DanglingReferenceException")
    void validator_throws_deploy_aborts() {
        doThrow(new DanglingReferenceException(List.of("missing-form"), List.of("missing-decision")))
                .when(deployReferenceValidator).validate(BPMN, TENANT);

        assertThatThrownBy(() -> service.deployBundle(BPMN, "P1", TENANT, "carol"))
                .isInstanceOf(DanglingReferenceException.class)
                .satisfies(e -> {
                    DanglingReferenceException ex = (DanglingReferenceException) e;
                    assertThat(ex.getMissingForms()).containsExactly("missing-form");
                    assertThat(ex.getMissingDecisions()).containsExactly("missing-decision");
                });

        // Nothing should have been deployed or saved.
        verify(processDefinitionService, never()).deployProcessDefinition(any(), any(), any(), any());
        verify(bundleRepository, never()).save(any(ProcessBundle.class));
    }

    @Test
    @DisplayName("rollback redeploys the target version's exact artifacts as a new latest bundle version")
    void rollback_redeploys_target_as_new_version() {
        String sourceParent = "acme:capex-approval:bundle:1";
        when(bundleRepository.findByTenantIdAndProcessKeyAndBundleVersion(TENANT, "capex-approval", 1))
                .thenReturn(Optional.of(ProcessBundle.builder()
                        .tenantId(TENANT).processKey("capex-approval").bundleVersion(1)
                        .parentDeploymentId(sourceParent).build()));
        when(bundleRepository.findMaxBundleVersion(TENANT, "capex-approval")).thenReturn(3);
        // The source bundle's original resource name (display-name slug), distinct from the
        // processKey — rollback must redeploy under this same name, not "capex-approval.bpmn20.xml".
        String originalResourceName = "capital-expenditure-approval.bpmn20.xml";
        when(processDefinitionService.getBundleBpmnByParentDeployment(sourceParent, TENANT))
                .thenReturn(new ProcessDefinitionService.BundleBpmn(originalResourceName, BPMN));
        // Stub with a real deploymentId so the saga compensation tracker is non-null.
        when(processDefinitionService.deployProcessDefinition(eq(BPMN), any(), any(), eq(TENANT)))
                .thenReturn(ProcessDefinitionResponse.builder().deploymentId("bpmn-dep-1").build());
        when(dmnDecisionService.getDecisionsByParentDeployment(sourceParent, TENANT))
                .thenReturn(List.of(new DmnDecisionService.DecisionXml("doa_routing", "<dmn/>")));
        when(dmnDecisionService.deployDecision(eq("<dmn/>"), eq("doa_routing"), eq(TENANT), any()))
                .thenReturn(DmnDecisionDto.builder().deploymentId("dmn-dep-1").key("doa_routing").build());

        BundleDeploymentResponse result = service.rollbackToBundleVersion(TENANT, "capex-approval", 1, "carol");

        String newParent = "acme:capex-approval:bundle:4";
        assertThat(result.bundleVersion()).isEqualTo(4);
        assertThat(result.parentDeploymentId()).isEqualTo(newParent);
        assertThat(result.bundledDecisions()).containsExactly("doa_routing");

        // Redeploys the re-read BPMN as-is (already pinned) under the new parent, never re-pins,
        // and preserves the source bundle's original resource name (symmetric with deployBundle).
        verify(formKeyPinner, never()).pinFormKeys(any(), any());
        verify(processDefinitionService)
                .deployProcessDefinition(eq(BPMN), eq(originalResourceName), eq(newParent), eq(TENANT));
        verify(dmnDecisionService).deployDecision("<dmn/>", "doa_routing", TENANT, newParent);

        ArgumentCaptor<ProcessBundle> saved = ArgumentCaptor.forClass(ProcessBundle.class);
        verify(bundleRepository).save(saved.capture());
        assertThat(saved.getValue().getBundleVersion()).isEqualTo(4);
        assertThat(saved.getValue().getCreatedBy()).isEqualTo("carol");
    }

    @Test
    @DisplayName("rollback throws when the target bundle version does not exist")
    void rollback_missing_version_throws() {
        when(bundleRepository.findByTenantIdAndProcessKeyAndBundleVersion(TENANT, "capex-approval", 99))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rollbackToBundleVersion(TENANT, "capex-approval", 99, "carol"))
                .isInstanceOf(ProcessNotFoundException.class);
    }

    @Test
    @DisplayName("rollback DMN deploy failure triggers saga compensation: BPMN deleted, no bundle saved, exception rethrown")
    void rollback_dmn_failure_compensates_bpmn_and_does_not_save() {
        String sourceParent = "acme:capex-approval:bundle:2";
        when(bundleRepository.findByTenantIdAndProcessKeyAndBundleVersion(TENANT, "capex-approval", 2))
                .thenReturn(Optional.of(ProcessBundle.builder()
                        .tenantId(TENANT).processKey("capex-approval").bundleVersion(2)
                        .parentDeploymentId(sourceParent).build()));
        when(bundleRepository.findMaxBundleVersion(TENANT, "capex-approval")).thenReturn(2);
        when(processDefinitionService.getBundleBpmnByParentDeployment(sourceParent, TENANT))
                .thenReturn(new ProcessDefinitionService.BundleBpmn("capex.bpmn20.xml", BPMN));
        // BPMN deploy succeeds and returns a known deploymentId for compensation verification.
        when(processDefinitionService.deployProcessDefinition(eq(BPMN), any(), any(), eq(TENANT)))
                .thenReturn(ProcessDefinitionResponse.builder().deploymentId("bpmn-dep-comp").build());
        // Two decisions; first succeeds, second throws — forcing compensation mid-loop.
        when(dmnDecisionService.getDecisionsByParentDeployment(sourceParent, TENANT))
                .thenReturn(List.of(
                        new DmnDecisionService.DecisionXml("doa_routing", "<dmn1/>"),
                        new DmnDecisionService.DecisionXml("approval_matrix", "<dmn2/>")));
        when(dmnDecisionService.deployDecision(eq("<dmn1/>"), eq("doa_routing"), eq(TENANT), any()))
                .thenReturn(DmnDecisionDto.builder().deploymentId("dmn-dep-comp-1").key("doa_routing").build());
        when(dmnDecisionService.deployDecision(eq("<dmn2/>"), eq("approval_matrix"), eq(TENANT), any()))
                .thenThrow(new RuntimeException("simulated DMN deploy failure"));

        assertThatThrownBy(() -> service.rollbackToBundleVersion(TENANT, "capex-approval", 2, "carol"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("simulated DMN deploy failure");

        // bundleRepository.save must never be called — the failure happened before it.
        verify(bundleRepository, never()).save(any(ProcessBundle.class));

        // Compensation: the successfully deployed DMN must be deleted.
        verify(dmnDecisionService).deleteDeployment("dmn-dep-comp-1", TENANT);

        // Compensation: the BPMN deployment must be deleted (cascade=true).
        verify(processDefinitionService).deleteProcessDefinition("bpmn-dep-comp", true);
    }
}
