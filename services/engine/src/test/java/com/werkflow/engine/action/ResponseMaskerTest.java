package com.werkflow.engine.action;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class ResponseMaskerTest {

    private final ResponseMasker masker = new ResponseMasker("");

    @Test
    void mask_redactsDefaultTokenField() {
        String json = "{\"data\":\"ok\",\"token\":\"abc123\"}";
        String masked = masker.mask(json, List.of());
        assertThat(masked).contains("\"token\":null");
        assertThat(masked).doesNotContain("abc123");
    }

    @Test
    void mask_redactsDesignerSuppliedField() {
        String json = "{\"orderRef\":\"ORD-001\",\"internalCode\":\"IC-999\"}";
        String masked = masker.mask(json, List.of("$.internalCode"));
        assertThat(masked).contains("\"internalCode\":null");
        assertThat(masked).doesNotContain("IC-999");
    }

    @Test
    void mask_defaultsCannotBeRemovedByDesigner() {
        String json = "{\"password\":\"secret\"}";
        String masked = masker.mask(json, List.of());
        assertThat(masked).contains("\"password\":null");
    }

    @Test
    void mask_returnsUnchangedForNonJsonInput() {
        String notJson = "plain text response";
        String masked = masker.mask(notJson, List.of());
        assertThat(masked).isEqualTo(notJson);
    }
}
