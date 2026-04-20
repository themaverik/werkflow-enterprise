package com.werkflow.admin.service;

import com.werkflow.admin.dto.CustodyMappingRequest;
import com.werkflow.admin.dto.CustodyMappingResponse;
import com.werkflow.admin.entity.TenantCustodyMapping;
import com.werkflow.admin.repository.TenantCustodyMappingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantCustodyMappingServiceTest {

    @Mock
    TenantCustodyMappingRepository repo;

    @InjectMocks
    TenantCustodyMappingService service;

    @Test
    void listByTenant_returnsOrderedMappings() {
        TenantCustodyMapping mapping1 = new TenantCustodyMapping();
        mapping1.setId(1L);
        mapping1.setTenantCode("default");
        mapping1.setCategoryKey("ASSET_A");
        mapping1.setCustodyGroup("flowable-group-1");
        mapping1.setDisplayName("Asset A Group");
        mapping1.setCreatedAt(LocalDateTime.now());
        mapping1.setUpdatedAt(LocalDateTime.now());

        TenantCustodyMapping mapping2 = new TenantCustodyMapping();
        mapping2.setId(2L);
        mapping2.setTenantCode("default");
        mapping2.setCategoryKey("ASSET_B");
        mapping2.setCustodyGroup("flowable-group-2");
        mapping2.setDisplayName("Asset B Group");
        mapping2.setCreatedAt(LocalDateTime.now());
        mapping2.setUpdatedAt(LocalDateTime.now());

        when(repo.findByTenantCodeOrderByCategoryKey("default")).thenReturn(List.of(mapping1, mapping2));

        List<CustodyMappingResponse> result = service.listByTenant("default");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).categoryKey()).isEqualTo("ASSET_A");
        assertThat(result.get(1).categoryKey()).isEqualTo("ASSET_B");
    }

    @Test
    void create_savesAndReturnsResponse() {
        CustodyMappingRequest request = new CustodyMappingRequest(
            "default",
            "ASSET_X",
            "flowable-group-x",
            "Asset X Group"
        );

        TenantCustodyMapping saved = new TenantCustodyMapping();
        saved.setId(5L);
        saved.setTenantCode(request.tenantCode());
        saved.setCategoryKey(request.categoryKey());
        saved.setCustodyGroup(request.custodyGroup());
        saved.setDisplayName(request.displayName());
        saved.setCreatedAt(LocalDateTime.now());
        saved.setUpdatedAt(LocalDateTime.now());

        when(repo.save(org.mockito.ArgumentMatchers.any(TenantCustodyMapping.class))).thenReturn(saved);

        CustodyMappingResponse result = service.create(request);

        assertThat(result.id()).isEqualTo(5L);
        assertThat(result.tenantCode()).isEqualTo("default");
        assertThat(result.categoryKey()).isEqualTo("ASSET_X");
        assertThat(result.custodyGroup()).isEqualTo("flowable-group-x");
    }
}
