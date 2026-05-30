package com.werkflow.engine.workflow;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.flowable.engine.RepositoryService;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class BpmnGroupValidatorTest {

    @Mock RepositoryService repositoryService;

    private BpmnGroupValidator validator;
    private Method isValidGroup;
    private Method isDeprecatedCompoundGroup;

    @BeforeEach
    void setUp() throws Exception {
        validator = new BpmnGroupValidator(repositoryService);
        isValidGroup = BpmnGroupValidator.class.getDeclaredMethod("isValidGroup", String.class);
        isValidGroup.setAccessible(true);
        isDeprecatedCompoundGroup = BpmnGroupValidator.class.getDeclaredMethod("isDeprecatedCompoundGroup", String.class);
        isDeprecatedCompoundGroup.setAccessible(true);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "SUPER_ADMIN", "WORKFLOW_DESIGNER", "DOA_L1", "DOA_L2", "DOA_L3", "DOA_L4", "DEPT:FIN", "DEPT:IT"})
    void isValidGroup_acceptsKnownPatterns(String group) throws Exception {
        assertThat((boolean) isValidGroup.invoke(validator, group)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"DOA:L1", "DOA:L2", "DOA:L3", "DEPT:FIN::DOA:L2", "DEPT:FIN::DOA:L3", "UNKNOWN_GROUP", ""})
    void isValidGroup_rejectsUnknownAndDeprecatedPatterns(String group) throws Exception {
        assertThat((boolean) isValidGroup.invoke(validator, group)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"DEPT:FIN::DOA:L2,true", "DEPT:IT::DOA:L3,true", "DEPT:FIN,false", "DOA_L2,false", "ADMIN,false"})
    void isDeprecatedCompoundGroup_detectsCompoundDeptGroups(String group, boolean expected) throws Exception {
        assertThat((boolean) isDeprecatedCompoundGroup.invoke(validator, group)).isEqualTo(expected);
    }
}
