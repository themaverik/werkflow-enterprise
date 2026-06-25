package com.werkflow.engine.config;

import com.werkflow.engine.workflow.BpmnGroupValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Uses the shared "test" profile (application-test.yml) for the datasource/schema/vault
// config, exactly like IntegrationTestBase. The only test-specific override is disabling
// management-endpoint exposure, which is what these two assertions exercise.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "werkflow.security.expose-management-endpoints=false"
})
class SecurityConfigTest {

    @MockBean BpmnGroupValidator bpmnGroupValidator;
    @Autowired private MockMvc mockMvc;

    @Test
    void actuatorNonHealth_withoutAuth_returns401WhenExposureDisabled() throws Exception {
        // /actuator/health/** is intentionally permitAll (public liveness/readiness probe).
        // Use a different actuator endpoint that maps to the authenticated() rule.
        mockMvc.perform(get("/actuator/info"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void swaggerUi_withoutAuth_returns401WhenExposureDisabled() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
            .andExpect(status().isUnauthorized());
    }
}
