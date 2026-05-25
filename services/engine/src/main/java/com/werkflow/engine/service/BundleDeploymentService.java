package com.werkflow.engine.service;

import com.werkflow.engine.dto.BundleDeploymentResponse;
import com.werkflow.engine.dto.ProcessDefinitionResponse;
import com.werkflow.engine.exception.ProcessNotFoundException;
import com.werkflow.engine.workflow.BpmnBundleRefExtractor;
import com.werkflow.engine.workflow.BpmnFormKeyPinner;
import com.werkflow.engine.workflow.ProcessBundle;
import com.werkflow.engine.workflow.ProcessBundleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Deploys a process and its referenced DMNs as one bundle under a shared
 * {@code parentDeploymentId} (ADR-026 Phase 1), so that same-deployment binding
 * (Phase 2) resolves to the pinned DMN versions instead of latest.
 *
 * <p>The {@code parentDeploymentId} convention is
 * {@code {tenantId}:{processKey}:bundle:{bundleVersion}}, with {@code bundleVersion}
 * monotonic per {@code (tenantId, processKey)}. A referenced decision that cannot be
 * resolved is skipped (logged) rather than failing the deploy — it will resolve to
 * latest at runtime until it is deployed and the bundle is redeployed.
 *
 * <p><b>Known limitation (ADR-026):</b> Flowable deployments are written through their
 * own command executor and are not unwound by this method's JPA transaction. If the
 * BPMN deploy or the bundle-row save fails after one or more artifacts were deployed,
 * those Flowable deployments persist as harmless orphans (same-deployment binding keys
 * off the {@code parentDeploymentId} string, not a FK). This mirrors the pre-existing
 * standalone deploy path and is acceptable for Phase 1.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BundleDeploymentService {

    private final BpmnBundleRefExtractor refExtractor;
    private final BpmnFormKeyPinner formKeyPinner;
    private final ProcessDefinitionService processDefinitionService;
    private final DmnDecisionService dmnDecisionService;
    private final ProcessBundleRepository bundleRepository;

    @Transactional
    public BundleDeploymentResponse deployBundle(String bpmnXml, String name, String tenantId, String deployedBy) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Process name is required");
        }
        if (bpmnXml == null || bpmnXml.isBlank()) {
            throw new IllegalArgumentException("BPMN XML is required");
        }

        BpmnBundleRefExtractor.BundleRefs refs = refExtractor.extract(bpmnXml);
        String processKey = refs.processKey();

        int bundleVersion = bundleRepository.findMaxBundleVersion(tenantId, processKey) + 1;
        String parentDeploymentId = "%s:%s:bundle:%d".formatted(tenantId, processKey, bundleVersion);

        // Pin each static formKey to its current active version so the bundle's forms are
        // reproducible for in-flight instances (ADR-026 P2 / F1). The standalone /deploy path
        // intentionally leaves keys bare (resolve-latest).
        String pinnedBpmn = formKeyPinner.pinFormKeys(bpmnXml);

        String resourceName = name.toLowerCase().replaceAll("\\s+", "-") + ".bpmn20.xml";
        ProcessDefinitionResponse process =
                processDefinitionService.deployProcessDefinition(pinnedBpmn, resourceName, parentDeploymentId, tenantId);

        List<String> bundled = new ArrayList<>();
        List<String> unbundled = new ArrayList<>();
        for (String decisionKey : refs.decisionRefs()) {
            try {
                String dmnXml = dmnDecisionService.getDecisionXml(decisionKey, tenantId);
                dmnDecisionService.deployDecision(dmnXml, decisionKey, tenantId, parentDeploymentId);
                bundled.add(decisionKey);
            } catch (ProcessNotFoundException | IllegalStateException e) {
                // Decision not yet deployed for this tenant — pin it on a later redeploy.
                log.warn("Bundle {}: referenced decision '{}' not bundled ({}); it will resolve to latest at runtime",
                        parentDeploymentId, decisionKey, e.getMessage());
                unbundled.add(decisionKey);
            }
        }

        try {
            bundleRepository.save(ProcessBundle.builder()
                    .tenantId(tenantId)
                    .processKey(processKey)
                    .bundleVersion(bundleVersion)
                    .parentDeploymentId(parentDeploymentId)
                    .createdBy(deployedBy)
                    .build());
        } catch (DataIntegrityViolationException e) {
            // A concurrent bundle deploy claimed the same version first.
            throw new IllegalStateException(
                    "Concurrent bundle deploy for (%s, %s); retry the request".formatted(tenantId, processKey), e);
        }

        log.info("Deployed bundle {} (process={}, bundled={}, unbundled={})",
                parentDeploymentId, processKey, bundled, unbundled);

        return new BundleDeploymentResponse(process, processKey, bundleVersion, parentDeploymentId, bundled, unbundled);
    }

    /**
     * Rolls a process back to a prior bundle version by redeploying that version's exact
     * artifacts (its BPMN and the DMN versions it was bundled with) as a NEW latest bundle
     * version (ADR-026 Phase 3). Flowable-idiomatic: history is preserved and in-flight
     * instances are left running on the version they started on (no instance migration).
     *
     * @throws ProcessNotFoundException if no bundle with {@code targetBundleVersion} exists
     */
    @Transactional
    public BundleDeploymentResponse rollbackToBundleVersion(
            String tenantId, String processKey, int targetBundleVersion, String deployedBy) {

        ProcessBundle target = bundleRepository
                .findByTenantIdAndProcessKeyAndBundleVersion(tenantId, processKey, targetBundleVersion)
                .orElseThrow(() -> new ProcessNotFoundException(
                        "No bundle version %d for process '%s'".formatted(targetBundleVersion, processKey)));
        String sourceParentDeploymentId = target.getParentDeploymentId();

        int newBundleVersion = bundleRepository.findMaxBundleVersion(tenantId, processKey) + 1;
        String newParentDeploymentId = "%s:%s:bundle:%d".formatted(tenantId, processKey, newBundleVersion);

        // Re-read and redeploy the target version's exact artifacts; the BPMN already carries
        // its original formKey@version pins, so it is redeployed as-is (no re-pinning). Reusing the
        // source bundle's original resource name keeps rollback symmetric with deployBundle.
        ProcessDefinitionService.BundleBpmn source =
                processDefinitionService.getBundleBpmnByParentDeployment(sourceParentDeploymentId, tenantId);
        ProcessDefinitionResponse process = processDefinitionService.deployProcessDefinition(
                source.xml(), source.resourceName(), newParentDeploymentId, tenantId);

        List<String> bundled = new ArrayList<>();
        for (DmnDecisionService.DecisionXml decision : dmnDecisionService.getDecisionsByParentDeployment(sourceParentDeploymentId, tenantId)) {
            dmnDecisionService.deployDecision(decision.xml(), decision.key(), tenantId, newParentDeploymentId);
            bundled.add(decision.key());
        }

        try {
            bundleRepository.save(ProcessBundle.builder()
                    .tenantId(tenantId)
                    .processKey(processKey)
                    .bundleVersion(newBundleVersion)
                    .parentDeploymentId(newParentDeploymentId)
                    .createdBy(deployedBy)
                    .build());
        } catch (DataIntegrityViolationException e) {
            throw new IllegalStateException(
                    "Concurrent bundle deploy for (%s, %s); retry the request".formatted(tenantId, processKey), e);
        }

        log.info("Rolled back process {} to bundle v{} as new bundle v{} ({})",
                processKey, targetBundleVersion, newBundleVersion, newParentDeploymentId);

        return new BundleDeploymentResponse(process, processKey, newBundleVersion, newParentDeploymentId, bundled, List.of());
    }
}
