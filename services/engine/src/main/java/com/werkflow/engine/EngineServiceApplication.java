package com.werkflow.engine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.werkflow.engine.workflow.FlowableGroupProperties;

/**
 * Werkflow Engine Service Application
 *
 * Central Flowable BPM engine for the werkflow enterprise platform.
 * Provides workflow orchestration for all departments.
 *
 * Responsibilities:
 * - Process definition management (deploy, version control)
 * - Process instance execution
 * - Task management and assignment
 * - Process variable management
 * - Event handling and messaging
 * - Workflow monitoring and history
 */
@SpringBootApplication(scanBasePackages = {"com.werkflow.engine", "com.werkflow.common"})
@EnableConfigurationProperties(FlowableGroupProperties.class)
public class EngineServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(EngineServiceApplication.class, args);
    }
}
