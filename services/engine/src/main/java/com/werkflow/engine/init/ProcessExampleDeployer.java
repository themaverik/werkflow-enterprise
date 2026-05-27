package com.werkflow.engine.init;

import com.werkflow.engine.service.ProcessDefinitionService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Deploys example BPMN processes from classpath:processes/examples/ on startup.
 * Controlled by the werkflow.examples.deploy-on-startup flag (default: false).
 */
@Slf4j
@Component
public class ProcessExampleDeployer {

    private final ProcessDefinitionService processDefinitionService;
    private final ResourcePatternResolver resourcePatternResolver;
    private final boolean deployOnStartup;

    public ProcessExampleDeployer(ProcessDefinitionService processDefinitionService,
                                   ResourcePatternResolver resourcePatternResolver,
                                   @Value("${werkflow.examples.deploy-on-startup:false}") boolean deployOnStartup) {
        this.processDefinitionService = processDefinitionService;
        this.resourcePatternResolver = resourcePatternResolver;
        this.deployOnStartup = deployOnStartup;
    }

    @PostConstruct
    public void deploy() throws IOException {
        if (!deployOnStartup) {
            log.info("Example process deployment skipped (werkflow.examples.deploy-on-startup=false)");
            return;
        }

        Resource[] resources = resourcePatternResolver.getResources("classpath:processes/examples/*.bpmn20.xml");
        if (resources.length == 0) {
            log.info("No example BPMN files found in classpath:processes/examples/");
            return;
        }

        for (Resource resource : resources) {
            String filename = resource.getFilename();
            try {
                String bpmnXml = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                processDefinitionService.deployExampleProcessDefinition(bpmnXml, filename);
                log.info("Deployed example process: {}", filename);
            } catch (Exception e) {
                log.error("Failed to deploy example process '{}': {}", filename, e.getMessage());
            }
        }
    }
}
