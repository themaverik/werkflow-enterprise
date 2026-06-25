package com.werkflow.engine.config.flowable;

import com.werkflow.engine.testsupport.WerkflowTestProcessEngine;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test proving the full XML-parse → delegate-wiring chain:
 *
 * <pre>
 *   XML bytes → BpmnXMLConverter → WerkflowSendTaskXMLConverter
 *             → WerkflowSendTaskParseHandler → delegate resolution at runtime
 * </pre>
 *
 * <p>This test deploys the real {@code it-helpdesk-ticket.bpmn20.xml} classpath resource
 * (which has two sendTasks with {@code flowable:delegateExpression="${notificationDelegate}"})
 * and starts the process. Only the first sendTask ({@code acknowledgeTicket}) fires on
 * process start — the second ({@code notifyResolution}) is behind the {@code resolveTicket}
 * userTask wait state. The counting stub is therefore expected to be invoked exactly once.
 *
 * <p>This test would fail WITHOUT {@link WerkflowSendTaskXMLConverter}: the stock
 * {@code SendTaskXMLConverter} drops {@code flowable:delegateExpression} silently at parse
 * time (it does not call {@code BpmnXMLUtil.addCustomAttributes}), so the parse handler
 * cannot wire the delegate and the process would either error or leave the sendTask
 * as a no-op.
 *
 * @see WerkflowSendTaskXMLConverter
 * @see WerkflowSendTaskParseHandler
 */
class WerkflowSendTaskXmlConverterTest {

    private static WerkflowTestProcessEngine testEngine;
    private static final AtomicInteger NOTIFICATION_COUNT = new AtomicInteger(0);

    @BeforeAll
    static void buildEngine() {
        // Counting stub — increments each time notificationDelegate.execute() is called.
        JavaDelegate countingStub = (DelegateExecution execution) -> NOTIFICATION_COUNT.incrementAndGet();

        testEngine = WerkflowTestProcessEngine.build(
                "sendTaskXmlConverterTest",
                Map.of("notificationDelegate", countingStub)
        );
    }

    @AfterAll
    static void closeEngine() {
        if (testEngine != null) {
            testEngine.close();
        }
    }

    @Test
    void xmlParsedSendTask_invokesDelegate_exactlyOnceOnProcessStart() {
        RepositoryService repositoryService = testEngine.getProcessEngine().getRepositoryService();
        RuntimeService runtimeService = testEngine.getProcessEngine().getRuntimeService();

        // Deploy the real BPMN from classpath — this is the path that exercises
        // WerkflowSendTaskXMLConverter (XML bytes parsed via BpmnXMLConverter).
        repositoryService.createDeployment()
                .addClasspathResource(
                        "examples/tenants/default/bpmn/it-helpdesk-ticket.bpmn20.xml")
                .deploy();

        NOTIFICATION_COUNT.set(0);

        // Start the process — acknowledgeTicket sendTask fires immediately on start.
        // notifyResolution is behind the resolveTicket userTask wait state and does NOT fire.
        runtimeService.startProcessInstanceByKey(
                "it-helpdesk-ticket",
                Map.of("requesterEmail", "test@example.com")
        );

        assertThat(NOTIFICATION_COUNT.get())
                .as("acknowledgeTicket sendTask should invoke notificationDelegate exactly once on start")
                .isEqualTo(1);
    }
}
