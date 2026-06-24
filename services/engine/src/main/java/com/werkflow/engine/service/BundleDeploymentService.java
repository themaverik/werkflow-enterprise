package com.werkflow.engine.service;

import com.werkflow.engine.dto.BundleDeploymentResponse;
import com.werkflow.engine.dto.ProcessDefinitionResponse;
import com.werkflow.engine.dto.dmn.DmnDecisionDto;
import com.werkflow.engine.exception.ProcessNotFoundException;
import com.werkflow.engine.workflow.BpmnBundleRefExtractor;
import com.werkflow.engine.workflow.BpmnFormKeyPinner;
import com.werkflow.engine.workflow.DeployReferenceValidator;
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

    private final DeployReferenceValidator deployReferenceValidator;
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

        // Fail loud: all referenced forms and decisions must exist for this tenant before
        // any Flowable artifact is written. Throws DanglingReferenceException (→ HTTP 422)
        // with the full list of missing keys on the first validation failure.
        deployReferenceValidator.validate(bpmnXml, tenantId);

        BpmnBundleRefExtractor.BundleRefs refs = refExtractor.extract(bpmnXml);
        String processKey = refs.processKey();

        int bundleVersion = bundleRepository.findMaxBundleVersion(tenantId, processKey) + 1;
        String parentDeploymentId = "%s:%s:bundle:%d".formatted(tenantId, processKey, bundleVersion);

        // Pin each static formKey to its current active version so the bundle's forms are
        // reproducible for in-flight instances (ADR-026 P2 / F1). The standalone /deploy path
        // intentionally leaves keys bare (resolve-latest).
        String pinnedBpmn = formKeyPinner.pinFormKeys(bpmnXml, tenantId);

        String resourceName = name.toLowerCase().replaceAll("\\s+", "-") + ".bpmn20.xml";
        ProcessDefinitionResponse process =
                processDefinitionService.deployProcessDefinition(pinnedBpmn, resourceName, parentDeploymentId, tenantId);

        List<String> bundled = new ArrayList<>();
        for (String decisionKey : refs.decisionRefs()) {
            String dmnXml = dmnDecisionService.getDecisionXml(decisionKey, tenantId);
            dmnDecisionService.deployDecision(dmnXml, decisionKey, tenantId, parentDeploymentId);
            bundled.add(decisionKey);
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

        log.info("Deployed bundle {} (process={}, bundled={})",
                parentDeploymentId, processKey, bundled);

        return new BundleDeploymentResponse(process, processKey, bundleVersion, parentDeploymentId, bundled);
    }

    /**
     * Rolls a process back to a prior bundle version by redeploying that version's exact
     * artifacts (its BPMN and the DMN versions it was bundled with) as a NEW latest bundle
     * version (ADR-026 Phase 3). Flowable-idiomatic: history is preserved and in-flight
     * instances are left running on the version they started on (no instance migration).
     *
     * <p><b>Why {@code @Transactional} is absent here:</b> A rollback spans two independent
     * Flowable engines (BPMN repository + DMN repository) and a JPA write. Each Flowable engine
     * runs its own command executor and commits its own transaction — they cannot be enrolled in a
     * shared Spring transaction. Wrapping this method in {@code @Transactional} would therefore be
     * redundant-or-harmful: it would create a shared transaction context that Flowable ignores for
     * its own commits, yet would roll back the JPA {@code bundleRepository.save} on any failure
     * <em>after</em> the Flowable deploys have already self-committed — giving no rollback
     * guarantee while adding confusion. Instead, we guarantee all-or-nothing by explicit saga
     * compensation: each deploy self-commits, and on any failure we compensate by deleting every
     * Flowable deployment that was created during this attempt, then rethrow.
     *
     * @throws ProcessNotFoundException if no bundle with {@code targetBundleVersion} exists
     */
    public BundleDeploymentResponse rollbackToBundleVersion(
            String tenantId, String processKey, int targetBundleVersion, String deployedBy) {

        ProcessBundle target = bundleRepository
                .findByTenantIdAndProcessKeyAndBundleVersion(tenantId, processKey, targetBundleVersion)
                .orElseThrow(() -> new ProcessNotFoundException(
                        "No bundle version %d for process '%s'".formatted(targetBundleVersion, processKey)));
        String sourceParentDeploymentId = target.getParentDeploymentId();

        int newBundleVersion = bundleRepository.findMaxBundleVersion(tenantId, processKey) + 1;
        String newParentDeploymentId = "%s:%s:bundle:%d".formatted(tenantId, processKey, newBundleVersion);

        // Re-read source artifacts BEFORE any write — these reads are idempotent and safe to
        // retry. The BPMN already carries its original formKey@version pins, so it is redeployed
        // as-is (no re-pinning). Reusing the source bundle's original resource name keeps rollback
        // symmetric with deployBundle.
        ProcessDefinitionService.BundleBpmn source =
                processDefinitionService.getBundleBpmnByParentDeployment(sourceParentDeploymentId, tenantId);
        List<DmnDecisionService.DecisionXml> sourceDecisions =
                dmnDecisionService.getDecisionsByParentDeployment(sourceParentDeploymentId, tenantId);

        // Track created Flowable deployment ids for saga compensation on failure.
        String createdBpmnDeploymentId = null;
        List<String> createdDmnDeploymentIds = new ArrayList<>();

        try {
            ProcessDefinitionResponse process = processDefinitionService.deployProcessDefinition(
                    source.xml(), source.resourceName(), newParentDeploymentId, tenantId);
            createdBpmnDeploymentId = process.getDeploymentId();

            List<String> bundled = new ArrayList<>();
            for (DmnDecisionService.DecisionXml decision : sourceDecisions) {
                DmnDecisionDto deployed =
                        dmnDecisionService.deployDecision(decision.xml(), decision.key(), tenantId, newParentDeploymentId);
                createdDmnDeploymentIds.add(deployed.getDeploymentId());
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

            return new BundleDeploymentResponse(process, processKey, newBundleVersion, newParentDeploymentId, bundled);

        } catch (RuntimeException failure) {
            // Saga compensation: delete any Flowable deployments created during this attempt so we
            // do not leave orphaned-but-visible deployments. Each delete is best-effort — a failure
            // here must not mask the original exception.
            for (String dmnDepId : createdDmnDeploymentIds) {
                if (dmnDepId == null || dmnDepId.isBlank()) {
                    continue;
                }
                try {
                    dmnDecisionService.deleteDeployment(dmnDepId, tenantId);
                } catch (Exception ex) {
                    log.error("Rollback compensation: failed to delete DMN deployment {} (tenant={}); "
                            + "leaving as orphan — manual cleanup may be needed",
                            dmnDepId, tenantId, ex);
                }
            }
            if (createdBpmnDeploymentId != null && !createdBpmnDeploymentId.isBlank()) {
                try {
                    processDefinitionService.deleteProcessDefinition(createdBpmnDeploymentId, true);
                } catch (Exception ex) {
                    log.error("Rollback compensation: failed to delete BPMN deployment {} (tenant={}); "
                            + "leaving as orphan — manual cleanup may be needed",
                            createdBpmnDeploymentId, tenantId, ex);
                }
            }
            throw failure;
        }
    }
}
