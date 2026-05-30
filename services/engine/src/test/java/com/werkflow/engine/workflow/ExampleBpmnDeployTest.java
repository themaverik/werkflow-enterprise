package com.werkflow.engine.workflow;

import com.werkflow.engine.testsupport.WerkflowTestProcessEngine;
import org.flowable.engine.RepositoryService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Sanity-checks that every BPMN edited as part of the ADR-027/FIX 1-4 work
 * deploys and parses cleanly on the in-memory engine.
 *
 * <p>Does NOT execute processes — deployment alone confirms that:
 * <ul>
 *   <li>The XML is valid BPMN 2.0.</li>
 *   <li>Flowable's BPMN parser accepts it (parse handlers, validators).</li>
 *   <li>All element IDs referenced in sequence flows exist (parse-time wiring).</li>
 * </ul>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Example BPMN deploy-sanity — all four edited files parse and deploy cleanly")
class ExampleBpmnDeployTest {

    private static final org.flowable.engine.delegate.JavaDelegate STUB =
            execution -> { /* no-op */ };

    private static final Map<String, Object> STUB_BEANS = Map.of(
            "externalApiCallDelegate", STUB,
            "notificationDelegate",    STUB
    );

    private WerkflowTestProcessEngine testEngine;
    private RepositoryService repositoryService;

    @BeforeAll
    void bootEngine() {
        testEngine = WerkflowTestProcessEngine.build("bpmnDeploySanity", STUB_BEANS);
        repositoryService = testEngine.getProcessEngine().getRepositoryService();
    }

    @AfterAll
    void shutdown() {
        if (testEngine != null) {
            testEngine.close();
        }
    }

    @Test
    @DisplayName("general-approval.bpmn20.xml deploys without error")
    void generalApprovalDeploys() {
        assertThatCode(() -> {
            var deployment = repositoryService.createDeployment()
                    .addClasspathResource("processes/examples/general-approval.bpmn20.xml")
                    .name("sanity-general-approval")
                    .deploy();
            assertThat(deployment.getId()).isNotNull();
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("procurement-approval-process.bpmn20.xml deploys without error")
    void procurementApprovalDeploys() {
        assertThatCode(() -> {
            var deployment = repositoryService.createDeployment()
                    .addClasspathResource("processes/examples/procurement-approval-process.bpmn20.xml")
                    .name("sanity-procurement-approval")
                    .deploy();
            assertThat(deployment.getId()).isNotNull();
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("asset-request-process.bpmn20.xml deploys without error")
    void assetRequestDeploys() {
        assertThatCode(() -> {
            var deployment = repositoryService.createDeployment()
                    .addClasspathResource("processes/examples/asset-request-process.bpmn20.xml")
                    .name("sanity-asset-request")
                    .deploy();
            assertThat(deployment.getId()).isNotNull();
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("onboarding-checklist.bpmn20.xml deploys without error")
    void onboardingChecklistDeploys() {
        assertThatCode(() -> {
            var deployment = repositoryService.createDeployment()
                    .addClasspathResource("processes/examples/onboarding-checklist.bpmn20.xml")
                    .name("sanity-onboarding-checklist")
                    .deploy();
            assertThat(deployment.getId()).isNotNull();
        }).doesNotThrowAnyException();
    }
}
