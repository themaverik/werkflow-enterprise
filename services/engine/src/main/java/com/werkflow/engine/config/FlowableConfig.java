package com.werkflow.engine.config;

import com.werkflow.engine.listener.GlobalTaskNotificationListener;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.common.engine.api.delegate.event.FlowableEventListener;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.spring.boot.EngineConfigurationConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

@Configuration
public class FlowableConfig {

    @Bean
    public EngineConfigurationConfigurer<SpringProcessEngineConfiguration> processEngineConfigurer(
        GlobalTaskNotificationListener globalTaskNotificationListener
    ) {
        return engineConfiguration -> {
            engineConfiguration.setCreateDiagramOnDeploy(false);
            engineConfiguration.setEnableSafeBpmnXml(true);
            engineConfiguration.setActivityFontName("Arial");
            engineConfiguration.setLabelFontName("Arial");
            engineConfiguration.setAnnotationFontName("Arial");

            // Register global task notification listener for automatic email dispatch.
            // Eliminates the need for explicit NOTIFICATION service task nodes in BPMN.
            Map<String, List<FlowableEventListener>> typedListeners = Map.of(
                FlowableEngineEventType.TASK_ASSIGNED.name(), List.of(globalTaskNotificationListener),
                FlowableEngineEventType.TASK_COMPLETED.name(), List.of(globalTaskNotificationListener)
            );
            engineConfiguration.setTypedEventListeners(typedListeners);
        };
    }
}
