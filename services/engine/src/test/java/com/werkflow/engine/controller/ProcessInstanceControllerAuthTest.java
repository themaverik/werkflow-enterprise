package com.werkflow.engine.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that destructive ProcessInstanceController endpoints carry the expected
 * @PreAuthorize annotation. This guards against the annotations being accidentally
 * removed during refactoring.
 */
class ProcessInstanceControllerAuthTest {

    private static final String EXPECTED_PERMISSION = "hasPermission(null, 'PROCESS:MANAGE')";

    @Test
    void deleteProcessInstance_hasProcessManageGuard() throws Exception {
        Method method = ProcessInstanceController.class.getMethod(
            "deleteProcessInstance", String.class, String.class);
        assertPreAuthorize(method);
    }

    @Test
    void suspendProcessInstance_hasProcessManageGuard() throws Exception {
        Method method = ProcessInstanceController.class.getMethod(
            "suspendProcessInstance", String.class);
        assertPreAuthorize(method);
    }

    @Test
    void activateProcessInstance_hasProcessManageGuard() throws Exception {
        Method method = ProcessInstanceController.class.getMethod(
            "activateProcessInstance", String.class);
        assertPreAuthorize(method);
    }

    @Test
    void setProcessVariables_hasProcessManageGuard() throws Exception {
        Method method = ProcessInstanceController.class.getMethod(
            "setProcessVariables", String.class, java.util.Map.class);
        assertPreAuthorize(method);
    }

    private void assertPreAuthorize(Method method) {
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertThat(annotation)
            .as("@PreAuthorize missing on " + method.getName())
            .isNotNull();
        assertThat(annotation.value())
            .as("@PreAuthorize value on " + method.getName())
            .isEqualTo(EXPECTED_PERMISSION);
    }
}
