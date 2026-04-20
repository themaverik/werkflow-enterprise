package com.werkflow.engine.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.flowable.engine.RepositoryService;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ProcessDefinitionServiceJuelValidationTest {

    @Mock RepositoryService repositoryService;
    @Mock FormSchemaService formSchemaService;

    @InjectMocks ProcessDefinitionService service;

    @Test
    void deploy_rejectsJuelKeywordGetClass() {
        String bpmn = "<bpmn><expression>${someBean.getClass().forName('evil')}</expression></bpmn>";
        assertThatThrownBy(() -> service.deployProcessDefinition(bpmn, "test.bpmn"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("getClass");
    }

    @Test
    void deploy_rejectsRuntimeKeyword() {
        String bpmn = "<bpmn><expression>${Runtime.getRuntime().exec('ls')}</expression></bpmn>";
        assertThatThrownBy(() -> service.deployProcessDefinition(bpmn, "test.bpmn"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deploy_acceptsNormalExpression() {
        String bpmn = "<bpmn><expression>${employee.email}</expression></bpmn>";
        // validation passes — Flowable deploy will fail due to mock but that's fine
        // the important thing is it does NOT throw IllegalArgumentException
        assertThatThrownBy(() -> service.deployProcessDefinition(bpmn, "test.bpmn"))
            .isNotInstanceOf(IllegalArgumentException.class);
    }
}
