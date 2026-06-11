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
 * Sanity-checks that the two curated example BPMNs deploy and parse cleanly on the
 * in-memory engine.
 *
 * <p>Does NOT execute processes — deployment alone confirms that:
 * <ul>
 *   <li>The XML is valid BPMN 2.0.</li>
 *   <li>Flowable's BPMN parser accepts it (parse handlers, validators).</li>
 *   <li>All element IDs referenced in sequence flows exist (parse-time wiring).</li>
 * </ul>
 *
 * <p>For the deploy+start quality gate (including DMN execution), see
 * {@link AllProcessesDeployAndStartTest}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Example BPMN deploy-sanity — curated example files parse and deploy cleanly")
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
    @DisplayName("capex-approval-process.bpmn20.xml deploys without error")
    void capexApprovalDeploys() {
        assertThatCode(() -> {
            var deployment = repositoryService.createDeployment()
                    .addClasspathResource("examples/tenants/default/bpmn/capex-approval-process.bpmn20.xml")
                    .name("sanity-capex-approval")
                    .deploy();
            assertThat(deployment.getId()).isNotNull();
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("leave-request.bpmn20.xml deploys without error")
    void leaveRequestDeploys() {
        assertThatCode(() -> {
            var deployment = repositoryService.createDeployment()
                    .addClasspathResource("examples/tenants/default/bpmn/leave-request.bpmn20.xml")
                    .name("sanity-leave-request")
                    .deploy();
            assertThat(deployment.getId()).isNotNull();
        }).doesNotThrowAnyException();
    }
}
