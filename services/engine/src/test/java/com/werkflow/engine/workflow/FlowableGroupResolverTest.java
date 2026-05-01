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
        when(adminServiceClient.getRoleMappings("default")).thenReturn(Map.of());
    }

    @Test
    void step2_adminRole_emitsAdminAndSuperAdmin() {
        when(adminServiceClient.getUserProfile("admin-1", "default"))
            .thenReturn(new UserProfileDto("admin-1", "default", null, null));

        JwtUserContext ctx = JwtUserContext.builder()
            .userId("admin-1").tenantCode("default").roles(List.of("admin")).build();

        List<String> groups = resolver.resolveGroups(ctx);

        assertThat(groups).contains("ADMIN", "SUPER_ADMIN");
    }

    @Test
    void step2_dbRoleMappings_mergedWithYaml() {
        when(adminServiceClient.getRoleMappings("default"))
            .thenReturn(Map.of("finance_approver", List.of("DOA:L2")));
        when(adminServiceClient.getUserProfile("user-1", "default"))
            .thenReturn(new UserProfileDto("user-1", "default", null, "FIN"));

        JwtUserContext ctx = JwtUserContext.builder()
            .userId("user-1").tenantCode("default").roles(List.of("finance_approver")).build();

        List<String> groups = resolver.resolveGroups(ctx);

        assertThat(groups).contains("DOA:L2");
        assertThat(groups).doesNotContain("DOA_L1", "DOA_L2"); // old cumulative format removed
    }

    @Test
    void step3_emitsDeptGroup_fromErpProfile() {
        when(adminServiceClient.getUserProfile("user-1", "default"))
            .thenReturn(new UserProfileDto("user-1", "default", null, "FIN"));

        JwtUserContext ctx = JwtUserContext.builder()
            .userId("user-1").tenantCode("default").roles(List.of()).build();

        List<String> groups = resolver.resolveGroups(ctx);

        assertThat(groups).contains("DEPT:FIN");
        assertThat(groups).doesNotContain("DEPT:FIN::DOA:L1"); // compound groups removed
    }

    @Test
    void noDeptCode_emitsNoDeptGroup() {
        when(adminServiceClient.getUserProfile("user-1", "default"))
            .thenReturn(new UserProfileDto("user-1", "default", null, null));

        JwtUserContext ctx = JwtUserContext.builder()
            .userId("user-1").tenantCode("default").roles(List.of()).build();

        List<String> groups = resolver.resolveGroups(ctx);

        assertThat(groups).noneMatch(g -> g.startsWith("DEPT:"));
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

    @Test
    void dbMappingsFetchFails_gracefullyFallsBackToYaml() {
        when(adminServiceClient.getRoleMappings("default"))
            .thenThrow(new RuntimeException("DB unavailable"));
        when(adminServiceClient.getUserProfile("user-1", "default"))
            .thenReturn(new UserProfileDto("user-1", "default", null, "IT"));

        JwtUserContext ctx = JwtUserContext.builder()
            .userId("user-1").tenantCode("default").roles(List.of("admin")).build();

        List<String> groups = resolver.resolveGroups(ctx);

        assertThat(groups).contains("ADMIN", "SUPER_ADMIN");
        assertThat(groups).contains("DEPT:IT");
    }
}
