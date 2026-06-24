package com.werkflow.engine.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.werkflow.engine.dto.FormSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link FormSchemaService#loadFormSchemaByRef(String, String)} — the ADR-026 P2/F1
 * version-pin resolution. Verifies the dispatch between latest and pinned-version lookups.
 */
class FormSchemaServiceRefTest {

    private JdbcTemplate jdbcTemplate;
    private FormSchemaService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        service = new FormSchemaService(jdbcTemplate, mock(FormSchemaValidator.class), new ObjectMapper());
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(mock(FormSchema.class)));
    }

    @Test
    @DisplayName("a pinned 'key@version' resolves the exact version (version and tenant passed to query)")
    void pinnedRef_resolvesExactVersion() {
        service.loadFormSchemaByRef("capex-request-form@3", "acme");

        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).query(anyString(), any(RowMapper.class), args.capture());
        assertThat(args.getValue()).containsExactly("capex-request-form", 3, "acme");
    }

    @Test
    @DisplayName("a bare 'key' resolves latest (no version arg, but tenant arg present)")
    void bareRef_resolvesLatest() {
        service.loadFormSchemaByRef("capex-request-form", "acme");

        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).query(anyString(), any(RowMapper.class), args.capture());
        assertThat(args.getValue()).containsExactly("capex-request-form", "acme");
    }

    @Test
    @DisplayName("a non-numeric '@' suffix is treated as part of the key (latest lookup, tenant present)")
    void nonNumericSuffix_treatedAsKey() {
        service.loadFormSchemaByRef("weird@key", "acme");

        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).query(anyString(), any(RowMapper.class), args.capture());
        assertThat(args.getValue()).containsExactly("weird@key", "acme");
    }

    @Test
    @DisplayName("null tenantId is normalised to 'default'")
    void nullTenant_normalisedToDefault() {
        service.loadFormSchemaByRef("some-form", null);

        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).query(anyString(), any(RowMapper.class), args.capture());
        assertThat(args.getValue()).containsExactly("some-form", "default");
    }
}
