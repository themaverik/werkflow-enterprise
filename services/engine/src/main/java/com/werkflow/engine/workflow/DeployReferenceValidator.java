package com.werkflow.engine.workflow;

import com.werkflow.engine.exception.DanglingReferenceException;
import com.werkflow.engine.service.DmnDecisionService;
import com.werkflow.engine.service.FormSchemaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates that every static form key and DMN decision key referenced in a BPMN definition
 * exists for the given tenant before the deploy is allowed to proceed.
 *
 * <p>Aggregates ALL missing refs and throws once with the full list — the caller gets a single
 * HTTP 422 enumerating every gap rather than one error per missing artifact.
 *
 * <p>Form existence uses {@link FormSchemaService#formExistsActiveVersion(String, String)} so
 * only forms with an active version satisfy the check — an archived-only form is reported as
 * missing. Decision existence uses {@link DmnDecisionService#decisionExists(String, String)}
 * which is tenant-strict.
 */
@Component
@RequiredArgsConstructor
public class DeployReferenceValidator {

    private final BpmnBundleRefExtractor refExtractor;
    private final FormSchemaService formSchemaService;
    private final DmnDecisionService dmnDecisionService;

    /**
     * Validates all form and decision references in the given BPMN XML against the tenant.
     *
     * @param bpmnXml  raw BPMN 2.0 XML
     * @param tenantId the deploying tenant (null/blank normalised inside the checked services)
     * @throws DanglingReferenceException if any form or decision referenced in the BPMN does
     *                                    not exist for this tenant
     */
    public void validate(String bpmnXml, String tenantId) {
        BpmnBundleRefExtractor.BundleRefs refs = refExtractor.extract(bpmnXml);

        List<String> missingForms = new ArrayList<>();
        for (String formKey : refs.formRefs()) {
            if (!formSchemaService.formExistsActiveVersion(formKey, tenantId)) {
                missingForms.add(formKey);
            }
        }

        List<String> missingDecisions = new ArrayList<>();
        for (String decisionKey : refs.decisionRefs()) {
            if (!dmnDecisionService.decisionExists(decisionKey, tenantId)) {
                missingDecisions.add(decisionKey);
            }
        }

        if (!missingForms.isEmpty() || !missingDecisions.isEmpty()) {
            throw new DanglingReferenceException(missingForms, missingDecisions);
        }
    }
}
