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
@DisplayName("Sample service-task fragment — harness core smoke test (ADR-028 Phase 1)")
class SampleFragmentTest extends WerkflowProcessTest {

    private JavaDelegate sampleDelegate;

    @BeforeAll
    void setup() throws Exception {
        sampleDelegate = mock(JavaDelegate.class);
        doAnswer(inv -> {
            DelegateExecution execution = inv.getArgument(0);
            execution.setVariable("outputVar", "processed");
            return null;
        }).when(sampleDelegate).execute(any(DelegateExecution.class));

        startEngine("sampleFragment", Map.of("sampleDelegate", sampleDelegate));
        dsl.deploy("fragments/sample-service-task.bpmn20.xml");
    }

    @Test
    @DisplayName("delegate invoked once, output variable set, flow completes")
    void sampleServiceTask_invokesDelegate_andSetsOutputVariable() {
        dsl.start("sample-service-task", Map.of("inputVar", "test"))
           .assertCompleted()
           .assertHistoricVariable("outputVar", "processed");

        verify(sampleDelegate, times(1)).execute(any(DelegateExecution.class));
    }
}
