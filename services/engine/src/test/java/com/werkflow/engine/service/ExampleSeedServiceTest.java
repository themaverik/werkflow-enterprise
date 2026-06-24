package com.werkflow.engine.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.werkflow.engine.dto.FormSchema;
import com.werkflow.engine.dto.SeedResult;
import com.werkflow.engine.dto.WorkflowSeedResult;
import org.flowable.dmn.api.DmnDeploymentQuery;
import org.flowable.dmn.api.DmnRepositoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ExampleSeedService}.
 *
 * <p>Covers:
 * <ul>
 *   <li>DOM extraction: form keys, decision keys, DMN decision IDs</li>
 *   <li>Idempotency: BPMN already deployed → SKIPPED result</li>
 *   <li>Happy path: not deployed → forms seeded, DMNs deployed, BPMN deployed → DEPLOYED result</li>
 *   <li>Form idempotency: form already exists → not re-saved</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ExampleSeedService — unit")
class ExampleSeedServiceTest {

    @Mock FormSchemaService formSchemaService;
    @Mock ProcessDefinitionService processDefinitionService;
    @Mock DmnRepositoryService dmnRepositoryService;
    @Mock RepositoryService repositoryService;
    @Mock ResourcePatternResolver resourcePatternResolver;

    private ExampleSeedService service;

    private static final String CAPEX_BPMN = """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                     xmlns:flowable="http://flowable.org/bpmn"
                     targetNamespace="http://werkflow.com/bpmn">
          <process id="capex-approval-process" isExecutable="true">
            <startEvent id="start" flowable:formKey="capex-request-form"/>
            <serviceTask id="dmn1" flowable:type="dmn">
              <extensionElements>
                <flowable:field name="decisionTableReferenceKey">
                  <flowable:string>capex_manager_group</flowable:string>
                </flowable:field>
              </extensionElements>
            </serviceTask>
            <userTask id="approval" flowable:actionType="HUMAN_APPROVAL"
                      flowable:formKey="capex-approval-form"/>
          </process>
        </definitions>
        """;

    private static final String LEAVE_DMN = """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/"
                     namespace="http://werkflow.com/dmn">
          <decision id="leave_approval" name="Leave Approval Rules">
            <decisionTable id="leave_table" hitPolicy="FIRST">
              <output id="out_required" name="approvalRequired" typeRef="boolean"/>
            </decisionTable>
          </decision>
        </definitions>
        """;

    @BeforeEach
    void setUp() {
        service = new ExampleSeedService(
                formSchemaService,
                processDefinitionService,
                dmnRepositoryService,
                repositoryService,
                resourcePatternResolver,
                new ObjectMapper()
        );
    }

    // -------------------------------------------------------------------------
    // DOM extraction tests
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("extractFormRefs")
    class ExtractFormRefs {

        @Test
        @DisplayName("start event formKey → TASK_FORM")
        void startEvent_returnsTaskForm() {
            Document doc = parse(CAPEX_BPMN);
            Map<String, FormSchema.FormType> refs = service.extractFormRefs(doc);
            assertThat(refs).containsEntry("capex-request-form", FormSchema.FormType.TASK_FORM);
        }

        @Test
        @DisplayName("HUMAN_APPROVAL userTask formKey → APPROVAL")
        void humanApprovalTask_returnsApproval() {
            Document doc = parse(CAPEX_BPMN);
            Map<String, FormSchema.FormType> refs = service.extractFormRefs(doc);
            assertThat(refs).containsEntry("capex-approval-form", FormSchema.FormType.APPROVAL);
        }

        @Test
        @DisplayName("no formKeys → empty map")
        void noFormKeys_returnsEmptyMap() {
            String bpmn = """
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:flowable="http://flowable.org/bpmn"
                             targetNamespace="test">
                  <process id="p" isExecutable="true">
                    <startEvent id="s"/>
                  </process>
                </definitions>""";
            assertThat(service.extractFormRefs(parse(bpmn))).isEmpty();
        }
    }

    @Nested
    @DisplayName("extractDecisionKeys")
    class ExtractDecisionKeys {

        @Test
        @DisplayName("DMN service task extracts decisionTableReferenceKey")
        void dmnServiceTask_extractsKey() {
            Document doc = parse(CAPEX_BPMN);
            Set<String> keys = service.extractDecisionKeys(doc);
            assertThat(keys).containsExactly("capex_manager_group");
        }

