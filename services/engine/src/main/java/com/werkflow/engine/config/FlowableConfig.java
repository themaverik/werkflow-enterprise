package com.werkflow.engine.config;

import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.spring.boot.EngineConfigurationConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Flowable Engine Configuration
 *
 * Customizes the Flowable process engine behavior for the Werkflow platform.
 * This configuration addresses specific requirements and issues related to
 * process deployment and execution.
 */
@Configuration
public class FlowableConfig {

    /**
     * Configures the Flowable process engine to prevent diagram generation errors
     * during deployment of BPMN files that lack graphic information.
     *
     * Background:
     * When BPMN files are created programmatically or lack the bpmndi:BPMNDiagram
     * section (which contains graphic information like bounds and coordinates),
     * Flowable's DefaultProcessDiagramGenerator throws a NullPointerException when
     * trying to access GraphicInfo objects that don't exist.
     *
     * Solution:
     * This configuration disables automatic diagram generation during deployment.
     * Process definitions will still deploy successfully, and diagrams can be
     * generated on-demand later if proper graphic information is added to the BPMN files.
     *
     * @return EngineConfigurationConfigurer that customizes the process engine
     */
    @Bean
    public EngineConfigurationConfigurer<SpringProcessEngineConfiguration> processEngineConfigurer() {
        return engineConfiguration -> {
            // Disable automatic diagram generation during deployment
            // This prevents NullPointerException when BPMN files lack graphic information
            engineConfiguration.setCreateDiagramOnDeploy(false);

            // Enable detailed validation to catch other BPMN issues early
            engineConfiguration.setEnableSafeBpmnXml(true);

            // Set activity font name to prevent rendering issues if diagrams are generated later
            engineConfiguration.setActivityFontName("Arial");
            engineConfiguration.setLabelFontName("Arial");
            engineConfiguration.setAnnotationFontName("Arial");

        };
    }
}
