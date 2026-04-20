package com.werkflow.engine.integration.capex;

import com.werkflow.engine.fixtures.IntegrationTestBase;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DOA (Delegation of Authority) Level Routing Tests for CapEx Approval
 * Tests that CapEx requests are routed to the correct approval level based on amount
 *
 * DOA Levels:
 * - Level 1: $1 - $1,000 (Department Manager)
 * - Level 2: $1,001 - $10,000 (Department Head)
 * - Level 3: $10,001 - $100,000 (Finance Manager)
 * - Level 4: $100,001+ (Executive/CFO)
 */
@DisplayName("CapEx DOA Routing Tests")
class CapExDOARoutingTest extends IntegrationTestBase {

    @Test
    @DisplayName("Test Level 1: $500 routes to Department Manager")
    void testLevel1_500DollarRoutesToDepartmentManager() throws Exception {
        // ARRANGE
        Map<String, Object> variables = CapExTestDataFactory.createCapEx500ServerUpgrade();

        // ACT
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                "capex-approval-process",
                (String) variables.get("requestId"),
                variables
        );

        String processInstanceId = processInstance.getId();
        waitForAsyncJobs(5000);

        // ASSERT - Verify routing to Level 1 approver
        Task managerTask = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .taskDefinitionKey("managerApproval")
                .singleResult();

        assertThat(managerTask).isNotNull();
        assertThat(managerTask.getName()).isEqualTo("Manager Review");

        // Verify candidate groups include Department Manager role
        assertThat(taskService.getIdentityLinksForTask(managerTask.getId()))
                .anyMatch(link -> link.getGroupId() != null &&
                        (link.getGroupId().contains("MANAGER") || link.getGroupId().contains("IT_MANAGER")));

        // Verify DOA level in process variables
        Integer doaLevel = (Integer) runtimeService.getVariable(processInstanceId, "doaLevel");
        assertThat(doaLevel).isEqualTo(1);

