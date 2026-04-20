package com.werkflow.engine.fixtures;

import com.werkflow.engine.dto.JwtUserContext;
import org.flowable.engine.ManagementService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Base class for integration tests providing common setup and utilities.
 * Includes Spring Boot test context, Flowable engine services, and test data helpers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected RuntimeService runtimeService;

    @Autowired
    protected TaskService taskService;

    @Autowired
    protected HistoryService historyService;

    @Autowired
    protected RepositoryService repositoryService;

    @Autowired
    protected ManagementService managementService;

    @MockBean
    protected JavaMailSender mailSender;

    @BeforeEach
    public void baseSetUp() {
        // Mock email sender to prevent actual email sending during tests
        MimeMessage mockMessage = new MimeMessage((Session) null);
        when(mailSender.createMimeMessage()).thenReturn(mockMessage);

        // Verify Flowable engine is initialized
        long processDefinitionCount = repositoryService.createProcessDefinitionQuery().count();
        System.out.println("Flowable engine initialized with " + processDefinitionCount + " process definitions");
    }

    @AfterEach
    public void baseTearDown() {
        // Clean up any running process instances
        runtimeService.createProcessInstanceQuery()
                .list()
                .forEach(processInstance ->
                    runtimeService.deleteProcessInstance(processInstance.getId(), "Test cleanup")
                );

        // Note: We don't clean up history to allow post-test verification
        // History will be cleaned up by test database reset
    }

    /**
     * Creates a mock JWT token for testing with specified user details.
     *
     * @param username Username (preferred_username claim)
     * @param email User email
     * @param fullName User full name
     * @param department User department
     * @param groups User groups/teams
     * @param roles Keycloak roles
     * @param doaLevel Delegation of Authority level
     * @return Mock JWT token
     */
    protected Jwt createMockJwt(String username, String email, String fullName,
                                String department, List<String> groups,
                                List<String> roles, Integer doaLevel) {
        Map<String, Object> claims = Map.of(
                "sub", "user-uuid-" + username,
                "preferred_username", username,
                "email", email,
                "name", fullName,
                "department", department,
                "groups", groups,
                "realm_access", Map.of("roles", roles),
                "doa_level", doaLevel
        );

        Map<String, Object> headers = Map.of(
                "alg", "RS256",
                "typ", "JWT"
        );

        return new Jwt(
                "mock-token-" + username,
                Instant.now(),
                Instant.now().plusSeconds(3600),
                headers,
                claims
        );
    }

    /**
     * Creates a JwtUserContext for testing with specified user details.
     *
     * @param username Username
     * @param email User email
     * @param fullName User full name
     * @param department User department
     * @param groups User groups/teams
     * @param roles User roles
     * @param doaLevel Delegation of Authority level
     * @return JwtUserContext
     */
    protected JwtUserContext createUserContext(String username, String email, String fullName,
                                               String department, List<String> groups,
                                               List<String> roles, Integer doaLevel) {
        return JwtUserContext.builder()
                .userId(username)
                .email(email)
                .fullName(fullName)
                .department(department)
                .groups(groups)
                .roles(roles)
                .doaLevel(doaLevel)
                .build();
    }

    /**
     * Waits for asynchronous jobs to complete.
     * Useful when testing async service tasks.
     *
     * @param maxWaitMs Maximum time to wait in milliseconds
     * @throws InterruptedException if interrupted while waiting
     */
    protected void waitForAsyncJobs(long maxWaitMs) throws InterruptedException {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < maxWaitMs) {
            long jobCount = managementService.createJobQuery().count();
            if (jobCount == 0) {
                return;
            }
            Thread.sleep(100);
        }
    }

    /**
     * Retrieves the first active task for a given process instance.
     *
     * @param processInstanceId Process instance ID
     * @return Task ID or null if no active task found
     */
    protected String getActiveTaskId(String processInstanceId) {
        return taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .active()
                .list()
                .stream()
                .findFirst()
                .map(org.flowable.task.api.Task::getId)
                .orElse(null);
    }

    /**
     * Retrieves all active task IDs for a given process instance.
     *
     * @param processInstanceId Process instance ID
     * @return List of task IDs
     */
    protected List<String> getAllActiveTaskIds(String processInstanceId) {
        return taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .active()
                .list()
                .stream()
                .map(org.flowable.task.api.Task::getId)
                .toList();
    }

    /**
     * Checks if a process instance is completed.
     *
     * @param processInstanceId Process instance ID
     * @return true if process is completed, false otherwise
     */
    protected boolean isProcessCompleted(String processInstanceId) {
        return runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .count() == 0;
    }

    /**
     * Retrieves process variable value.
     *
     * @param processInstanceId Process instance ID
     * @param variableName Variable name
     * @return Variable value or null if not found
     */
    protected Object getProcessVariable(String processInstanceId, String variableName) {
        return runtimeService.getVariable(processInstanceId, variableName);
    }

    /**
     * Sets process variable.
     *
     * @param processInstanceId Process instance ID
     * @param variableName Variable name
     * @param value Variable value
     */
    protected void setProcessVariable(String processInstanceId, String variableName, Object value) {
        runtimeService.setVariable(processInstanceId, variableName, value);
    }
}
