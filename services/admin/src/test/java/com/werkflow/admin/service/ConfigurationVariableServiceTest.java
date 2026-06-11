package com.werkflow.admin.service;

import com.werkflow.admin.designtime.platform.service.LocaleProjector;
import com.werkflow.admin.dto.ConfigVarRequest;
import com.werkflow.admin.dto.ConfigVarResponse;
import com.werkflow.admin.entity.ConfigurationVariable;
import com.werkflow.admin.repository.ConfigurationVariableRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConfigurationVariableServiceTest {

    @Mock ConfigurationVariableRepository repository;
    @Mock LocaleProjector localeProjector;
    @InjectMocks ConfigurationVariableService service;

    private ConfigurationVariable var(Long id, String key, String value, String type) {
        ConfigurationVariable v = new ConfigurationVariable();
        v.setId(id);
        v.setTenantCode("default");
        v.setVarKey(key);
        v.setVarValue(value);
        v.setVarType(type);
        v.setCreatedAt(LocalDateTime.now());
        v.setUpdatedAt(LocalDateTime.now());
        return v;
    }

    @Test
    void listByTenant_returnsOrderedVars() {
        when(repository.findByTenantCodeOrderByVarKey("default")).thenReturn(List.of(
            var(1L, "DOA_L1_AMOUNT", "10000", "NUMBER"),
            var(2L, "DOA_L2_AMOUNT", "50000", "NUMBER")
        ));

        List<ConfigVarResponse> result = service.listByTenant("default");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).varKey()).isEqualTo("DOA_L1_AMOUNT");
        assertThat(result.get(1).varKey()).isEqualTo("DOA_L2_AMOUNT");
    }

    @Test
    void getVarMap_returnsKeyValueMap() {
        when(repository.findByTenantCodeOrderByVarKey("default")).thenReturn(List.of(
            var(1L, "DOA_L1_AMOUNT", "10000", "NUMBER"),
            var(2L, "CSS_THEME",     "dark",   "STRING")
        ));

        Map<String, String> result = service.getVarMap("default");

        assertThat(result).containsEntry("DOA_L1_AMOUNT", "10000");
        assertThat(result).containsEntry("CSS_THEME", "dark");
    }

    @Test
    void create_savesNewVar() {
        ConfigVarRequest req = new ConfigVarRequest("default", "DOA_L1_AMOUNT", "10000", "NUMBER", null);
        when(repository.findByTenantCodeAndVarKey("default", "DOA_L1_AMOUNT")).thenReturn(Optional.empty());
        ConfigurationVariable saved = var(1L, "DOA_L1_AMOUNT", "10000", "NUMBER");
        when(repository.save(any(ConfigurationVariable.class))).thenReturn(saved);

        ConfigVarResponse result = service.create(req);

        assertThat(result.varKey()).isEqualTo("DOA_L1_AMOUNT");
        assertThat(result.varValue()).isEqualTo("10000");
    }

    @Test
    void create_upsertsExistingKey() {
        ConfigVarRequest req = new ConfigVarRequest("default", "DOA_L1_AMOUNT", "15000", "NUMBER", null);
        ConfigurationVariable existing = var(1L, "DOA_L1_AMOUNT", "10000", "NUMBER");
        when(repository.findByTenantCodeAndVarKey("default", "DOA_L1_AMOUNT")).thenReturn(Optional.of(existing));
        when(repository.save(any(ConfigurationVariable.class))).thenAnswer(inv -> inv.getArgument(0));

        ConfigVarResponse result = service.create(req);

        assertThat(result.varValue()).isEqualTo("15000");
        verify(repository).save(any(ConfigurationVariable.class));
    }

    @Test
    void update_modifiesExistingVar() {
        ConfigurationVariable existing = var(1L, "DOA_L1_AMOUNT", "10000", "NUMBER");
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        ConfigVarRequest req = new ConfigVarRequest("default", "DOA_L1_AMOUNT", "15000", "NUMBER", "Updated");
        ConfigurationVariable updated = var(1L, "DOA_L1_AMOUNT", "15000", "NUMBER");
        when(repository.save(any(ConfigurationVariable.class))).thenReturn(updated);

        ConfigVarResponse result = service.update(1L, req);

        assertThat(result.varValue()).isEqualTo("15000");
    }

    @Test
    void create_evictsLocaleCacheForLocaleVar() {
        ConfigVarRequest req = new ConfigVarRequest("default", "tenantLocale",
            "{\"currencyCode\":\"INR\"}", "LOCALE", null);
        when(repository.findByTenantCodeAndVarKey("default", "tenantLocale")).thenReturn(Optional.empty());
        when(repository.save(any(ConfigurationVariable.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(req);

        verify(localeProjector).evict("default");
    }

    @Test
    void update_evictsLocaleCacheForLocaleVar() {
        ConfigurationVariable existing = var(1L, "tenantLocale", "{\"currencyCode\":\"USD\"}", "LOCALE");
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.save(any(ConfigurationVariable.class))).thenAnswer(inv -> inv.getArgument(0));
        ConfigVarRequest req = new ConfigVarRequest("default", "tenantLocale",
            "{\"currencyCode\":\"INR\"}", "LOCALE", null);

        service.update(1L, req);

        verify(localeProjector).evict("default");
    }

    @Test
    void create_doesNotEvictLocaleCacheForNonLocaleVar() {
        ConfigVarRequest req = new ConfigVarRequest("default", "DOA_L1_AMOUNT", "10000", "NUMBER", null);
        when(repository.findByTenantCodeAndVarKey("default", "DOA_L1_AMOUNT")).thenReturn(Optional.empty());
        when(repository.save(any(ConfigurationVariable.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(req);

        verify(localeProjector, never()).evict(any());
    }

    @Test
    void delete_removesById() {
        ConfigurationVariable existing = new ConfigurationVariable();
        existing.setTenantCode("acme");
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        service.delete(1L, "acme");
        verify(repository).delete(existing);
    }

    @Test
    void delete_throwsWhenNotFound() {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.delete(99L, "acme"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("99");
    }
}
