package com.werkflow.engine.workflow;

import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Probes, against a live in-memory Flowable 7.2 engine, whether a BPMN-modeled signal throw
 * crosses tenant boundaries — the F-EV-1 question raised by the M4.13 event-type audit discovery.
 *
 * <p>Context: the platform once had a {@code TenantAwareSignalService} wrapping the Java
 * {@code RuntimeService.signalEventReceivedWithTenantId(...)} API, but the audit found it had zero
 * production callers (and it was subsequently removed) — signals are only ever thrown via BPMN
 * {@code <intermediateThrowEvent>} +
 * {@code <signalEventDefinition>}, which Flowable routes through its internal
 * {@code ThrowSignalEventActivityBehavior}, NOT that Java API. Both shipped example processes
 * declare their signal with {@code flowable:scope="global"} (engine-wide broadcast). The open
 * question is whether a global-scope BPMN throw from tenant A delivers to a matching catch
 * subscription in tenant B.
 *
 * <p>The model below mirrors the examples exactly (separate catcher/thrower processes, a top-level
 * {@code <signal>} with {@code flowable:scope="global"}). Tests assert the SAFE expectation
 * (tenant isolation holds), so:
 * <ul>
 *   <li>green = Flowable scopes a tenant-deployed throw to the throwing tenant → F-EV-1 is NOT a leak;</li>
 *   <li>red on the cross-tenant assertion = the leak is real.</li>
 * </ul>
 *
 * <p>Standalone (no Spring / Postgres / Vault) because the behaviour under test is purely the
 * engine's signal-delivery + tenant-scoping contract. Mirrors {@link DmnDecisionTaskExecutionTest}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Signal tenant isolation — BPMN throw vs Java API (Flowable 7.2 contract)")
class SignalTenantIsolationTest {

    private static final String TENANT_A = "tenant-a";
    private static final String TENANT_B = "tenant-b";

    /**
     * One definitions doc holding a global-scope signal plus a catcher and a thrower process,
     * matching the shipped examples' shape (separate processes, top-level {@code <signal>} with
     * {@code flowable:scope="global"}). The catcher parks on a userTask after the catch so we can
     * observe whether it advanced.
     */
    private static final String SIGNAL_BPMN = """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                     xmlns:flowable="http://flowable.org/bpmn"
                     targetNamespace="http://werkflow.com/bpmn/test">
          <signal id="theSignal" name="testSignal" flowable:scope="global"/>
          <process id="catcher" name="Signal Catcher" isExecutable="true">
            <startEvent id="cstart"/>
            <sequenceFlow id="cf1" sourceRef="cstart" targetRef="catchSig"/>
            <intermediateCatchEvent id="catchSig">
              <signalEventDefinition signalRef="theSignal"/>
            </intermediateCatchEvent>
            <sequenceFlow id="cf2" sourceRef="catchSig" targetRef="park"/>
            <userTask id="park" name="Park"/>
            <sequenceFlow id="cf3" sourceRef="park" targetRef="cend"/>
            <endEvent id="cend"/>
          </process>
          <process id="thrower" name="Signal Thrower" isExecutable="true">
            <startEvent id="tstart"/>
            <sequenceFlow id="tf1" sourceRef="tstart" targetRef="throwSig"/>
            <intermediateThrowEvent id="throwSig">
              <signalEventDefinition signalRef="theSignal"/>
            </intermediateThrowEvent>
            <sequenceFlow id="tf2" sourceRef="throwSig" targetRef="tend"/>
            <endEvent id="tend"/>
          </process>
        </definitions>
        """;

    private ProcessEngine processEngine;
    private RepositoryService repositoryService;
    private RuntimeService runtimeService;
    private TaskService taskService;
    private final List<String> deploymentIds = new ArrayList<>();

    @BeforeAll
    void bootEngine() {
        StandaloneInMemProcessEngineConfiguration cfg = new StandaloneInMemProcessEngineConfiguration();
        cfg.setJdbcUrl("jdbc:h2:mem:signalTenantIsolation;DB_CLOSE_DELAY=1000");
        cfg.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
        processEngine = cfg.buildProcessEngine();
        repositoryService = processEngine.getRepositoryService();
        runtimeService = processEngine.getRuntimeService();
        taskService = processEngine.getTaskService();
    }

    @AfterAll
    void shutdown() {
        if (processEngine != null) {
            processEngine.close();
        }
    }

    @AfterEach
    void cleanup() {
        runtimeService.createProcessInstanceQuery().list()
                .forEach(pi -> runtimeService.deleteProcessInstance(pi.getId(), "test cleanup"));
        deploymentIds.forEach(id -> repositoryService.deleteDeployment(id, true));
        deploymentIds.clear();
    }

    private void deployForBothTenants() {
        deploymentIds.add(repositoryService.createDeployment()
                .addString("signals.bpmn20.xml", SIGNAL_BPMN).tenantId(TENANT_A).name("a").deploy().getId());
        deploymentIds.add(repositoryService.createDeployment()
                .addString("signals.bpmn20.xml", SIGNAL_BPMN).tenantId(TENANT_B).name("b").deploy().getId());
    }

