package com.werkflow.engine.service;

import com.werkflow.engine.dto.BundleDeploymentResponse;
import com.werkflow.engine.dto.ProcessDefinitionResponse;
import com.werkflow.engine.exception.ProcessNotFoundException;
import com.werkflow.engine.workflow.BpmnBundleRefExtractor;
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

        String resourceName = name.toLowerCase().replaceAll("\\s+", "-") + ".bpmn20.xml";
        ProcessDefinitionResponse process =
                processDefinitionService.deployProcessDefinition(bpmnXml, resourceName, parentDeploymentId, tenantId);

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
}
