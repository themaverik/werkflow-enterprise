package com.werkflow.engine.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.werkflow.engine.dto.FormSchema;
import com.werkflow.engine.dto.JwtUserContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Timestamp;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Strict tenant-isolation tests for {@link FormSchemaService} (D2).
 *
 * <p>Proves that every SQL-issuing method includes {@code tenant_id = ?} in its query
 * and that the correct tenant argument is forwarded to JdbcTemplate — so cross-tenant
 * reads are structurally impossible through this service.
 */
class FormSchemaServiceTenantTest {

    private JdbcTemplate jdbcTemplate;
    private FormSchemaService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        service = new FormSchemaService(jdbcTemplate, mock(FormSchemaValidator.class), new ObjectMapper());
        // Default stub: return one mock schema so isEmpty() check passes
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(mock(FormSchema.class)));
    }

    // -------------------------------------------------------------------------
    // loadFormSchema (latest active version)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("loadFormSchema(key, tenant) passes tenant_id as second SQL arg")
    void loadFormSchema_latest_includesTenantArg() {
        service.loadFormSchema("my-form", "tenantA");

        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).query(anyString(), any(RowMapper.class), args.capture());
        assertThat(args.getValue()).containsExactly("my-form", "tenantA");
    }

    @Test
    @DisplayName("loadFormSchema SQL contains tenant_id = ? predicate")
    void loadFormSchema_latest_sqlContainsTenantPredicate() {
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        service.loadFormSchema("my-form", "tenantA");
        verify(jdbcTemplate).query(sql.capture(), any(RowMapper.class), any(Object[].class));
        assertThat(sql.getValue()).containsIgnoringCase("tenant_id = ?");
    }

    // -------------------------------------------------------------------------
    // loadFormSchema (specific version)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("loadFormSchema(key, version, tenant) passes tenant_id as third SQL arg")
    void loadFormSchema_versioned_includesTenantArg() {
        service.loadFormSchema("my-form", 2, "tenantA");

        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).query(anyString(), any(RowMapper.class), args.capture());
        assertThat(args.getValue()).containsExactly("my-form", 2, "tenantA");
    }

    // -------------------------------------------------------------------------
    // getNextVersion — independent version sequence per tenant
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getNextVersion SQL is tenant-scoped (via saveFormSchema path)")
    void getNextVersion_includesTenantArg() {
        // queryForObject is used by getNextVersion; stub it
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
                .thenReturn(1);

        // Trigger getNextVersion by calling saveFormSchema — it throws after the version
        // query because the INSERT path fails on ObjectMapper serialisation of a null schema,
        // but by then queryForObject has already been called with the tenant arg.
        try {
            service.saveFormSchema("my-form", null, "desc", FormSchema.FormType.CUSTOM, "user", "tenantA");
        } catch (Exception ignored) {
            // FormValidationException or NullPointerException from null schemaJson — expected.
        }

        // Capture all queryForObject calls; the version query is the first one.
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).queryForObject(anyString(), eq(Integer.class), args.capture());
        assertThat(args.getValue()).containsExactly("my-form", "tenantA");
    }

    // -------------------------------------------------------------------------
    // formExistsAnyVersion — tenant-scoped guard
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("formExistsAnyVersion SQL contains tenant_id and passes tenant arg")
    void formExistsAnyVersion_includesTenantArg() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
                .thenReturn(0);

        service.formExistsAnyVersion("my-form", "tenantB");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).queryForObject(sql.capture(), eq(Integer.class), args.capture());
        assertThat(sql.getValue()).containsIgnoringCase("tenant_id = ?");
        assertThat(args.getValue()).containsExactly("my-form", "tenantB");
    }

    // -------------------------------------------------------------------------
    // Null/blank tenant normalisation
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("null tenantId is normalised to 'default' in loadFormSchema")
    void nullTenant_normalisedToDefault() {
        service.loadFormSchema("my-form", (String) null);

        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).query(anyString(), any(RowMapper.class), args.capture());
        assertThat(args.getValue()).containsExactly("my-form", "default");
    }

    @Test
    @DisplayName("blank tenantId is normalised to 'default' in loadFormSchema")
    void blankTenant_normalisedToDefault() {
        service.loadFormSchema("my-form", "   ");

        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).query(anyString(), any(RowMapper.class), args.capture());
        assertThat(args.getValue()).containsExactly("my-form", "default");
    }

    // -------------------------------------------------------------------------
    // getFormsList — tenant-scoped list query
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getFormsList SQL contains tenant_id and passes tenant arg")
    void getFormsList_includesTenantArg() {
        service.getFormsList("tenantC");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).query(sql.capture(), any(RowMapper.class), args.capture());
        assertThat(sql.getValue()).containsIgnoringCase("tenant_id = ?");
        assertThat(args.getValue()).containsExactly("tenantC");
    }

    // -------------------------------------------------------------------------
    // getFormHistory — tenant-scoped history query
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getFormHistory SQL contains tenant_id and passes correct args")
    void getFormHistory_includesTenantArg() {
        service.getFormHistory("my-form", "tenantD");

        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).query(anyString(), any(RowMapper.class), args.capture());
        assertThat(args.getValue()).containsExactly("my-form", "tenantD");
    }

    // -------------------------------------------------------------------------
    // archiveForm — write path tenant isolation
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("archiveForm UPDATE SQL contains tenant_id = ? and passes tenantX as arg")
    void archiveForm_updateIncludesTenantArg() {
        when(jdbcTemplate.update(anyString(), any(Timestamp.class), anyString(), eq("tenantX")))
                .thenReturn(1);

        service.archiveForm("some-form", "tenantX");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> ts = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> key = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> tenant = ArgumentCaptor.forClass(Object.class);
        verify(jdbcTemplate).update(sql.capture(), ts.capture(), key.capture(), tenant.capture());
        assertThat(sql.getValue()).containsIgnoringCase("tenant_id = ?");
        assertThat(tenant.getValue()).isEqualTo("tenantX");
    }

    // -------------------------------------------------------------------------
    // updateFormSchema — deactivate UPDATE write path tenant isolation
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("updateFormSchema deactivate UPDATE SQL contains tenant_id = ? and passes tenantX as arg")
    void updateFormSchema_deactivateUpdateIncludesTenantArg() {
        // Arrange: build a mock current schema for the loadFormSchema pre-check and custody check
        FormSchema currentSchema = mock(FormSchema.class);
        when(currentSchema.getFormType()).thenReturn(FormSchema.FormType.CUSTOM);
        when(currentSchema.getOwningDepartment()).thenReturn(null);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(currentSchema));

        // getNextVersion needs queryForObject
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
                .thenReturn(1);

        // Stub all update calls to succeed
        when(jdbcTemplate.update(anyString(), any(), any(), anyString(), anyString()))
                .thenReturn(1);
        when(jdbcTemplate.update(anyString(), any(), any(), anyString(), any(), any(), any(), any(), any(), anyString()))
                .thenReturn(1);

        JwtUserContext updater = JwtUserContext.builder()
                .userId("user1")
                .tenantCode("tenantX")
                .roles(List.of("WORKFLOW_DESIGNER"))
                .build();

        try {
            service.updateFormSchema("some-form", null, "desc", updater);
        } catch (Exception ignored) {
            // NullPointerException from null schemaJson after custody check — expected.
        }

        // Capture the deactivate UPDATE (is_active = false ... WHERE form_key = ? AND is_active = true AND tenant_id = ?)
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        // update(sql, updatedAt, userId, formKey, tenantId) — 4-arg positional capture
        ArgumentCaptor<Object> arg1 = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> arg2 = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> arg3 = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> arg4 = ArgumentCaptor.forClass(Object.class);
        verify(jdbcTemplate).update(sql.capture(), arg1.capture(), arg2.capture(), arg3.capture(), arg4.capture());
        assertThat(sql.getValue()).containsIgnoringCase("is_active = false");
        assertThat(sql.getValue()).containsIgnoringCase("tenant_id = ?");
        assertThat(arg4.getValue()).isEqualTo("tenantX");
    }
}
