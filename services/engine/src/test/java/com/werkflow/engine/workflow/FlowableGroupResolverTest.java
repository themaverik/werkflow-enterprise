package com.werkflow.engine.workflow;

import com.werkflow.engine.client.AdminServiceClient;
import com.werkflow.engine.client.UserProfileDto;
import com.werkflow.engine.dto.JwtUserContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlowableGroupResolverTest {

    @Mock AdminServiceClient adminServiceClient;
    @Mock FlowableGroupProperties properties;
    @InjectMocks FlowableGroupResolver resolver;

    @BeforeEach
    void setUp() {
        when(properties.getRoleMappings()).thenReturn(Map.of(
            "admin",       List.of("ADMIN", "SUPER_ADMIN"),
            "super_admin", List.of("SUPER_ADMIN")
        ));
    }

    @Test
    void step3_doaLevel2_emitsBothFormats() {
        UserProfileDto profile = new UserProfileDto("user-1", "default", 2, "FIN");
        when(adminServiceClient.getUserProfile("user-1", "default")).thenReturn(profile);

        JwtUserContext ctx = JwtUserContext.builder()
            .userId("user-1").tenantCode("default").roles(List.of()).build();

        List<String> groups = resolver.resolveGroups(ctx);

        assertThat(groups).contains("DOA:L1", "DOA:L2");
        assertThat(groups).contains("DOA_L1", "DOA_L2");
        assertThat(groups).doesNotContain("DOA:L3", "DOA_L3");
    }

    @Test
    void step4_emitsDeptGroup_andDeptDoaCompound() {
        UserProfileDto profile = new UserProfileDto("user-1", "default", 2, "FIN");
        when(adminServiceClient.getUserProfile("user-1", "default")).thenReturn(profile);
        when(adminServiceClient.getTenantCrossDeptThreshold("default")).thenReturn(4);

        JwtUserContext ctx = JwtUserContext.builder()
            .userId("user-1").tenantCode("default").roles(List.of()).build();

        List<String> groups = resolver.resolveGroups(ctx);

        assertThat(groups).contains("DEPT:FIN");
        assertThat(groups).contains("DEPT:FIN::DOA:L1", "DEPT:FIN::DOA:L2");
        assertThat(groups).doesNotContain("DEPT:FIN::DOA:L3");
    }

    @Test
    void step4_highDoaAboveThreshold_emitsAllDepts() {
        UserProfileDto profile = new UserProfileDto("user-1", "default", 4, "FIN");
        when(adminServiceClient.getUserProfile("user-1", "default")).thenReturn(profile);
        when(adminServiceClient.getTenantCrossDeptThreshold("default")).thenReturn(4);
        when(adminServiceClient.getTenantDepartmentCodes("default")).thenReturn(List.of("FIN", "IT", "HR"));

        JwtUserContext ctx = JwtUserContext.builder()
            .userId("user-1").tenantCode("default").roles(List.of()).build();

        List<String> groups = resolver.resolveGroups(ctx);

        assertThat(groups).contains("DEPT:IT::DOA:L1", "DEPT:IT::DOA:L4");
        assertThat(groups).contains("DEPT:HR::DOA:L1", "DEPT:HR::DOA:L4");
    }

    @Test
    void step2_adminRole_emitsAdminAndSuperAdmin() {
        UserProfileDto profile = new UserProfileDto("admin-1", "default", null, null);
        when(adminServiceClient.getUserProfile("admin-1", "default")).thenReturn(profile);

        JwtUserContext ctx = JwtUserContext.builder()
            .userId("admin-1").tenantCode("default").roles(List.of("admin")).build();

        List<String> groups = resolver.resolveGroups(ctx);
        assertThat(groups).contains("ADMIN", "SUPER_ADMIN");
    }

    @Test
    void adminServiceUnavailable_fallsBackToYamlRolesOnly() {
        when(adminServiceClient.getUserProfile("user-1", "default"))
            .thenThrow(new RuntimeException("admin-service unavailable"));

        JwtUserContext ctx = JwtUserContext.builder()
            .userId("user-1").tenantCode("default").roles(List.of("admin")).build();

        List<String> groups = resolver.resolveGroups(ctx);
        assertThat(groups).contains("ADMIN", "SUPER_ADMIN");
        assertThat(groups).doesNotContain("DOA:L1");
    }
}
