package com.werkflow.engine.workflow;

import com.werkflow.engine.testsupport.WerkflowTestProcessEngine;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Universal process quality gate — every example BPMN must deploy AND start
 * without throwing an engine exception.
 *
 * <p>This is the hard gate described in ADR-028 §"Quality Levels":
 * <ul>
 *   <li>Level 1 (deploy-only): {@link ExampleBpmnDeployTest}</li>
 *   <li><b>Level 2 (deploy+start): this test</b> — asserts that the engine can
 *       instantiate each process given a representative set of start variables.
 *       All DMNs are pre-deployed so DMN service tasks execute correctly.</li>
 *   <li>Level 3 (routing tests): process-specific tests (e.g. CapexApprovalRoutingTest).</li>
 * </ul>
 *
 * <p>Any new BPMN added to {@code processes/examples/} must be listed in
 * {@link #allProcessKeys()} so that authoring a new process automatically
 * triggers this gate on the next CI run — no per-process test is required for
 * the basic deploy+start contract.
 *
 * <p>Stub delegates ({@code externalApiCallDelegate}, {@code notificationDelegate})
 * are registered so that connector-type service tasks complete without error.
 * The {@link #START_VARS} map supplies all DMN input variables needed by
 * capex, leave, and procurement decision tables.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Universal process quality gate — every example BPMN deploys and starts clean")
class AllProcessesDeployAndStartTest {

    private static final org.flowable.engine.delegate.JavaDelegate EXTERNAL_API_STUB =
            execution -> { /* no-op — connector service tasks complete without external call */ };

    private static final org.flowable.engine.delegate.JavaDelegate NOTIFICATION_STUB =
            execution -> { /* no-op */ };

    private static final Map<String, Object> STUB_BEANS = Map.of(
            "externalApiCallDelegate", EXTERNAL_API_STUB,
            "notificationDelegate",    NOTIFICATION_STUB
    );

    /**
     * Comprehensive start-variable map covering all example process inputs.
     * Each process only reads the variables it declares; extras are silently
     * ignored by Flowable's execution context.
     */
    private static final Map<String, Object> START_VARS;

    static {
        Map<String, Object> vars = new HashMap<>();
        // capex-approval-process (ADR-029 Phase 2): capexOwner drives DMN group resolution
        vars.put("requestAmount", 10_000);
        vars.put("capexOwner", "FIN");
        // procurement-approval-process: amount <= 50000 → DIRECT_PURCHASE path (no human task)
        vars.put("amount", 500);
        vars.put("category", "IT");
        vars.put("requestId", "TEST-001");
        vars.put("itemName", "Test Item");
        vars.put("quantity", 1);
        vars.put("estimatedCost", 500);
        // leave-request: type + days drive the leave-approval DMN
        vars.put("days", 3);
        vars.put("leaveType", "ANNUAL");
        // onboarding-checklist: buddyRequired controls the buddy-assignment branch
        vars.put("buddyRequired", false);
        vars.put("employeeEmail", "test@example.com");
        // asset-request-process: custodianGroupName set by createAssetRecord service task at runtime;
        // stub skips that, so provide it directly so the custodianReview user task resolves
        vars.put("custodianGroupName", "CUSTODIAN");
        // capex-approval-process: budgetAvailable set by checkBudget service task;
        // stub skips that, so provide true so budgetGateway takes the approved path
        vars.put("budgetAvailable", true);
        START_VARS = Map.copyOf(vars);
    }

    private WerkflowTestProcessEngine testEngine;
    private RepositoryService repositoryService;
    private RuntimeService runtimeService;

    @BeforeAll
    void bootAndDeployAll() {
        testEngine = WerkflowTestProcessEngine.build("allProcessesQualityGate", STUB_BEANS);
        repositoryService = testEngine.getProcessEngine().getRepositoryService();
        runtimeService   = testEngine.getProcessEngine().getRuntimeService();

        // Pre-deploy all DMN resources. DMN must precede the BPMNs that reference them
        // because Flowable resolves DMN service tasks at execution time, not deploy time,
        // but deploying DMNs first ensures the DmnRepositoryService is ready.
        repositoryService.createDeployment()
                .addClasspathResource("dmn-examples/capex-approver-resolution.dmn")
                .addClasspathResource("dmn-examples/leave-approval.dmn")
                .addClasspathResource("dmn-examples/procurement-matrix.dmn")
                .name("quality-gate-dmn-all")
                .deploy();

        // Deploy all eight example BPMNs in a single bundle.
        repositoryService.createDeployment()
                .addClasspathResource("processes/examples/asset-request-process.bpmn20.xml")
                .addClasspathResource("processes/examples/capex-approval-process.bpmn20.xml")
                .addClasspathResource("processes/examples/event-ticket-request.bpmn20.xml")
                .addClasspathResource("processes/examples/finance-approval-process.bpmn20.xml")
                .addClasspathResource("processes/examples/general-approval.bpmn20.xml")
                .addClasspathResource("processes/examples/leave-request.bpmn20.xml")
                .addClasspathResource("processes/examples/onboarding-checklist.bpmn20.xml")
                .addClasspathResource("processes/examples/procurement-approval-process.bpmn20.xml")
                .name("quality-gate-bpmn-all")
                .deploy();
    }

    @AfterAll
    void shutdown() {
        if (testEngine != null) {
            testEngine.close();
        }
    }

    /**
     * Every process key listed here is tested by the parameterized gate below.
     * Add the process key here when authoring a new BPMN in processes/examples/.
     */
    static Stream<String> allProcessKeys() {
        return Stream.of(
                "asset-request-process",
                "capex-approval-process",
                "event-ticket-request",
                "finance-approval-process",
                "general-approval",
                "leave-request",
                "onboarding-checklist",
                "procurement-approval-process"
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allProcessKeys")
    @DisplayName("deploy+start gate")
    void processDeploysAndStarts(String processKey) {
        assertThatCode(() -> {
            ProcessInstance pi = runtimeService.startProcessInstanceByKey(
                    processKey, START_VARS);
            assertThat(pi)
                    .as("process instance must be created for '" + processKey + "'")
                    .isNotNull();
            assertThat(pi.getId())
                    .as("process instance ID must not be blank")
                    .isNotBlank();
        }).doesNotThrowAnyException();
    }
}
