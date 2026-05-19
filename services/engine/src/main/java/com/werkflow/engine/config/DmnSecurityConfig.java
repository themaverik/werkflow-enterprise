package com.werkflow.engine.config;

import com.werkflow.engine.security.el.DmnModeCommandInterceptor;
import org.flowable.dmn.spring.SpringDmnEngineConfiguration;
import org.flowable.spring.boot.EngineConfigurationConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * DMN engine security configuration.
 *
 * <p>Registers {@link DmnModeCommandInterceptor} as a custom pre-command interceptor on
 * the DMN engine (audit doc {@code EL-Expression-Security.md §4.5}, item P1-7). The
 * interceptor wraps every DMN command execution with the {@link
 * com.werkflow.engine.security.el.SecurityELResolver} DMN pass-through flag so FEEL
 * expressions are not blocked by the BPMN-oriented EL guard during DMN decision evaluation.
 *
 * <p>Uses the symmetric {@link EngineConfigurationConfigurer}{@code <SpringDmnEngineConfiguration>}
 * hook, picked up by Flowable's {@code DmnEngineAutoConfiguration$DmnEngineProcessConfiguration}
 * (which extends {@code BaseEngineConfigurationWithConfigurers<SpringDmnEngineConfiguration>}).
 * This is the same mechanism {@link FlowableConfig} uses for the process engine.
 *
 * <p>Kept as a separate {@code @Configuration} class to match the project's many-small-files
 * convention and avoid bloating {@link FlowableConfig}.
 */
@Configuration
public class DmnSecurityConfig {

    /**
     * Configurer that installs {@link DmnModeCommandInterceptor} at the head of the DMN
     * engine command chain.
     *
     * @return the configurer bean
     */
    @Bean
    public EngineConfigurationConfigurer<SpringDmnEngineConfiguration> dmnSecurityConfigurer() {
        return dmnEngineConfiguration ->
            dmnEngineConfiguration.addCustomPreCommandInterceptor(new DmnModeCommandInterceptor());
    }
}
