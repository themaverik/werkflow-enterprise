package com.werkflow.engine.controller;

import com.werkflow.engine.dto.JwtUserContext;
import com.werkflow.engine.dto.TaskListResponse;
import com.werkflow.engine.service.WorkflowTaskService;
import com.werkflow.engine.util.JwtClaimsExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for WorkflowTaskController
 */
@WebMvcTest(WorkflowTaskController.class)
class WorkflowTaskControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WorkflowTaskService workflowTaskService;

    @MockBean
    private JwtClaimsExtractor jwtClaimsExtractor;

    @MockBean
    private JwtDecoder jwtDecoder;

    private JwtUserContext userContext;
    private Jwt jwt;

    @BeforeEach
    void setUp() {
        userContext = JwtUserContext.builder()
                .userId("john.doe")
                .email("john.doe@example.com")
                .fullName("John Doe")
                .department("HR")
                .groups(List.of("HR_STAFF", "HR_MANAGER"))
                .roles(List.of("USER", "MANAGER"))
                .doaLevel(2)
                .build();

        jwt = createMockJwt("john.doe", List.of("HR_STAFF", "HR_MANAGER"));
    }

    @Test
    @WithMockUser
    void getMyTasks_shouldReturn200WithTaskList() throws Exception {
        // Arrange
        TaskListResponse mockResponse = TaskListResponse.builder()
                .content(Collections.emptyList())
                .page(TaskListResponse.PageInfo.builder()
                        .size(20)
                        .number(0)
                        .totalElements(0)
                        .totalPages(0)
                        .build())
                .links(Map.of(
                        "self", "/workflows/tasks/my-tasks?page=0&size=20",
                        "first", "/workflows/tasks/my-tasks?page=0&size=20",
                        "last", "/workflows/tasks/my-tasks?page=0&size=20"
                ))
                .build();

        when(jwtClaimsExtractor.extractUserContext(any(Jwt.class))).thenReturn(userContext);
        when(workflowTaskService.getMyTasks(any(JwtUserContext.class), any())).thenReturn(mockResponse);

        // Act & Assert
        mockMvc.perform(get("/workflows/tasks/my-tasks")
                        .with(jwt().jwt(jwt))
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").exists())
                .andExpect(jsonPath("$.page.size").value(20))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.totalElements").value(0))
                .andExpect(jsonPath("$.links").exists())
                .andExpect(jsonPath("$.links.self").exists());
    }

    @Test
    @WithMockUser
    void getMyTasks_shouldAcceptSearchParameter() throws Exception {
        // Arrange
        TaskListResponse mockResponse = createEmptyTaskListResponse();

        when(jwtClaimsExtractor.extractUserContext(any(Jwt.class))).thenReturn(userContext);
        when(workflowTaskService.getMyTasks(any(JwtUserContext.class), any())).thenReturn(mockResponse);

        // Act & Assert
        mockMvc.perform(get("/workflows/tasks/my-tasks")
                        .with(jwt().jwt(jwt))
                        .param("search", "leave")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @WithMockUser
    void getMyTasks_shouldAcceptPriorityFilter() throws Exception {
        // Arrange
        TaskListResponse mockResponse = createEmptyTaskListResponse();

        when(jwtClaimsExtractor.extractUserContext(any(Jwt.class))).thenReturn(userContext);
        when(workflowTaskService.getMyTasks(any(JwtUserContext.class), any())).thenReturn(mockResponse);

        // Act & Assert
        mockMvc.perform(get("/workflows/tasks/my-tasks")
                        .with(jwt().jwt(jwt))
                        .param("priority", "50")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @WithMockUser
    void getMyTasks_shouldAcceptSortParameter() throws Exception {
        // Arrange
        TaskListResponse mockResponse = createEmptyTaskListResponse();

        when(jwtClaimsExtractor.extractUserContext(any(Jwt.class))).thenReturn(userContext);
        when(workflowTaskService.getMyTasks(any(JwtUserContext.class), any())).thenReturn(mockResponse);

        // Act & Assert
        mockMvc.perform(get("/workflows/tasks/my-tasks")
                        .with(jwt().jwt(jwt))
                        .param("sort", "priority,desc")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void getMyTasks_shouldReturn401WhenNoAuthentication() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/workflows/tasks/my-tasks")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void getGroupTasks_shouldReturn200WithTaskList() throws Exception {
        // Arrange
        TaskListResponse mockResponse = createEmptyTaskListResponse();

        when(jwtClaimsExtractor.extractUserContext(any(Jwt.class))).thenReturn(userContext);
        when(workflowTaskService.getGroupTasks(any(JwtUserContext.class), any())).thenReturn(mockResponse);

        // Act & Assert
        mockMvc.perform(get("/workflows/tasks/group-tasks")
                        .with(jwt().jwt(jwt))
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").exists())
                .andExpect(jsonPath("$.links").exists());
    }

    @Test
    @WithMockUser
    void getGroupTasks_shouldAcceptGroupIdParameter() throws Exception {
        // Arrange
        TaskListResponse mockResponse = createEmptyTaskListResponse();

        when(jwtClaimsExtractor.extractUserContext(any(Jwt.class))).thenReturn(userContext);
        when(workflowTaskService.getGroupTasks(any(JwtUserContext.class), any())).thenReturn(mockResponse);

        // Act & Assert
        mockMvc.perform(get("/workflows/tasks/group-tasks")
                        .with(jwt().jwt(jwt))
                        .param("groupId", "HR_STAFF")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @WithMockUser
    void getGroupTasks_shouldAcceptIncludeAssignedParameter() throws Exception {
        // Arrange
        TaskListResponse mockResponse = createEmptyTaskListResponse();

        when(jwtClaimsExtractor.extractUserContext(any(Jwt.class))).thenReturn(userContext);
        when(workflowTaskService.getGroupTasks(any(JwtUserContext.class), any())).thenReturn(mockResponse);

        // Act & Assert
        mockMvc.perform(get("/workflows/tasks/group-tasks")
                        .with(jwt().jwt(jwt))
                        .param("includeAssigned", "true")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void getGroupTasks_shouldReturn401WhenNoAuthentication() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/workflows/tasks/group-tasks")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void getMyTasks_shouldHandlePagination() throws Exception {
        // Arrange
        TaskListResponse mockResponse = TaskListResponse.builder()
                .content(Collections.emptyList())
                .page(TaskListResponse.PageInfo.builder()
                        .size(10)
                        .number(2)
                        .totalElements(50)
                        .totalPages(5)
                        .build())
                .links(Map.of(
                        "self", "/workflows/tasks/my-tasks?page=2&size=10",
                        "first", "/workflows/tasks/my-tasks?page=0&size=10",
                        "prev", "/workflows/tasks/my-tasks?page=1&size=10",
                        "next", "/workflows/tasks/my-tasks?page=3&size=10",
                        "last", "/workflows/tasks/my-tasks?page=4&size=10"
                ))
                .build();

        when(jwtClaimsExtractor.extractUserContext(any(Jwt.class))).thenReturn(userContext);
        when(workflowTaskService.getMyTasks(any(JwtUserContext.class), any())).thenReturn(mockResponse);

        // Act & Assert
        mockMvc.perform(get("/workflows/tasks/my-tasks")
                        .with(jwt().jwt(jwt))
                        .param("page", "2")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.number").value(2))
                .andExpect(jsonPath("$.page.size").value(10))
                .andExpect(jsonPath("$.page.totalPages").value(5))
                .andExpect(jsonPath("$.links.prev").exists())
                .andExpect(jsonPath("$.links.next").exists());
    }

    @Test
    @WithMockUser
    void getMyTasks_shouldAcceptProcessDefinitionKeyFilter() throws Exception {
        // Arrange
        TaskListResponse mockResponse = createEmptyTaskListResponse();

        when(jwtClaimsExtractor.extractUserContext(any(Jwt.class))).thenReturn(userContext);
        when(workflowTaskService.getMyTasks(any(JwtUserContext.class), any())).thenReturn(mockResponse);

        // Act & Assert
        mockMvc.perform(get("/workflows/tasks/my-tasks")
                        .with(jwt().jwt(jwt))
                        .param("processDefinitionKey", "leave-request")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    // Helper methods

    private Jwt createMockJwt(String username, List<String> groups) {
        Map<String, Object> claims = Map.of(
                "sub", "user-uuid-123",
                "preferred_username", username,
                "email", username + "@example.com",
                "name", "John Doe",
                "department", "HR",
                "groups", groups,
                "realm_access", Map.of("roles", List.of("USER", "MANAGER")),
                "doa_level", 2
        );

        Map<String, Object> headers = Map.of(
                "alg", "RS256",
                "typ", "JWT"
        );

        return new Jwt(
                "mock-token",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                headers,
                claims
        );
    }

    private TaskListResponse createEmptyTaskListResponse() {
        return TaskListResponse.builder()
                .content(Collections.emptyList())
                .page(TaskListResponse.PageInfo.builder()
                        .size(20)
                        .number(0)
                        .totalElements(0)
                        .totalPages(0)
                        .build())
                .links(Map.of(
                        "self", "/workflows/tasks/my-tasks?page=0&size=20",
                        "first", "/workflows/tasks/my-tasks?page=0&size=20",
                        "last", "/workflows/tasks/my-tasks?page=0&size=20"
                ))
                .build();
    }
}
