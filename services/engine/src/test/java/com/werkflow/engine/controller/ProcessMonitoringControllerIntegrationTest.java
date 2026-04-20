package com.werkflow.engine.controller;

import com.werkflow.engine.dto.*;
import com.werkflow.engine.exception.ProcessNotFoundException;
import com.werkflow.engine.exception.UnauthorizedTaskAccessException;
import com.werkflow.engine.service.ProcessMonitoringService;
import com.werkflow.engine.util.JwtClaimsExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for ProcessMonitoringController
 */
@WebMvcTest(ProcessMonitoringController.class)
class ProcessMonitoringControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProcessMonitoringService processMonitoringService;

    @MockBean
    private JwtClaimsExtractor jwtClaimsExtractor;

    private JwtUserContext userContext;
    private Jwt mockJwt;
    private static final String PROCESS_INSTANCE_ID = "process-123";
    private static final String BUSINESS_KEY = "PR-2025-00042";

    @BeforeEach
    void setUp() {
        userContext = JwtUserContext.builder()
                .userId("john.doe")
                .email("john.doe@example.com")
                .fullName("John Doe")
                .department("Finance")
                .groups(List.of("FINANCE_STAFF"))
                .roles(List.of("USER"))
                .doaLevel(2)
                .build();

        mockJwt = Jwt.withTokenValue("mock-token")
                .header("alg", "RS256")
                .claim("preferred_username", "john.doe")
                .claim("email", "john.doe@example.com")
                .claim("name", "John Doe")
                .build();

        when(jwtClaimsExtractor.extractUserContext(any(Jwt.class))).thenReturn(userContext);
    }

    @Test
    void getProcessInstanceDetails_shouldReturn200_whenProcessExists() throws Exception {
        // Arrange
        ProcessInstanceDTO processInstance = ProcessInstanceDTO.builder()
                .id(PROCESS_INSTANCE_ID)
                .businessKey(BUSINESS_KEY)
                .name("Purchase Requisition Approval")
                .processDefinitionKey("purchase-requisition-approval")
                .status("RUNNING")
                .startTime(Instant.now().minus(2, ChronoUnit.HOURS))
                .initiatorUsername("john.doe")
                .initiatorEmail("john.doe@example.com")
                .activeTaskCount(1)
                .completedTaskCount(2)
                .build();

        when(processMonitoringService.getProcessInstanceDetails(
                eq(PROCESS_INSTANCE_ID), eq(false), any(JwtUserContext.class)))
                .thenReturn(processInstance);

        // Act & Assert
        mockMvc.perform(get("/workflows/processes/{processInstanceId}", PROCESS_INSTANCE_ID)
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(mockJwt)
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(PROCESS_INSTANCE_ID))
                .andExpect(jsonPath("$.businessKey").value(BUSINESS_KEY))
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.initiatorUsername").value("john.doe"))
                .andExpect(jsonPath("$.activeTaskCount").value(1))
                .andExpect(jsonPath("$.completedTaskCount").value(2));
    }

    @Test
    void getProcessInstanceDetails_shouldIncludeVariables_whenRequested() throws Exception {
        // Arrange
        Map<String, Object> variables = Map.of("amount", 5000, "department", "Finance");

        ProcessInstanceDTO processInstance = ProcessInstanceDTO.builder()
                .id(PROCESS_INSTANCE_ID)
                .businessKey(BUSINESS_KEY)
                .name("Purchase Requisition Approval")
                .processDefinitionKey("purchase-requisition-approval")
                .status("RUNNING")
                .startTime(Instant.now())
                .initiatorUsername("john.doe")
                .variables(variables)
                .activeTaskCount(1)
                .completedTaskCount(0)
                .build();

        when(processMonitoringService.getProcessInstanceDetails(
                eq(PROCESS_INSTANCE_ID), eq(true), any(JwtUserContext.class)))
                .thenReturn(processInstance);

        // Act & Assert
        mockMvc.perform(get("/workflows/processes/{processInstanceId}", PROCESS_INSTANCE_ID)
                        .param("includeVariables", "true")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(mockJwt)
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(PROCESS_INSTANCE_ID))
                .andExpect(jsonPath("$.variables").exists())
                .andExpect(jsonPath("$.variables.amount").value(5000))
                .andExpect(jsonPath("$.variables.department").value("Finance"));
    }

    @Test
    void getProcessInstanceDetails_shouldReturn404_whenProcessNotFound() throws Exception {
        // Arrange
        when(processMonitoringService.getProcessInstanceDetails(
                eq(PROCESS_INSTANCE_ID), anyBoolean(), any(JwtUserContext.class)))
                .thenThrow(new ProcessNotFoundException("Process instance not found: " + PROCESS_INSTANCE_ID));

        // Act & Assert
        mockMvc.perform(get("/workflows/processes/{processInstanceId}", PROCESS_INSTANCE_ID)
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(mockJwt)
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    void getProcessInstanceDetails_shouldReturn403_whenUserNotAuthorized() throws Exception {
        // Arrange
        when(processMonitoringService.getProcessInstanceDetails(
                eq(PROCESS_INSTANCE_ID), anyBoolean(), any(JwtUserContext.class)))
                .thenThrow(new UnauthorizedTaskAccessException("You do not have permission to view this process instance"));

        // Act & Assert
        mockMvc.perform(get("/workflows/processes/{processInstanceId}", PROCESS_INSTANCE_ID)
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(mockJwt)
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    void getProcessInstanceTasks_shouldReturn200_withActiveTasks() throws Exception {
        // Arrange
        ProcessTaskHistoryDTO task = ProcessTaskHistoryDTO.builder()
                .taskId("task-1")
                .name("Manager Approval")
                .taskDefinitionKey("managerApproval")
                .status("ACTIVE")
                .assignedTo("jane.smith")
                .outcome("PENDING")
                .createdTime(Instant.now())
                .priority(50)
                .build();

        when(processMonitoringService.getProcessInstanceTasks(
                eq(PROCESS_INSTANCE_ID), eq(true), isNull(), any(JwtUserContext.class)))
                .thenReturn(List.of(task));

        // Act & Assert
        mockMvc.perform(get("/workflows/processes/{processInstanceId}/tasks", PROCESS_INSTANCE_ID)
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(mockJwt)
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].taskId").value("task-1"))
                .andExpect(jsonPath("$[0].name").value("Manager Approval"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[0].outcome").value("PENDING"));
    }

    @Test
    void getProcessInstanceTasks_shouldFilterByStatus() throws Exception {
        // Arrange
        ProcessTaskHistoryDTO task = ProcessTaskHistoryDTO.builder()
                .taskId("task-1")
                .name("Finance Review")
                .taskDefinitionKey("financeReview")
                .status("COMPLETED")
                .assignedTo("john.doe")
                .completedBy("john.doe")
                .outcome("APPROVED")
                .createdTime(Instant.now().minus(1, ChronoUnit.HOURS))
                .completedTime(Instant.now())
                .durationInMinutes(60L)
                .priority(50)
                .build();

        when(processMonitoringService.getProcessInstanceTasks(
                eq(PROCESS_INSTANCE_ID), eq(true), eq("COMPLETED"), any(JwtUserContext.class)))
                .thenReturn(List.of(task));

        // Act & Assert
        mockMvc.perform(get("/workflows/processes/{processInstanceId}/tasks", PROCESS_INSTANCE_ID)
                        .param("status", "COMPLETED")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(mockJwt)
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$[0].outcome").value("APPROVED"))
                .andExpect(jsonPath("$[0].durationInMinutes").value(60));
    }

    @Test
    void getProcessInstanceHistory_shouldReturn200_withPaginatedEvents() throws Exception {
        // Arrange
        ProcessEventHistoryDTO event1 = ProcessEventHistoryDTO.builder()
                .eventType("PROCESS_STARTED")
                .timestamp(Instant.now().minus(2, ChronoUnit.HOURS))
                .userId("john.doe")
                .details("Process started")
                .build();

        ProcessEventHistoryDTO event2 = ProcessEventHistoryDTO.builder()
                .eventType("TASK_COMPLETED")
                .timestamp(Instant.now().minus(1, ChronoUnit.HOURS))
                .userId("john.doe")
                .taskName("Finance Review")
                .details("Task 'Finance Review' completed with outcome APPROVED")
                .build();

        ProcessHistoryResponse historyResponse = ProcessHistoryResponse.builder()
                .events(List.of(event1, event2))
                .totalEvents(2L)
                .page(0)
                .size(50)
                .totalPages(1)
                .build();

        when(processMonitoringService.getProcessInstanceHistory(
                eq(PROCESS_INSTANCE_ID), eq(0), eq(50), any(JwtUserContext.class)))
                .thenReturn(historyResponse);

        // Act & Assert
        mockMvc.perform(get("/workflows/processes/{processInstanceId}/history", PROCESS_INSTANCE_ID)
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(mockJwt)
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events").isArray())
                .andExpect(jsonPath("$.events.length()").value(2))
                .andExpect(jsonPath("$.events[0].eventType").value("PROCESS_STARTED"))
                .andExpect(jsonPath("$.events[1].eventType").value("TASK_COMPLETED"))
                .andExpect(jsonPath("$.totalEvents").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(50))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void getProcessInstanceHistory_shouldSupportPagination() throws Exception {
        // Arrange
        ProcessHistoryResponse historyResponse = ProcessHistoryResponse.builder()
                .events(Collections.emptyList())
                .totalEvents(100L)
                .page(2)
                .size(20)
                .totalPages(5)
                .build();

        when(processMonitoringService.getProcessInstanceHistory(
                eq(PROCESS_INSTANCE_ID), eq(2), eq(20), any(JwtUserContext.class)))
                .thenReturn(historyResponse);

        // Act & Assert
        mockMvc.perform(get("/workflows/processes/{processInstanceId}/history", PROCESS_INSTANCE_ID)
                        .param("page", "2")
                        .param("size", "20")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(mockJwt)
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalPages").value(5))
                .andExpect(jsonPath("$.totalEvents").value(100));
    }

    @Test
    void getProcessByBusinessKey_shouldReturn200_whenProcessExists() throws Exception {
        // Arrange
        ProcessInstanceDTO processInstance = ProcessInstanceDTO.builder()
                .id(PROCESS_INSTANCE_ID)
                .businessKey(BUSINESS_KEY)
                .name("Purchase Requisition Approval")
                .processDefinitionKey("purchase-requisition-approval")
                .status("RUNNING")
                .startTime(Instant.now())
                .initiatorUsername("john.doe")
                .activeTaskCount(1)
                .completedTaskCount(0)
                .build();

        when(processMonitoringService.getProcessByBusinessKey(
                eq(BUSINESS_KEY), any(JwtUserContext.class)))
                .thenReturn(processInstance);

        // Act & Assert
        mockMvc.perform(get("/workflows/processes/by-key/{businessKey}", BUSINESS_KEY)
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(mockJwt)
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(PROCESS_INSTANCE_ID))
                .andExpect(jsonPath("$.businessKey").value(BUSINESS_KEY))
                .andExpect(jsonPath("$.status").value("RUNNING"));
    }

    @Test
    void getProcessByBusinessKey_shouldReturn404_whenProcessNotFound() throws Exception {
        // Arrange
        when(processMonitoringService.getProcessByBusinessKey(
                eq(BUSINESS_KEY), any(JwtUserContext.class)))
                .thenThrow(new ProcessNotFoundException("Process not found with business key: " + BUSINESS_KEY));

        // Act & Assert
        mockMvc.perform(get("/workflows/processes/by-key/{businessKey}", BUSINESS_KEY)
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(mockJwt)
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    void allEndpoints_shouldReturn401_whenNoAuthentication() throws Exception {
        // Test process details endpoint
        mockMvc.perform(get("/workflows/processes/{processInstanceId}", PROCESS_INSTANCE_ID))
                .andExpect(status().isUnauthorized());

        // Test tasks endpoint
        mockMvc.perform(get("/workflows/processes/{processInstanceId}/tasks", PROCESS_INSTANCE_ID))
                .andExpect(status().isUnauthorized());

        // Test history endpoint
        mockMvc.perform(get("/workflows/processes/{processInstanceId}/history", PROCESS_INSTANCE_ID))
                .andExpect(status().isUnauthorized());

        // Test business key endpoint
        mockMvc.perform(get("/workflows/processes/by-key/{businessKey}", BUSINESS_KEY))
                .andExpect(status().isUnauthorized());
    }
}
