package com.werkflow.engine.config;

import com.werkflow.engine.workflow.BpmnGroupValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "werkflow.security.expose-management-endpoints=false",
    "spring.datasource.url=jdbc:postgresql://localhost:5433/werkflow",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.flyway.enabled=false",
    "flowable.database-schema-update=false",
    "flowable.async-executor-activate=false",
    "flowable.check-process-definitions=false",
    "werkflow.vault.token=test-token"
})
class SecurityConfigTest {

    @MockBean BpmnGroupValidator bpmnGroupValidator;
    @Autowired private MockMvc mockMvc;

    @Test
    void actuator_withoutAuth_returns401WhenExposureDisabled() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void swaggerUi_withoutAuth_returns401WhenExposureDisabled() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
            .andExpect(status().isUnauthorized());
    }
}
