package com.werkflow.engine.process;

import com.werkflow.engine.testsupport.WerkflowTestProcessEngine;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.Gateway;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.ProcessDefinition;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins shut a latent defect in the procurement seed: its exclusive gateways must declare the
 * default sequence flow with the BPMN-standard, <em>unprefixed</em> {@code default} attribute.
 *
 * <p>Flowable 7.2's {@code BaseBpmnXMLConverter} reads the default flow only from the no-namespace
 * attribute ({@code getAttributeValue(null, "default")} -> {@code Gateway.setDefaultFlow}). A
 * namespaced {@code flowable:default} is silently ignored, leaving the gateway with no
 * engine-registered default flow — so the intended "fall through to the reject/vendor branch"
 * never fires. This test deploys the real seed through the production-faithful engine and asserts
 * both gateways carry a registered default flow; it fails if anyone reintroduces {@code flowable:default}.
 */
@Tag("flow")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Procurement seed — exclusive gateways register their default flow")
class ProcurementSeedDefaultFlowTest {

    private static final String SEED =
        "examples/tenants/default/bpmn/procurement-approval-process.bpmn20.xml";

    private WerkflowTestProcessEngine engine;
    private RepositoryService repositoryService;

    @BeforeAll
    void deploySeed() {
        engine = WerkflowTestProcessEngine.build("procurementDefaultFlow");
        repositoryService = engine.getProcessEngine().getRepositoryService();
        repositoryService.createDeployment().addClasspathResource(SEED).deploy();
    }

    @AfterAll
    void stopEngine() {
        if (engine != null) {
            engine.close();
        }
    }

    @Test
    @DisplayName("directPurchaseGateway and approvalDecision both register a default flow")
    void exclusiveGateways_haveRegisteredDefaultFlow() {
        ProcessDefinition pd = repositoryService.createProcessDefinitionQuery()
            .processDefinitionKey("procurement-approval-process")
            .latestVersion()
            .singleResult();
        assertThat(pd).as("procurement seed deployed").isNotNull();

        BpmnModel model = repositoryService.getBpmnModel(pd.getId());

        assertThat(defaultFlowOf(model, "directPurchaseGateway"))
            .as("directPurchaseGateway default flow")
            .isEqualTo("flowToVendorDefault");
        assertThat(defaultFlowOf(model, "approvalDecision"))
            .as("approvalDecision default flow")
            .isEqualTo("flowRejectDefault");
    }

    private static String defaultFlowOf(BpmnModel model, String gatewayId) {
        FlowElement el = model.getMainProcess().getFlowElement(gatewayId);
        assertThat(el).as("gateway %s present", gatewayId).isInstanceOf(Gateway.class);
        return ((Gateway) el).getDefaultFlow();
    }
}
