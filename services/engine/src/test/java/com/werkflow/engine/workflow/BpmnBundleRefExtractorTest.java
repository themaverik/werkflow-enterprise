package com.werkflow.engine.workflow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BpmnBundleRefExtractorTest {

    private final BpmnBundleRefExtractor extractor = new BpmnBundleRefExtractor();

    @Test
    @DisplayName("extracts process key and all flowable:decisionRef values")
    void extracts_processKey_and_decisionRefs() {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:flowable="http://flowable.org/bpmn">
              <process id="capex-approval" name="Capex Approval">
                <businessRuleTask id="brt1" flowable:decisionRef="doa_routing"/>
                <businessRuleTask id="brt2" flowable:decisionRef="risk_scoring"/>
              </process>
            </definitions>
            """;

        BpmnBundleRefExtractor.BundleRefs refs = extractor.extract(xml);

        assertThat(refs.processKey()).isEqualTo("capex-approval");
        assertThat(refs.decisionRefs()).containsExactlyInAnyOrder("doa_routing", "risk_scoring");
    }

    @Test
    @DisplayName("returns empty decisionRefs when no business rule tasks reference a decision")
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
    @DisplayName("skips business rule tasks with a blank or missing decisionRef")
    void skips_blank_decisionRef() {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:flowable="http://flowable.org/bpmn">
              <process id="p1">
                <businessRuleTask id="brt1" flowable:decisionRef=""/>
                <businessRuleTask id="brt2"/>
                <businessRuleTask id="brt3" flowable:decisionRef="real_decision"/>
              </process>
            </definitions>
            """;

        BpmnBundleRefExtractor.BundleRefs refs = extractor.extract(xml);

        assertThat(refs.decisionRefs()).containsExactly("real_decision");
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
