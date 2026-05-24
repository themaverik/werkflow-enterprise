package com.werkflow.engine.workflow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BpmnBundleRefExtractorTest {

    private final BpmnBundleRefExtractor extractor = new BpmnBundleRefExtractor();

    @Test
    @DisplayName("extracts process key and all DMN serviceTask decisionTableReferenceKey values")
    void extracts_processKey_and_decisionRefs() {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:flowable="http://flowable.org/bpmn">
              <process id="capex-approval" name="Capex Approval">
                <serviceTask id="d1" flowable:type="dmn">
                  <extensionElements>
                    <flowable:field name="decisionTableReferenceKey"><flowable:string>doa_routing</flowable:string></flowable:field>
                  </extensionElements>
                </serviceTask>
                <serviceTask id="d2" flowable:type="dmn">
                  <extensionElements>
                    <flowable:field name="decisionTableReferenceKey"><flowable:string>risk_scoring</flowable:string></flowable:field>
                  </extensionElements>
                </serviceTask>
              </process>
            </definitions>
            """;

        BpmnBundleRefExtractor.BundleRefs refs = extractor.extract(xml);

        assertThat(refs.processKey()).isEqualTo("capex-approval");
        assertThat(refs.decisionRefs()).containsExactlyInAnyOrder("doa_routing", "risk_scoring");
    }

    @Test
    @DisplayName("returns empty decisionRefs when no DMN service tasks reference a decision")
    void empty_decisionRefs_when_none() {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:flowable="http://flowable.org/bpmn">
              <process id="simple-process">
                <userTask id="t1"/>
              </process>
            </definitions>
            """;

        BpmnBundleRefExtractor.BundleRefs refs = extractor.extract(xml);

        assertThat(refs.processKey()).isEqualTo("simple-process");
        assertThat(refs.decisionRefs()).isEmpty();
    }

    @Test
    @DisplayName("ignores non-DMN service tasks and DMN tasks missing the reference key")
    void skips_blank_decisionRef() {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:flowable="http://flowable.org/bpmn">
              <process id="p1">
                <serviceTask id="rest" flowable:type="http"/>
                <serviceTask id="dmnNoKey" flowable:type="dmn"/>
                <serviceTask id="dmnReal" flowable:type="dmn">
                  <extensionElements>
                    <flowable:field name="decisionTableReferenceKey"><flowable:string>real_decision</flowable:string></flowable:field>
                  </extensionElements>
                </serviceTask>
              </process>
            </definitions>
            """;

        BpmnBundleRefExtractor.BundleRefs refs = extractor.extract(xml);

        assertThat(refs.decisionRefs()).containsExactly("real_decision");
    }

    @Test
    @DisplayName("excludes expression-keyed decisions (not statically resolvable, so not bundleable)")
    void excludes_expression_keyed_decision() {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:flowable="http://flowable.org/bpmn">
              <process id="p1">
                <serviceTask id="dynamic" flowable:type="dmn">
                  <extensionElements>
                    <flowable:field name="decisionTableReferenceKey"><flowable:expression>${decisionKey}</flowable:expression></flowable:field>
                  </extensionElements>
                </serviceTask>
                <serviceTask id="static" flowable:type="dmn">
                  <extensionElements>
                    <flowable:field name="decisionTableReferenceKey"><flowable:string>static_decision</flowable:string></flowable:field>
                  </extensionElements>
                </serviceTask>
              </process>
            </definitions>
            """;

        BpmnBundleRefExtractor.BundleRefs refs = extractor.extract(xml);

        assertThat(refs.decisionRefs()).containsExactly("static_decision");
    }

    @Test
    @DisplayName("de-duplicates the same decision key referenced by multiple DMN tasks")
    void deduplicates_repeated_decision_key() {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:flowable="http://flowable.org/bpmn">
              <process id="p1">
                <serviceTask id="d1" flowable:type="dmn">
                  <extensionElements>
                    <flowable:field name="decisionTableReferenceKey"><flowable:string>doa_routing</flowable:string></flowable:field>
                  </extensionElements>
                </serviceTask>
                <serviceTask id="d2" flowable:type="dmn">
                  <extensionElements>
                    <flowable:field name="decisionTableReferenceKey"><flowable:string>doa_routing</flowable:string></flowable:field>
                  </extensionElements>
                </serviceTask>
              </process>
            </definitions>
            """;

        BpmnBundleRefExtractor.BundleRefs refs = extractor.extract(xml);

        assertThat(refs.decisionRefs()).containsExactly("doa_routing");
    }

    @Test
    @DisplayName("rejects malformed XML")
    void rejects_malformed_xml() {
        assertThatThrownBy(() -> extractor.extract("<definitions><process"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("does not resolve external entities (XXE-safe)")
    void xxe_safe() {
        String xml = """
            <?xml version="1.0"?>
            <!DOCTYPE foo [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL">
              <process id="&xxe;"/>
            </definitions>
            """;

        assertThatThrownBy(() -> extractor.extract(xml))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
