package com.werkflow.engine.process;

import com.werkflow.engine.testsupport.WerkflowProcessTest;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Tag("fragment")
@DisplayName("Connector service task fragment — BPMN↔delegate seam (ADR-028 Phase 3)")
class ConnectorFragmentTest extends WerkflowProcessTest {

    private JavaDelegate externalApiCallDelegate;

    @BeforeAll
    void setup() throws Exception {
        externalApiCallDelegate = mock(JavaDelegate.class);
        doAnswer(inv -> {
            DelegateExecution execution = inv.getArgument(0);
            execution.setVariable("connectorResult", "ok");
            return null;
        }).when(externalApiCallDelegate).execute(any(DelegateExecution.class));

        startEngine("connectorFragment", Map.of("externalApiCallDelegate", externalApiCallDelegate));
        dsl.deploy("fragments/connector-service-task.bpmn20.xml");
    }

    @Test
    @DisplayName("connector delegate invoked once, output variable set, flow completes")
    void connectorServiceTask_invokesDelegate_andSetsOutputVariable() {
        dsl.start("connector-service-task", Map.of("inputPayload", "{}"))
           .assertCompleted()
           .assertHistoricVariable("connectorResult", "ok");

        verify(externalApiCallDelegate, times(1)).execute(any(DelegateExecution.class));
    }
}