    /** True when the catcher instance advanced past the signal catch to its park userTask. */
    private boolean caught(ProcessInstance catcher) {
        Task park = taskService.createTaskQuery()
                .processInstanceId(catcher.getId()).taskDefinitionKey("park").singleResult();
        return park != null;
    }

    @Test
    @DisplayName("BPMN intermediateThrowEvent (scope=global) from tenant A must NOT reach tenant B's catcher")
    void bpmnGlobalSignalThrow_doesNotCrossTenants() {
        deployForBothTenants();

        ProcessInstance catcherA = runtimeService.startProcessInstanceByKeyAndTenantId("catcher", TENANT_A);
        ProcessInstance catcherB = runtimeService.startProcessInstanceByKeyAndTenantId("catcher", TENANT_B);

        // Sanity: both parked at the signal catch, neither advanced yet.
        assertThat(caught(catcherA)).as("catcher A waiting before throw").isFalse();
        assertThat(caught(catcherB)).as("catcher B waiting before throw").isFalse();

        // Throw the signal via the BPMN throw event, in tenant A (the only signal path the platform uses).
        runtimeService.startProcessInstanceByKeyAndTenantId("thrower", TENANT_A);

        assertThat(caught(catcherA))
                .as("tenant A's catcher should receive the signal thrown within its own tenant")
                .isTrue();
        assertThat(caught(catcherB))
                .as("F-EV-1: tenant B's catcher MUST NOT receive a signal thrown by tenant A")
                .isFalse();
    }

    @Test
    @DisplayName("Java signalEventReceivedWithTenantId is tenant-scoped; bare signalEventReceived misses tenant subscriptions")
    void javaApi_tenantScopedVsBare() {
        deployForBothTenants();

        // --- tenant-scoped API (what TenantAwareSignalService wrapped) — isolates correctly ---
        ProcessInstance scopedA = runtimeService.startProcessInstanceByKeyAndTenantId("catcher", TENANT_A);
        ProcessInstance scopedB = runtimeService.startProcessInstanceByKeyAndTenantId("catcher", TENANT_B);
        runtimeService.signalEventReceivedWithTenantId("testSignal", TENANT_A);
        assertThat(caught(scopedA)).as("scoped signal reaches its own tenant").isTrue();
        assertThat(caught(scopedB)).as("scoped signal does NOT reach the other tenant").isFalse();

        cleanup();
        deployForBothTenants();

        // --- bare API (the one CLAUDE.md forbids) — empirically a NO-OP against tenant-scoped subs ---
        // Surprise finding: a no-tenant signalEventReceived matches NEITHER tenant's subscription
        // (it only matches no-tenant/default-tenant subscriptions). So against tenant-deployed
        // processes it is a silent no-op, NOT the "broadcasts to all tenants" the CLAUDE.md rule
        // asserts. The forbiddance is still correct (it doesn't deliver where you'd want), but its
        // stated rationale ("broadcasts to all tenants") is inaccurate for tenant-scoped deployments.
        ProcessInstance bareA = runtimeService.startProcessInstanceByKeyAndTenantId("catcher", TENANT_A);
        ProcessInstance bareB = runtimeService.startProcessInstanceByKeyAndTenantId("catcher", TENANT_B);
        runtimeService.signalEventReceived("testSignal");
        assertThat(caught(bareA)).as("bare signalEventReceived does NOT reach tenant A's scoped subscription").isFalse();
        assertThat(caught(bareB)).as("bare signalEventReceived does NOT reach tenant B's scoped subscription").isFalse();
    }

    /**
     * Confirms the tenant-scoped Java dispatch API ({@code signalEventReceivedWithTenantId}) is a
     * BROADCAST confined to the target tenant: many instances in tenant A all wake, while tenant B
     * is untouched. ("Broadcast to that tenant's instances only.") This is the behaviour the
     * (now-removed) {@code TenantAwareSignalService} wrapped — kept here as the engine-contract
     * characterization since the platform models signals in BPMN rather than dispatching from Java.
     */
    @Test
    @DisplayName("signalEventReceivedWithTenantId broadcasts within one tenant only (real BPMN catchers)")
    void tenantScopedJavaApi_broadcastsWithinTenantOnly() {
        deployForBothTenants();

        ProcessInstance a1 = runtimeService.startProcessInstanceByKeyAndTenantId("catcher", TENANT_A);
        ProcessInstance a2 = runtimeService.startProcessInstanceByKeyAndTenantId("catcher", TENANT_A);
        ProcessInstance b1 = runtimeService.startProcessInstanceByKeyAndTenantId("catcher", TENANT_B);

        runtimeService.signalEventReceivedWithTenantId("testSignal", TENANT_A);

        assertThat(caught(a1)).as("first tenant-A catcher wakes (signal is a broadcast within the tenant)").isTrue();
        assertThat(caught(a2)).as("second tenant-A catcher also wakes — broadcast reaches ALL matching subs").isTrue();
        assertThat(caught(b1)).as("tenant-B catcher is NOT reached — broadcast is confined to the tenant").isFalse();
    }
}