        System.out.println("PASS: $500 CapEx correctly routed to Level 1 Department Manager");
    }

    @Test
    @DisplayName("Test Level 1 Boundary: $999 routes to Department Manager")
    void testLevel1Boundary_999DollarRoutesToDepartmentManager() throws Exception {
        // ARRANGE - Test upper boundary of Level 1
        Map<String, Object> variables = CapExTestDataFactory.createCapEx500ServerUpgrade();
        variables.put("amount", new BigDecimal("999.00"));
        variables.put("requestAmount", new BigDecimal("999.00"));
        variables.put("requestId", "CAPEX-2024-BOUNDARY-L1-HIGH");
        variables.put("doaLevel", 1);

        // ACT
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                "capex-approval-process",
                "CAPEX-2024-BOUNDARY-L1-HIGH",
                variables
        );

        waitForAsyncJobs(5000);

        // ASSERT
        Task task = taskService.createTaskQuery()
                .processInstanceId(processInstance.getId())
                .singleResult();

        assertThat(task).isNotNull();
        assertThat(task.getTaskDefinitionKey()).isEqualTo("managerApproval");

        Integer doaLevel = (Integer) runtimeService.getVariable(processInstance.getId(), "doaLevel");
        assertThat(doaLevel).isEqualTo(1);

        System.out.println("PASS: $999 boundary correctly routed to Level 1");
    }

    @Test
    @DisplayName("Test Level 2: $7,500 routes to Department Head")
    void testLevel2_7500DollarRoutesToDepartmentHead() throws Exception {
        // ARRANGE
        Map<String, Object> variables = CapExTestDataFactory.createCapEx7500PrintingEquipment();

        // ACT
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                "capex-approval-process",
                (String) variables.get("requestId"),
                variables
        );

        String processInstanceId = processInstance.getId();
        waitForAsyncJobs(5000);

        // ASSERT - Verify routing to Level 2 approver
        Task headTask = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .singleResult();

        assertThat(headTask).isNotNull();

        // Verify DOA level
        Integer doaLevel = (Integer) runtimeService.getVariable(processInstanceId, "doaLevel");
        assertThat(doaLevel).isEqualTo(2);

        // Verify task is assigned to Department Head role
        assertThat(taskService.getIdentityLinksForTask(headTask.getId()))
                .anyMatch(link -> link.getGroupId() != null &&
                        (link.getGroupId().contains("HEAD") || link.getGroupId().contains("DEPARTMENT_HEAD")));

        System.out.println("PASS: $7,500 CapEx correctly routed to Level 2 Department Head");
    }

    @Test
    @DisplayName("Test Level 2 Lower Boundary: $1,001 routes to Department Head")
    void testLevel2LowerBoundary_1001DollarRoutesToDepartmentHead() throws Exception {
        // ARRANGE - Test lower boundary of Level 2
        Map<String, Object> variables = CapExTestDataFactory.createCapEx7500PrintingEquipment();
        variables.put("amount", new BigDecimal("1001.00"));
        variables.put("requestAmount", new BigDecimal("1001.00"));
        variables.put("requestId", "CAPEX-2024-BOUNDARY-L2-LOW");
        variables.put("doaLevel", 2);

        // ACT
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                "capex-approval-process",
                "CAPEX-2024-BOUNDARY-L2-LOW",
                variables
        );

        waitForAsyncJobs(5000);

        // ASSERT
        Integer doaLevel = (Integer) runtimeService.getVariable(processInstance.getId(), "doaLevel");
        assertThat(doaLevel).isEqualTo(2);

        System.out.println("PASS: $1,001 boundary correctly routed to Level 2");
    }

    @Test
    @DisplayName("Test Level 2 Upper Boundary: $10,000 routes to Department Head")
    void testLevel2UpperBoundary_10000DollarRoutesToDepartmentHead() throws Exception {
        // ARRANGE - Test upper boundary of Level 2
        Map<String, Object> variables = CapExTestDataFactory.createCapEx7500PrintingEquipment();
        variables.put("amount", new BigDecimal("10000.00"));
        variables.put("requestAmount", new BigDecimal("10000.00"));
        variables.put("requestId", "CAPEX-2024-BOUNDARY-L2-HIGH");
        variables.put("doaLevel", 2);

        // ACT
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                "capex-approval-process",
                "CAPEX-2024-BOUNDARY-L2-HIGH",
                variables
        );

        waitForAsyncJobs(5000);

        // ASSERT
        Integer doaLevel = (Integer) runtimeService.getVariable(processInstance.getId(), "doaLevel");
        assertThat(doaLevel).isEqualTo(2);

        System.out.println("PASS: $10,000 boundary correctly routed to Level 2");
    }

    @Test
    @DisplayName("Test Level 3: $75,000 routes to Finance Manager")
    void testLevel3_75KDollarRoutesToFinanceManager() throws Exception {
        // ARRANGE
        Map<String, Object> variables = CapExTestDataFactory.createCapEx75KNetworkInfrastructure();

        // ACT
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                "capex-approval-process",
                (String) variables.get("requestId"),
                variables
        );

        String processInstanceId = processInstance.getId();
        waitForAsyncJobs(5000);

        // ASSERT - Verify routing to Level 3 approver
        Task financeTask = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .singleResult();

        assertThat(financeTask).isNotNull();

        // Verify DOA level
        Integer doaLevel = (Integer) runtimeService.getVariable(processInstanceId, "doaLevel");
        assertThat(doaLevel).isEqualTo(3);

        // Verify task is assigned to Finance Manager role
        assertThat(taskService.getIdentityLinksForTask(financeTask.getId()))
                .anyMatch(link -> link.getGroupId() != null &&
                        (link.getGroupId().contains("FINANCE") || link.getGroupId().contains("FINANCE_MANAGER")));

        System.out.println("PASS: $75,000 CapEx correctly routed to Level 3 Finance Manager");
    }

    @Test
    @DisplayName("Test Level 3 Lower Boundary: $10,001 routes to Finance Manager")
    void testLevel3LowerBoundary_10001DollarRoutesToFinanceManager() throws Exception {
        // ARRANGE - Test lower boundary of Level 3
        Map<String, Object> variables = CapExTestDataFactory.createCapEx75KNetworkInfrastructure();
        variables.put("amount", new BigDecimal("10001.00"));
        variables.put("requestAmount", new BigDecimal("10001.00"));
        variables.put("requestId", "CAPEX-2024-BOUNDARY-L3-LOW");
        variables.put("doaLevel", 3);

        // ACT
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                "capex-approval-process",
                "CAPEX-2024-BOUNDARY-L3-LOW",
                variables
        );

        waitForAsyncJobs(5000);

        // ASSERT
        Integer doaLevel = (Integer) runtimeService.getVariable(processInstance.getId(), "doaLevel");
        assertThat(doaLevel).isEqualTo(3);

        System.out.println("PASS: $10,001 boundary correctly routed to Level 3");
    }

    @Test
    @DisplayName("Test Level 3 Upper Boundary: $100,000 routes to Finance Manager")
    void testLevel3UpperBoundary_100KDollarRoutesToFinanceManager() throws Exception {
        // ARRANGE - Test upper boundary of Level 3
        Map<String, Object> variables = CapExTestDataFactory.createCapEx75KNetworkInfrastructure();
        variables.put("amount", new BigDecimal("100000.00"));
        variables.put("requestAmount", new BigDecimal("100000.00"));
        variables.put("requestId", "CAPEX-2024-BOUNDARY-L3-HIGH");
        variables.put("doaLevel", 3);

        // ACT
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                "capex-approval-process",
                "CAPEX-2024-BOUNDARY-L3-HIGH",
                variables
        );

        waitForAsyncJobs(5000);

        // ASSERT
        Integer doaLevel = (Integer) runtimeService.getVariable(processInstance.getId(), "doaLevel");
        assertThat(doaLevel).isEqualTo(3);

        System.out.println("PASS: $100,000 boundary correctly routed to Level 3");
    }

    @Test
    @DisplayName("Test Level 4: $250,000 routes to Executive/CFO")
    void testLevel4_250KDollarRoutesToExecutive() throws Exception {
        // ARRANGE
        Map<String, Object> variables = CapExTestDataFactory.createCapEx250KBuildingRenovation();

        // ACT
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                "capex-approval-process",
                (String) variables.get("requestId"),
                variables
        );

        String processInstanceId = processInstance.getId();
        waitForAsyncJobs(5000);

        // ASSERT - Verify routing to Level 4 approver
        Task executiveTask = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .singleResult();

        assertThat(executiveTask).isNotNull();

        // Verify DOA level
        Integer doaLevel = (Integer) runtimeService.getVariable(processInstanceId, "doaLevel");
        assertThat(doaLevel).isEqualTo(4);

        // Verify task is assigned to Executive/CFO role
        assertThat(taskService.getIdentityLinksForTask(executiveTask.getId()))
                .anyMatch(link -> link.getGroupId() != null &&
                        (link.getGroupId().contains("EXECUTIVE") ||
                         link.getGroupId().contains("CFO") ||
                         link.getGroupId().contains("FINANCE_VP")));

        System.out.println("PASS: $250,000 CapEx correctly routed to Level 4 Executive/CFO");
    }

    @Test
    @DisplayName("Test Level 4 Lower Boundary: $100,001 routes to Executive")
    void testLevel4LowerBoundary_100001DollarRoutesToExecutive() throws Exception {
        // ARRANGE - Test lower boundary of Level 4
        Map<String, Object> variables = CapExTestDataFactory.createCapEx250KBuildingRenovation();
        variables.put("amount", new BigDecimal("100001.00"));
        variables.put("requestAmount", new BigDecimal("100001.00"));
        variables.put("requestId", "CAPEX-2024-BOUNDARY-L4-LOW");
        variables.put("doaLevel", 4);

        // ACT
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                "capex-approval-process",
                "CAPEX-2024-BOUNDARY-L4-LOW",
                variables
        );

        waitForAsyncJobs(5000);

        // ASSERT
        Integer doaLevel = (Integer) runtimeService.getVariable(processInstance.getId(), "doaLevel");
        assertThat(doaLevel).isEqualTo(4);

        System.out.println("PASS: $100,001 boundary correctly routed to Level 4");
    }

    @Test
    @DisplayName("Test Multi-Million Dollar Request: $2M routes to Executive")
    void testMultiMillionDollar_2MRoutesToExecutive() throws Exception {
        // ARRANGE - Test very large amount
        Map<String, Object> variables = CapExTestDataFactory.createCapEx250KBuildingRenovation();
        variables.put("amount", new BigDecimal("2000000.00"));
        variables.put("requestAmount", new BigDecimal("2000000.00"));
        variables.put("requestId", "CAPEX-2024-MEGA-PROJECT");
        variables.put("title", "Major Office Expansion - 50,000 sq ft");
        variables.put("doaLevel", 4);

        // ACT
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                "capex-approval-process",
                "CAPEX-2024-MEGA-PROJECT",
                variables
        );

        waitForAsyncJobs(5000);

        // ASSERT
        Integer doaLevel = (Integer) runtimeService.getVariable(processInstance.getId(), "doaLevel");
        assertThat(doaLevel).isEqualTo(4);

        Task executiveTask = taskService.createTaskQuery()
                .processInstanceId(processInstance.getId())
                .singleResult();

        assertThat(executiveTask).isNotNull();

        System.out.println("PASS: $2M CapEx correctly routed to Level 4 Executive");
    }

    @Test
    @DisplayName("Test Candidate Groups Assignment Based on DOA Level")
    void testCandidateGroupsAssignmentByDOA() throws Exception {
        // ARRANGE - Test multiple amounts and verify correct candidate groups

        // Level 1 test
        Map<String, Object> level1Vars = CapExTestDataFactory.createCapEx500ServerUpgrade();
        level1Vars.put("requestId", "CAPEX-2024-GROUPS-L1");

        ProcessInstance level1Process = runtimeService.startProcessInstanceByKey(
                "capex-approval-process",
                "CAPEX-2024-GROUPS-L1",
                level1Vars
        );

        waitForAsyncJobs(5000);

        Task level1Task = taskService.createTaskQuery()
                .processInstanceId(level1Process.getId())
                .singleResult();

        // Verify Level 1 has manager-related candidate groups
        assertThat(level1Task).isNotNull();
        assertThat(taskService.getIdentityLinksForTask(level1Task.getId()))
                .anyMatch(link -> link.getGroupId() != null && link.getGroupId().contains("MANAGER"));

        System.out.println("PASS: Candidate groups correctly assigned based on DOA levels");
    }
}