        @Test
        @DisplayName("no DMN tasks → empty set")
        void noDmnTasks_returnsEmpty() {
            String bpmn = """
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:flowable="http://flowable.org/bpmn"
                             targetNamespace="test">
                  <process id="p" isExecutable="true"><startEvent id="s"/></process>
                </definitions>""";
            assertThat(service.extractDecisionKeys(parse(bpmn))).isEmpty();
        }
    }

    @Nested
    @DisplayName("extractDmnDecisionIds")
    class ExtractDmnDecisionIds {

        @Test
        @DisplayName("DMN file with one decision → returns its ID")
        void singleDecision_returnsId() {
            assertThat(service.extractDmnDecisionIds(LEAVE_DMN))
                    .containsExactly("leave_approval");
        }

        @Test
        @DisplayName("malformed DMN → returns empty set without exception")
        void malformedDmn_returnsEmpty() {
            assertThat(service.extractDmnDecisionIds("not-xml")).isEmpty();
        }
    }

    // -------------------------------------------------------------------------
    // Input validation
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("seedForTenant: blank tenantId → IllegalArgumentException")
    void blankTenantId_throws() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.seedForTenant(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
    }

    @Test
    @DisplayName("seedForTenant: path-traversal tenantId → IllegalArgumentException")
    void pathTraversalTenantId_throws() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.seedForTenant("../etc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid characters");
    }

    // -------------------------------------------------------------------------
    // Idempotency: BPMN already deployed → SKIPPED
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("seedForTenant: BPMN already deployed → SKIPPED, no form/DMN calls")
    void bpmnAlreadyDeployed_skips() throws Exception {
        ProcessDefinitionQuery pdq = mockProcessDefinitionQuery(1L);
        when(repositoryService.createProcessDefinitionQuery()).thenReturn(pdq);

        Resource bpmnResource = namedResource("capex-approval-process.bpmn20.xml", CAPEX_BPMN);
        mockBpmnFolder(bpmnResource);

        SeedResult result = service.seedForTenant("acme");

        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.deployed()).isEqualTo(0);
        assertThat(result.failed()).isEqualTo(0);
        assertThat(result.workflows()).hasSize(1);
        assertThat(result.workflows().get(0).status()).isEqualTo("SKIPPED");

        verify(formSchemaService, never()).saveFormSchema(anyString(), any(), anyString(), any(), anyString(), anyString());
        verify(processDefinitionService, never()).deployExampleProcessDefinition(anyString(), anyString(), anyString());
    }

    // -------------------------------------------------------------------------
    // Happy path: deploy from scratch
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("seedForTenant: new tenant → forms saved, DMNs deployed, BPMN deployed → DEPLOYED")
    void freshTenant_deploysAll() throws Exception {
        // BPMN query → 0 (not yet deployed)
        ProcessDefinitionQuery pdq = mockProcessDefinitionQuery(0L);
        when(repositoryService.createProcessDefinitionQuery()).thenReturn(pdq);

        // Forms don't exist yet for this tenant
        when(formSchemaService.formExistsAnyVersion("capex-request-form", "acme")).thenReturn(false);
        when(formSchemaService.formExistsAnyVersion("capex-approval-form", "acme")).thenReturn(false);

        // No prior DMN deployments
        DmnDeploymentQuery ddq = mockDmnDeploymentQuery(0L);
        when(dmnRepositoryService.createDeploymentQuery()).thenReturn(ddq);
        when(dmnRepositoryService.createDeployment()).thenReturn(
                mock(org.flowable.dmn.api.DmnDeploymentBuilder.class,
                        org.mockito.Answers.RETURNS_DEEP_STUBS));

        // Resources: one BPMN, one DMN, two form JSONs
        Resource bpmnResource = namedResource("capex-approval-process.bpmn20.xml", CAPEX_BPMN);
        mockBpmnFolder(bpmnResource);

        String capexDmn = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/"
                         namespace="http://werkflow.com/dmn">
              <decision id="capex_manager_group" name="Manager Group"/>
            </definitions>""";
        Resource dmnResource = namedResource("capex-approver-resolution.dmn", capexDmn);
        when(resourcePatternResolver.getResources("classpath:examples/tenants/default/dmn/*.dmn"))
                .thenReturn(new Resource[]{dmnResource});

        String formJson = """
            {"type":"default","components":[{"type":"select","key":"decision","label":"Decision"}]}""";
        when(resourcePatternResolver.getResources(
                "classpath:examples/tenants/default/forms/capex-request-form.json"))
                .thenReturn(new Resource[]{namedResource("capex-request-form.json", formJson)});
        when(resourcePatternResolver.getResources(
                "classpath:examples/tenants/default/forms/capex-approval-form.json"))
                .thenReturn(new Resource[]{namedResource("capex-approval-form.json", formJson)});

        when(processDefinitionService.deployExampleProcessDefinition(anyString(), anyString(), anyString()))
                .thenReturn(mock(com.werkflow.engine.dto.ProcessDefinitionResponse.class));

        SeedResult result = service.seedForTenant("acme");

        assertThat(result.deployed()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(0);
        assertThat(result.failed()).isEqualTo(0);

        WorkflowSeedResult wf = result.workflows().get(0);
        assertThat(wf.status()).isEqualTo("DEPLOYED");
        assertThat(wf.newForms()).containsExactlyInAnyOrder("capex-request-form", "capex-approval-form");
        assertThat(wf.newDmns()).containsExactly("capex-approver-resolution.dmn");
        assertThat(wf.bpmnDeployed()).isTrue();
    }

    // -------------------------------------------------------------------------
    // Form idempotency: form already exists → not re-saved
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("seedForms: form already exists → saveFormSchema not called for that key")
    void formAlreadyExists_notReSaved() throws Exception {
        ProcessDefinitionQuery pdq = mockProcessDefinitionQuery(0L);
        when(repositoryService.createProcessDefinitionQuery()).thenReturn(pdq);

        // capex-request-form already exists for this tenant; capex-approval-form does not
        when(formSchemaService.formExistsAnyVersion("capex-request-form", "acme")).thenReturn(true);
        when(formSchemaService.formExistsAnyVersion("capex-approval-form", "acme")).thenReturn(false);

        DmnDeploymentQuery ddq = mockDmnDeploymentQuery(0L);
        when(dmnRepositoryService.createDeploymentQuery()).thenReturn(ddq);
        when(dmnRepositoryService.createDeployment()).thenReturn(
                mock(org.flowable.dmn.api.DmnDeploymentBuilder.class,
                        org.mockito.Answers.RETURNS_DEEP_STUBS));

        Resource bpmnResource = namedResource("capex-approval-process.bpmn20.xml", CAPEX_BPMN);
        mockBpmnFolder(bpmnResource);

        String capexDmn = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/"
                         namespace="http://werkflow.com/dmn">
              <decision id="capex_manager_group" name="Manager Group"/>
            </definitions>""";
        when(resourcePatternResolver.getResources("classpath:examples/tenants/default/dmn/*.dmn"))
                .thenReturn(new Resource[]{namedResource("capex-approver-resolution.dmn", capexDmn)});

        String formJson = """
            {"type":"default","components":[{"type":"select","key":"decision","label":"Decision"}]}""";
        when(resourcePatternResolver.getResources(
                "classpath:examples/tenants/default/forms/capex-approval-form.json"))
                .thenReturn(new Resource[]{namedResource("capex-approval-form.json", formJson)});
        when(processDefinitionService.deployExampleProcessDefinition(anyString(), anyString(), anyString()))
                .thenReturn(mock(com.werkflow.engine.dto.ProcessDefinitionResponse.class));

        service.seedForTenant("acme");

        // Only capex-approval-form should have been saved (request-form already existed)
        verify(formSchemaService).saveFormSchema(
                eq("capex-approval-form"), any(), anyString(), any(), anyString(), anyString());
        verify(formSchemaService, never()).saveFormSchema(
                eq("capex-request-form"), any(), anyString(), any(), anyString(), anyString());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private ProcessDefinitionQuery mockProcessDefinitionQuery(long count) {
        ProcessDefinitionQuery query = org.mockito.Mockito.mock(ProcessDefinitionQuery.class,
                org.mockito.Answers.RETURNS_SELF);
        when(query.count()).thenReturn(count);
        return query;
    }

    private DmnDeploymentQuery mockDmnDeploymentQuery(long count) {
        DmnDeploymentQuery query = org.mockito.Mockito.mock(DmnDeploymentQuery.class,
                org.mockito.Answers.RETURNS_SELF);
        when(query.count()).thenReturn(count);
        return query;
    }

    private Resource namedResource(String filename, String content) {
        return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)) {
            @Override public String getFilename() { return filename; }
        };
    }

    private void mockBpmnFolder(Resource... resources) throws Exception {
        when(resourcePatternResolver.getResources(
                "classpath:examples/tenants/acme/bpmn/*.bpmn20.xml"))
                .thenReturn(new Resource[0]);
        when(resourcePatternResolver.getResources(
                "classpath:examples/tenants/default/bpmn/*.bpmn20.xml"))
                .thenReturn(resources);
    }

    private Document parse(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
