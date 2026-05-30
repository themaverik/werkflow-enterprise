package com.werkflow.engine.process;

import com.werkflow.engine.testsupport.FormTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;

@Tag("fragment")
@DisplayName("Shipped form schemas — process variable mapping (ADR-028 Phase 2)")
class FormSchemaTest {

    @Test
    @DisplayName("procurement-request-form declares all process input variables")
    void procurementRequestForm_hasRequiredFields() throws IOException {
        FormTestSupport.assertHasFields(
            "forms/procurement-request-form.json",
            "title", "requestedAmount", "vendor", "department", "priority");
    }

    @Test
    @DisplayName("procurement-approval declares the canonical decision variable (ADR-025)")
    void procurementApprovalForm_hasDecisionField() throws IOException {
        FormTestSupport.assertHasFields(
            "forms/procurement-approval.json",
            "decision", "approvalComments");
    }
}
