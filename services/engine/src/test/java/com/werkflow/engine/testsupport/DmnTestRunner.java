package com.werkflow.engine.testsupport;

import org.flowable.engine.ProcessEngine;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Harness helper for isolated DMN decision table tests (ADR-028 Phase 2).
 *
 * <p>{@link #deploy} deploys a DMN classpath resource and returns a {@link DecisionRunner} bound
 * to that specific decision key. Callers hold the returned runner — no shared mutable state means
 * multiple DMNs can be deployed in the same test class without interference.
 *
 * <p>Evaluation uses a generated wrapper BPMN ({@code flowable:type="dmn"} service task) so DMN
 * outputs land in process variables — the same execution path Flowable uses in production. JUEL
 * expressions are supported; FEEL range syntax is not (Flowable 7.x DMN engine limitation).
 *
 * <p>Usage:
 * <pre>{@code
 * class MyDmnTest extends WerkflowProcessTest {
 *     private DmnTestRunner.DecisionRunner procurement;
 *
 *     @BeforeAll void setup() {
 *         startEngine("myDb");
 *         procurement = dmnRunner.deploy("dmn/procurement-matrix.dmn", "procurement_matrix");
 *     }
 *
 *     @Test void routes_correctly() {
 *         assertThat(procurement.output("procurementPath", Map.of("amount", 10000, "category", "SUPPLIES")))
 *             .isEqualTo("DIRECT_PURCHASE");
 *     }
 * }
 * }</pre>
 */
public final class DmnTestRunner {

    private final RepositoryService repositoryService;
    private final RuntimeService runtimeService;

    DmnTestRunner(ProcessEngine engine) {
        this.repositoryService = engine.getRepositoryService();
        this.runtimeService = engine.getRuntimeService();
    }

    /**
     * Deploys a DMN file from the test classpath together with a generated wrapper BPMN and
     * returns a {@link DecisionRunner} bound to {@code decisionKey}.
     *
     * <p>The wrapper process ID is {@code "dmn-wrapper-<decisionKey>"}, so multiple calls with
     * different keys produce independent process definitions with no key collision.
     *
     * @param classpathResource path to the DMN file on the test classpath
     * @param decisionKey       the {@code id} attribute of the {@code <decision>} element in the DMN
     * @return a {@link DecisionRunner} bound to this decision key, ready to evaluate inputs
     */
    public DecisionRunner deploy(String classpathResource, String decisionKey) {
        repositoryService.createDeployment()
            .addClasspathResource(classpathResource)
            .addString("dmn-wrapper-" + decisionKey + ".bpmn20.xml", buildWrapperBpmn(decisionKey))
            .deploy();
        return new DecisionRunner(decisionKey, runtimeService);
    }

    private static String buildWrapperBpmn(String decisionKey) {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:flowable="http://flowable.org/bpmn"
                         targetNamespace="http://werkflow.com/bpmn/test">
              <process id="dmn-wrapper-%s" isExecutable="true">
                <startEvent id="start"/>
                <sequenceFlow id="f1" sourceRef="start" targetRef="evaluate"/>
                <serviceTask id="evaluate" flowable:type="dmn">
                  <extensionElements>
                    <flowable:field name="decisionTableReferenceKey">
                      <flowable:string>%s</flowable:string>
                    </flowable:field>
                  </extensionElements>
                </serviceTask>
                <sequenceFlow id="f2" sourceRef="evaluate" targetRef="park"/>
                <userTask id="park"/>
                <sequenceFlow id="f3" sourceRef="park" targetRef="end"/>
                <endEvent id="end"/>
              </process>
            </definitions>
            """.formatted(decisionKey, decisionKey);
    }

    /**
     * Bound to a specific decision key. Evaluate inputs and read output variables from the
     * live process instance (the process parks at a user task after the DMN fires).
     */
    public static final class DecisionRunner {

        private final String decisionKey;
        private final RuntimeService runtimeService;

        DecisionRunner(String decisionKey, RuntimeService runtimeService) {
            this.decisionKey = decisionKey;
            this.runtimeService = runtimeService;
        }

        /**
         * Evaluates the decision with the given inputs and returns the value of {@code outputVar}.
         *
         * @param outputVar the variable name written by the DMN service task
         * @param inputs    input variables matching the DMN's input column names
         */
        public Object output(String outputVar, Map<String, Object> inputs) {
            var pi = runtimeService.startProcessInstanceByKey("dmn-wrapper-" + decisionKey, inputs);
            return runtimeService.getVariable(pi.getId(), outputVar);
        }

        /**
         * Evaluates the decision and returns multiple output variables as an immutable map.
         * Useful when asserting two or more outputs from a single rule in one call.
         *
         * @param inputs     input variables matching the DMN's input column names
         * @param outputVars the variable names to collect from the decision output
         */
        public Map<String, Object> outputs(Map<String, Object> inputs, String... outputVars) {
            var pi = runtimeService.startProcessInstanceByKey("dmn-wrapper-" + decisionKey, inputs);
            var result = new LinkedHashMap<String, Object>();
            for (String var : outputVars) {
                result.put(var, runtimeService.getVariable(pi.getId(), var));
            }
            return Map.copyOf(result);
        }
    }
}
