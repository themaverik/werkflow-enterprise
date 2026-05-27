package com.werkflow.engine.config;

import com.werkflow.engine.config.flowable.WerkflowBusinessRuleTaskValidator;
import com.werkflow.engine.config.flowable.WerkflowManualTaskValidator;
import com.werkflow.engine.config.flowable.WerkflowScriptTaskQuarantineValidator;
import com.werkflow.engine.config.flowable.WerkflowSendTaskParseHandler;
import com.werkflow.engine.config.flowable.WerkflowSendTaskValidator;
import com.werkflow.engine.listener.GlobalTaskNotificationListener;
import com.werkflow.engine.security.el.ExpressionAuditLogger;
import com.werkflow.engine.security.el.ExpressionLimitsConfig;
import com.werkflow.engine.security.el.RestrictedExpressionManager;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.common.engine.api.delegate.event.FlowableEventListener;
import org.flowable.common.engine.impl.cfg.SpringBeanFactoryProxyMap;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.spring.boot.EngineConfigurationConfigurer;
import org.flowable.validation.ProcessValidatorFactory;
import org.flowable.validation.ProcessValidatorImpl;
import org.flowable.validation.validator.impl.SendTaskValidator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

/**
 * Flowable Engine Configuration
 *
 * Customizes the Flowable process engine for the Werkflow platform.
 *
 * <p>Key settings:
 * <ul>
 *   <li>{@code setCreateDiagramOnDeploy(false)} — prevents NPE from
 *       {@code DefaultProcessDiagramGenerator} when BPMN files lack graphic info
 *       (i.e., no {@code bpmndi:BPMNDiagram} section with bounds/coordinates).</li>
 *   <li>{@code setEnableSafeBpmnXml(true)} — enables detailed validation to catch
 *       BPMN issues early during deployment.</li>
 *   <li>{@code setTypedEventListeners} — registers {@link GlobalTaskNotificationListener}
 *       globally so every task assignment and completion fires an email automatically,
 *       eliminating the need for explicit NOTIFICATION service task nodes in BPMN.</li>
 *   <li>{@code setExpressionManager} — installs {@link RestrictedExpressionManager} to
 *       enforce parse-time hard limits (length, depth, function-call count) and audit
 *       denied expressions. Per EL-Expression-Security audit D-EL-8. The manager
 *       receives the {@link SpringBeanFactoryProxyMap} so that {@code ${beanName}}
 *       delegate-expression lookups continue to resolve Spring beans correctly —
 *       Flowable's {@code initExpressionManager()} does not inject the beans map into
 *       a user-supplied manager.</li>
 * </ul>
 */
@Configuration
@EnableConfigurationProperties(ExpressionLimitsConfig.class)
public class FlowableConfig {

    @Bean
    public EngineConfigurationConfigurer<SpringProcessEngineConfiguration> processEngineConfigurer(
        GlobalTaskNotificationListener globalTaskNotificationListener,
        ExpressionLimitsConfig expressionLimitsConfig,
        ExpressionAuditLogger expressionAuditLogger,
        ApplicationContext applicationContext
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

            // Install hardened EL expression manager (EL-Expression-Security audit, task C).
            // SpringBeanFactoryProxyMap mirrors what SpringProcessEngineConfiguration.initBeans()
            // would install, preserving ${delegateBean} resolution for all service tasks.
            Map<Object, Object> beans = new SpringBeanFactoryProxyMap(applicationContext);
            engineConfiguration.setExpressionManager(
                new RestrictedExpressionManager(expressionLimitsConfig, expressionAuditLogger, beans)
            );

            // Register custom SendTask parse handler so flowable:delegateExpression / class /
            // expression bindings on <bpmn:sendTask> are wired correctly.
            // ADR-015 / Send-Task.md §6 D-ST-1.
            engineConfiguration.setCustomDefaultBpmnParseHandlers(
                List.of(new WerkflowSendTaskParseHandler())
            );

            // Replace the default SendTaskValidator with our extended version that also
            // accepts class / delegateExpression / expression as valid implementation types.
            ProcessValidatorImpl processValidator = (ProcessValidatorImpl)
                new ProcessValidatorFactory().createDefaultProcessValidator();
            processValidator.getValidatorSets().forEach(set -> {
                set.removeValidator(SendTaskValidator.class);
                set.addValidator(new WerkflowSendTaskValidator());
                set.addValidator(new WerkflowScriptTaskQuarantineValidator());
                set.addValidator(new WerkflowBusinessRuleTaskValidator());
                set.addValidator(new WerkflowManualTaskValidator());
            });
            engineConfiguration.setProcessValidator(processValidator);
        };
    }
}
