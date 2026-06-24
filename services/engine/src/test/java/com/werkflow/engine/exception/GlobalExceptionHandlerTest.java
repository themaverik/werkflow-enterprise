package com.werkflow.engine.exception;

import com.werkflow.engine.dto.DanglingReferenceResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.WebRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link GlobalExceptionHandler} — verifies response shapes for
 * exception types handled by the global advice. No Spring context required.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("handleDanglingReferenceException returns 422 with missingForms and missingDecisions")
    void danglingReference_returns422WithLists() {
        List<String> forms = List.of("form-a");
        List<String> decisions = List.of("dec-b");
        DanglingReferenceException ex = new DanglingReferenceException(forms, decisions);

        ResponseEntity<DanglingReferenceResponse> response =
                handler.handleDanglingReferenceException(ex, mock(WebRequest.class));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        DanglingReferenceResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.missingForms()).containsExactly("form-a");
        assertThat(body.missingDecisions()).containsExactly("dec-b");
    }

    @Test
    @DisplayName("handleDanglingReferenceException returns 422 with empty lists when no refs missing")
    void danglingReference_emptyLists_returns422() {
        DanglingReferenceException ex = new DanglingReferenceException(List.of(), List.of());

        ResponseEntity<DanglingReferenceResponse> response =
                handler.handleDanglingReferenceException(ex, mock(WebRequest.class));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        DanglingReferenceResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.missingForms()).isEmpty();
        assertThat(body.missingDecisions()).isEmpty();
    }
}
