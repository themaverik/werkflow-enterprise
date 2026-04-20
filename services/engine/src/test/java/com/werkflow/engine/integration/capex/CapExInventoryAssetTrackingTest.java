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
 * Inventory Asset Tracking Tests for CapEx Workflow
 * Tests asset reception, tagging, depreciation, and capitalization
 */
@DisplayName("CapEx Inventory Asset Tracking Tests")
class CapExInventoryAssetTrackingTest extends IntegrationTestBase {

    @Test
    @DisplayName("Test Asset Reception - Single Item")
    void testAssetReceptionSingleItem() throws Exception {
        // ARRANGE - Approved CapEx request
        Map<String, Object> variables = CapExTestDataFactory.createCapEx500ServerUpgrade();

        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                "capex-approval-process",
                (String) variables.get("requestId"),
                variables
        );

        waitForAsyncJobs(5000);

        // Approve request
        Task approvalTask = taskService.createTaskQuery()
                .processInstanceId(processInstance.getId())
                .singleResult();

        String managerId = CapExTestDataFactory.createITDepartmentManager().getUserId();
        taskService.claim(approvalTask.getId(), managerId);
        taskService.complete(approvalTask.getId(),
                CapExTestDataFactory.createApprovalDecision(managerId, "Sarah IT Manager", "Approved"));

        waitForAsyncJobs(5000);

        // ACT - Receive asset in Inventory
        Map<String, Object> assetVars = CapExTestDataFactory.createAssetReception(
                "ASSET-2024-001",
                "Dell PowerEdge R740 Server",
                new BigDecimal("500.00"),
                1
        );
        runtimeService.setVariables(processInstance.getId(), assetVars);

        // ASSERT - Verify asset reception
        Boolean assetReceived = (Boolean) runtimeService.getVariable(processInstance.getId(), "assetReceived");
        String assetTag = (String) runtimeService.getVariable(processInstance.getId(), "assetTag");
        BigDecimal assetValue = (BigDecimal) runtimeService.getVariable(processInstance.getId(), "assetValue");

        assertThat(assetReceived).isTrue();
        assertThat(assetTag).isEqualTo("ASSET-2024-001");
        assertThat(assetValue).isEqualByComparingTo(new BigDecimal("500.00"));

