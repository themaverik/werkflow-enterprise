package com.werkflow.engine.workflow;

import com.werkflow.engine.dto.FormSchema;
import com.werkflow.engine.exception.FormNotFoundException;
import com.werkflow.engine.service.FormSchemaService;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.StartEvent;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BpmnFormKeyValidatorTest {

    private static final String FIXTURE_RESOURCE = "test-formkey-fixture.bpmn20.xml";

    @Mock RepositoryService repositoryService;
    @Mock FormSchemaService formSchemaService;
    @InjectMocks BpmnFormKeyValidator validator;

    @BeforeEach
    void stubQuery() {
        ProcessDefinition def = stubDefinition();
        ProcessDefinitionQuery query = mock(ProcessDefinitionQuery.class);
        lenient().when(repositoryService.createProcessDefinitionQuery()).thenReturn(query);
        lenient().when(query.latestVersion()).thenReturn(query);
        lenient().when(query.list()).thenReturn(List.of(def));
    }

    @Test
    void passes_when_all_formKeys_resolve() {
        UserTask task = userTask("managerApproval", "Manager Review", "manager-approval-form");
        when(repositoryService.getBpmnModel("def-1")).thenReturn(modelWith(task));
        when(formSchemaService.loadFormSchemaByRef("manager-approval-form"))
            .thenReturn(FormSchema.builder().formKey("manager-approval-form").build());

        validator.validateDeployedBpmns();
    }

    @Test
    void passes_when_userTask_has_no_formKey() {
        UserTask task = userTask("review", "Review", null);
        when(repositoryService.getBpmnModel("def-1")).thenReturn(modelWith(task));

        validator.validateDeployedBpmns();
    }

    @Test
    void skips_dynamic_expression_formKeys() {
        UserTask task = userTask("dynamicForm", "Dynamic", "${formKeyVar}");
        when(repositoryService.getBpmnModel("def-1")).thenReturn(modelWith(task));

        validator.validateDeployedBpmns();
    }

    @Test
    void fails_when_formKey_is_missing_from_formSchemas() {
        UserTask task = userTask("confirm", "Confirm Step", "missing_form_key");
        when(repositoryService.getBpmnModel("def-1")).thenReturn(modelWith(task));
        when(formSchemaService.loadFormSchemaByRef("missing_form_key"))
            .thenThrow(new FormNotFoundException("missing_form_key"));

        assertThatThrownBy(() -> validator.validateDeployedBpmns())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("BpmnFormKeyValidator: 1 missing formKey reference(s)")
            .hasMessageContaining("missing_form_key")
            .hasMessageContaining("task 'Confirm Step'");
    }

    @Test
    void collects_multiple_violations_and_reports_all() {
        UserTask t1 = userTask("a", "Task A", "missing-form-a");
        UserTask t2 = userTask("b", "Task B", "missing-form-b");
        UserTask t3 = userTask("c", "Task C", "good-form");
        when(repositoryService.getBpmnModel("def-1")).thenReturn(modelWith(t1, t2, t3));
        when(formSchemaService.loadFormSchemaByRef("missing-form-a"))
            .thenThrow(new FormNotFoundException("missing-form-a"));
        when(formSchemaService.loadFormSchemaByRef("missing-form-b"))
            .thenThrow(new FormNotFoundException("missing-form-b"));
        when(formSchemaService.loadFormSchemaByRef("good-form"))
            .thenReturn(FormSchema.builder().formKey("good-form").build());

        assertThatThrownBy(() -> validator.validateDeployedBpmns())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("2 missing formKey reference(s)")
            .hasMessageContaining("missing-form-a")
            .hasMessageContaining("missing-form-b");
    }

    @Test
    void fails_when_startEvent_formKey_is_missing() {
        StartEvent start = new StartEvent();
        start.setId("start");
        start.setName("Request Submitted");
        start.setFormKey("missing-start-form");
        BpmnModel model = new BpmnModel();
        Process process = new Process();
        process.setId("test-process");
        process.addFlowElement(start);
        model.addProcess(process);
        when(repositoryService.getBpmnModel("def-1")).thenReturn(model);
        when(formSchemaService.loadFormSchemaByRef("missing-start-form"))
            .thenThrow(new FormNotFoundException("missing-start-form"));

        assertThatThrownBy(() -> validator.validateDeployedBpmns())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("start event 'Request Submitted'")
            .hasMessageContaining("missing-start-form");
    }

    @Test
    void passes_when_no_definitions_deployed() {
        ProcessDefinitionQuery query = mock(ProcessDefinitionQuery.class);
        when(repositoryService.createProcessDefinitionQuery()).thenReturn(query);
        when(query.latestVersion()).thenReturn(query);
        when(query.list()).thenReturn(List.of());

        validator.validateDeployedBpmns();
    }

    private ProcessDefinition stubDefinition() {
        ProcessDefinition def = mock(ProcessDefinition.class);
        lenient().when(def.getId()).thenReturn("def-1");
        lenient().when(def.getKey()).thenReturn("test-process");
        lenient().when(def.getName()).thenReturn("Test Process");
        lenient().when(def.getResourceName()).thenReturn(FIXTURE_RESOURCE);
        return def;
    }

    private UserTask userTask(String id, String name, String formKey) {
        UserTask task = new UserTask();
        task.setId(id);
        task.setName(name);
        if (formKey != null) {
            task.setFormKey(formKey);
        }
        return task;
    }

    private BpmnModel modelWith(UserTask... tasks) {
        BpmnModel model = new BpmnModel();
        Process process = new Process();
        process.setId("test-process");
        for (UserTask task : tasks) {
            process.addFlowElement(task);
        }
        model.addProcess(process);
        return model;
    }

    static {
        // The validator's ClassPathResource("processes/<filename>") check requires the
        // fixture file to exist on the test classpath; otherwise every definition is
        // skipped as "stale" and assertions silently pass without exercising the code.
        if (BpmnFormKeyValidatorTest.class.getResource("/processes/" + FIXTURE_RESOURCE) == null) {
            throw new IllegalStateException(
                "Test fixture missing: src/test/resources/processes/" + FIXTURE_RESOURCE);
        }
    }
}