        System.out.println("PASS: Single asset reception successful");
    }

    @Test
    @DisplayName("Test Asset Tagging and Categorization")
    void testAssetTaggingAndCategorization() throws Exception {
        // ARRANGE
        Map<String, Object> variables = CapExTestDataFactory.createCapEx7500PrintingEquipment();

        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                "capex-approval-process",
                (String) variables.get("requestId"),
                variables
        );

        waitForAsyncJobs(5000);

        // ACT - Receive and tag asset
        Map<String, Object> assetVars = CapExTestDataFactory.createAssetReception(
                "ASSET-2024-002",
                "Industrial Printer HP Z9+",
                new BigDecimal("7500.00"),
                1
        );

        // Add categorization
        assetVars.put("assetCategory", "OFFICE_EQUIPMENT");
        assetVars.put("assetSubCategory", "PRINTING_EQUIPMENT");
        assetVars.put("assetLocation", "Marketing Department - Floor 3");
        assetVars.put("responsibleDepartment", "Marketing");

        runtimeService.setVariables(processInstance.getId(), assetVars);

        // ASSERT - Verify categorization
        String assetCategory = (String) runtimeService.getVariable(processInstance.getId(), "assetCategory");
        String assetSubCategory = (String) runtimeService.getVariable(processInstance.getId(), "assetSubCategory");
        String assetLocation = (String) runtimeService.getVariable(processInstance.getId(), "assetLocation");

        assertThat(assetCategory).isEqualTo("OFFICE_EQUIPMENT");
        assertThat(assetSubCategory).isEqualTo("PRINTING_EQUIPMENT");
        assertThat(assetLocation).contains("Marketing Department");

        System.out.println("PASS: Asset tagging and categorization successful");
    }

    @Test
    @DisplayName("Test Depreciation Schedule Creation")
    void testDepreciationScheduleCreation() throws Exception {
        // ARRANGE
        Map<String, Object> variables = CapExTestDataFactory.createCapEx7500PrintingEquipment();

        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                "capex-approval-process",
                (String) variables.get("requestId"),
                variables
        );

        waitForAsyncJobs(5000);

        // Receive asset
        Map<String, Object> assetVars = CapExTestDataFactory.createAssetReception(
                "ASSET-2024-002",
                "Industrial Printer",
                new BigDecimal("7500.00"),
                1
        );
        runtimeService.setVariables(processInstance.getId(), assetVars);

        // ACT - Create depreciation schedule
        Map<String, Object> depreciationVars = CapExTestDataFactory.createDepreciationSchedule(
                5, // 5 years useful life
                "STRAIGHT_LINE",
                new BigDecimal("1500.00") // $7,500 / 5 years
        );
        runtimeService.setVariables(processInstance.getId(), depreciationVars);

        // ASSERT - Verify depreciation schedule
        Integer usefulLife = (Integer) runtimeService.getVariable(processInstance.getId(), "usefulLifeYears");
        String depreciationMethod = (String) runtimeService.getVariable(processInstance.getId(), "depreciationMethod");
        BigDecimal annualDepreciation = (BigDecimal) runtimeService.getVariable(processInstance.getId(), "annualDepreciation");

        assertThat(usefulLife).isEqualTo(5);
        assertThat(depreciationMethod).isEqualTo("STRAIGHT_LINE");
        assertThat(annualDepreciation).isEqualByComparingTo(new BigDecimal("1500.00"));

        System.out.println("PASS: Depreciation schedule created successfully");
    }

    @Test
    @DisplayName("Test Phased Asset Delivery - Multiple Deliveries")
    void testPhasedAssetDelivery() throws Exception {
        // ARRANGE - Large project with phased delivery
        Map<String, Object> variables = CapExTestDataFactory.createCapEx75KNetworkInfrastructure();

        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                "capex-approval-process",
                (String) variables.get("requestId"),
                variables
        );

        waitForAsyncJobs(5000);

        // ACT - Phase 1 delivery (Switches)
        Map<String, Object> phase1Vars = CapExTestDataFactory.createPhasedDelivery(
                1,
                3,
                new BigDecimal("25000.00")
        );
        runtimeService.setVariables(processInstance.getId(), phase1Vars);

        Map<String, Object> phase1Asset = CapExTestDataFactory.createAssetReception(
                "ASSET-2024-003-P1",
                "Network Switches - Cisco Catalyst",
                new BigDecimal("25000.00"),
                10
        );
        runtimeService.setVariables(processInstance.getId(), phase1Asset);

        // Phase 2 delivery (Routers)
        Map<String, Object> phase2Vars = CapExTestDataFactory.createPhasedDelivery(
                2,
                3,
                new BigDecimal("30000.00")
        );
        runtimeService.setVariables(processInstance.getId(), phase2Vars);

        Map<String, Object> phase2Asset = CapExTestDataFactory.createAssetReception(
                "ASSET-2024-003-P2",
                "Network Routers - Cisco ASR",
                new BigDecimal("30000.00"),
                5
        );
        runtimeService.setVariables(processInstance.getId(), phase2Asset);

        // Phase 3 delivery (Firewall)
        Map<String, Object> phase3Vars = CapExTestDataFactory.createPhasedDelivery(
                3,
                3,
                new BigDecimal("20000.00")
        );
        runtimeService.setVariables(processInstance.getId(), phase3Vars);

        Map<String, Object> phase3Asset = CapExTestDataFactory.createAssetReception(
                "ASSET-2024-003-P3",
                "Enterprise Firewall - Palo Alto",
                new BigDecimal("20000.00"),
                2
        );
        runtimeService.setVariables(processInstance.getId(), phase3Asset);

        // ASSERT - Verify all phases tracked
        Boolean phasedDelivery = (Boolean) runtimeService.getVariable(processInstance.getId(), "phasedDelivery");
        Integer currentPhase = (Integer) runtimeService.getVariable(processInstance.getId(), "currentPhase");
        Integer totalPhases = (Integer) runtimeService.getVariable(processInstance.getId(), "totalPhases");

        assertThat(phasedDelivery).isTrue();
        assertThat(currentPhase).isEqualTo(3);
        assertThat(totalPhases).isEqualTo(3);

        System.out.println("PASS: Phased asset delivery tracked successfully");
    }

    @Test
    @DisplayName("Test Asset-to-Ledger Mapping (Capitalization)")
    void testAssetToLedgerMapping() throws Exception {
        // ARRANGE
        Map<String, Object> variables = CapExTestDataFactory.createCapEx7500PrintingEquipment();

        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                "capex-approval-process",
                (String) variables.get("requestId"),
                variables
        );

        waitForAsyncJobs(5000);

        // Receive asset
        Map<String, Object> assetVars = CapExTestDataFactory.createAssetReception(
                "ASSET-2024-002",
                "Industrial Printer",
                new BigDecimal("7500.00"),
                1
        );
        runtimeService.setVariables(processInstance.getId(), assetVars);

        // ACT - Capitalize asset to ledger
        Map<String, Object> capitalizationVars = CapExTestDataFactory.createAssetCapitalization(
                "1520-MARKETING-CAPEX",
                new BigDecimal("7500.00"),
                java.time.LocalDate.now()
        );
        runtimeService.setVariables(processInstance.getId(), capitalizationVars);

        // Use mock Fixed Assets system
        String faRecordId = CapExTestFixtures.MockFixedAssetsSystem.capitalizeAsset(
                "ASSET-2024-002",
                new BigDecimal("7500.00"),
                "1520-MARKETING-CAPEX"
        );
        runtimeService.setVariable(processInstance.getId(), "fixedAssetRecordId", faRecordId);

        // ASSERT - Verify capitalization
        Boolean capitalized = (Boolean) runtimeService.getVariable(processInstance.getId(), "capitalized");
        String ledgerAccountCode = (String) runtimeService.getVariable(processInstance.getId(), "ledgerAccountCode");
        BigDecimal capitalizedAmount = (BigDecimal) runtimeService.getVariable(processInstance.getId(), "capitalizedAmount");
        String fixedAssetRecordId = (String) runtimeService.getVariable(processInstance.getId(), "fixedAssetRecordId");

        assertThat(capitalized).isTrue();
        assertThat(ledgerAccountCode).isEqualTo("1520-MARKETING-CAPEX");
        assertThat(capitalizedAmount).isEqualByComparingTo(new BigDecimal("7500.00"));
        assertThat(fixedAssetRecordId).startsWith("FA-ASSET-2024-002");

        System.out.println("PASS: Asset capitalized to ledger successfully");
    }

    @Test
    @DisplayName("Test Quantity Verification During Reception")
    void testQuantityVerificationDuringReception() throws Exception {
        // ARRANGE
        Map<String, Object> variables = CapExTestDataFactory.createCapEx75KNetworkInfrastructure();

        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                "capex-approval-process",
                (String) variables.get("requestId"),
                variables
        );

        waitForAsyncJobs(5000);

        // ACT - Receive asset with quantity verification
        Map<String, Object> assetVars = CapExTestDataFactory.createAssetReception(
                "ASSET-2024-003-SWITCHES",
                "Network Switches",
                new BigDecimal("25000.00"),
                10 // Expected quantity
        );

        // Add quantity verification details
        assetVars.put("expectedQuantity", 10);
        assetVars.put("receivedQuantity", 10);
        assetVars.put("quantityVerified", true);
        assetVars.put("verifiedBy", "jane.inventory");

        runtimeService.setVariables(processInstance.getId(), assetVars);

        // ASSERT - Verify quantity tracking
        Integer expectedQty = (Integer) runtimeService.getVariable(processInstance.getId(), "expectedQuantity");
        Integer receivedQty = (Integer) runtimeService.getVariable(processInstance.getId(), "receivedQuantity");
        Boolean quantityVerified = (Boolean) runtimeService.getVariable(processInstance.getId(), "quantityVerified");

        assertThat(expectedQty).isEqualTo(10);
        assertThat(receivedQty).isEqualTo(10);
        assertThat(quantityVerified).isTrue();

        System.out.println("PASS: Quantity verification successful");
    }

    @Test
    @DisplayName("Test Inventory Notification to Finance After Reception")
    void testInventoryNotificationToFinance() throws Exception {
        // ARRANGE
        Map<String, Object> variables = CapExTestDataFactory.createCapEx7500PrintingEquipment();

        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                "capex-approval-process",
                (String) variables.get("requestId"),
                variables
        );

        waitForAsyncJobs(5000);

        // Receive asset
        Map<String, Object> assetVars = CapExTestDataFactory.createAssetReception(
                "ASSET-2024-002",
                "Industrial Printer",
                new BigDecimal("7500.00"),
                1
        );
        runtimeService.setVariables(processInstance.getId(), assetVars);

        // ACT - Send notification to Finance for capitalization
        Map<String, Object> notificationVars = CapExTestDataFactory.createNotificationTracking(
                "ASSET_RECEIVED_CAPITALIZE",
                "lisa.finance.manager@werkflow.com",
                "Asset Received - Ready for Capitalization"
        );
        runtimeService.setVariables(processInstance.getId(), notificationVars);

        // ASSERT - Verify notification sent to Finance
        Boolean notificationSent = (Boolean) runtimeService.getVariable(processInstance.getId(), "notificationSent");
        String notificationType = (String) runtimeService.getVariable(processInstance.getId(), "notificationType");
        String recipientEmail = (String) runtimeService.getVariable(processInstance.getId(), "recipientEmail");

        assertThat(notificationSent).isTrue();
        assertThat(notificationType).isEqualTo("ASSET_RECEIVED_CAPITALIZE");
        assertThat(recipientEmail).isEqualTo("lisa.finance.manager@werkflow.com");

        System.out.println("PASS: Inventory notification to Finance sent successfully");
    }
}
