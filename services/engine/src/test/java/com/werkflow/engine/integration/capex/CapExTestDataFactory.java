package com.werkflow.engine.integration.capex;

import com.werkflow.engine.dto.JwtUserContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Test Data Factory for CapEx Integration Tests
 * Provides comprehensive test data for all 4 CapEx scenarios and cross-department workflows
 */
public class CapExTestDataFactory {

    // ==================== Test Users by Department ====================

    /**
     * IT Department Employee (Requester)
     * Use case: Submits $500 server upgrade request
     */
    public static JwtUserContext createITEmployee() {
        return JwtUserContext.builder()
                .userId("john.it")
                .email("john.it@werkflow.com")
                .fullName("John IT Employee")
                .department("IT")
                .groups(List.of("IT_STAFF"))
                .roles(List.of("USER"))
                .doaLevel(0)
                .build();
    }

    /**
     * IT Department Manager (Level 1 - $1K DOA)
     * Use case: Approves $500 IT requests
     */
    public static JwtUserContext createITDepartmentManager() {
        return JwtUserContext.builder()
                .userId("sarah.it.manager")
                .email("sarah.it.manager@werkflow.com")
                .fullName("Sarah IT Manager")
                .department("IT")
                .groups(List.of("IT_STAFF", "IT_MANAGER"))
                .roles(List.of("USER", "MANAGER"))
                .doaLevel(1)
                .build();
    }

    /**
     * Marketing Department Employee (Requester)
     * Use case: Submits $7,500 printing equipment request
     */
    public static JwtUserContext createMarketingEmployee() {
        return JwtUserContext.builder()
                .userId("mary.marketing")
                .email("mary.marketing@werkflow.com")
                .fullName("Mary Marketing Specialist")
                .department("Marketing")
                .groups(List.of("MARKETING_STAFF"))
                .roles(List.of("USER"))
                .doaLevel(0)
                .build();
    }

    /**
     * Marketing Head (Level 2 - $10K DOA)
     * Use case: Approves $7,500 Marketing requests (but delegates to CFO)
     */
    public static JwtUserContext createMarketingHead() {
        return JwtUserContext.builder()
                .userId("mike.marketing.head")
                .email("mike.marketing.head@werkflow.com")
                .fullName("Mike Marketing Head")
                .department("Marketing")
                .groups(List.of("MARKETING_STAFF", "MARKETING_HEAD"))
                .roles(List.of("USER", "DEPARTMENT_HEAD"))
                .doaLevel(2)
                .build();
    }

    /**
     * Finance Manager (Level 3 - $100K DOA)
     * Use case: Reviews and approves/rejects $75K IT network infrastructure request
     */
    public static JwtUserContext createFinanceManager() {
        return JwtUserContext.builder()
                .userId("lisa.finance.manager")
                .email("lisa.finance.manager@werkflow.com")
                .fullName("Lisa Finance Manager")
                .department("Finance")
                .groups(List.of("FINANCE_STAFF", "FINANCE_MANAGER"))
                .roles(List.of("USER", "MANAGER", "FINANCE_MANAGER"))
                .doaLevel(3)
                .build();
    }

    /**
     * CFO (Level 4 - Unlimited DOA)
     * Use case: Approves executive-level CapEx including delegated approvals
     */
    public static JwtUserContext createCFO() {
        return JwtUserContext.builder()
                .userId("robert.cfo")
                .email("robert.cfo@werkflow.com")
                .fullName("Robert CFO")
                .department("Finance")
                .groups(List.of("FINANCE_STAFF", "EXECUTIVE", "CFO"))
                .roles(List.of("USER", "EXECUTIVE", "CFO"))
                .doaLevel(4)
                .build();
    }

    /**
     * Facilities Manager
     * Use case: Submits $250K building renovation request
     */
    public static JwtUserContext createFacilitiesManager() {
        return JwtUserContext.builder()
                .userId("tom.facilities")
                .email("tom.facilities@werkflow.com")
                .fullName("Tom Facilities Manager")
                .department("Facilities")
                .groups(List.of("FACILITIES_STAFF", "FACILITIES_MANAGER"))
                .roles(List.of("USER", "MANAGER"))
                .doaLevel(2)
                .build();
    }

    /**
     * Procurement Manager
     * Use case: Generates PO from approved CapEx requests
     */
    public static JwtUserContext createProcurementManager() {
        return JwtUserContext.builder()
                .userId("david.procurement")
                .email("david.procurement@werkflow.com")
                .fullName("David Procurement Manager")
                .department("Procurement")
                .groups(List.of("PROCUREMENT_STAFF", "PROCUREMENT_MANAGER"))
                .roles(List.of("USER", "PROCUREMENT_MANAGER"))
                .doaLevel(2)
                .build();
    }

    /**
     * Inventory Manager
     * Use case: Receives assets and tracks depreciation
     */
    public static JwtUserContext createInventoryManager() {
        return JwtUserContext.builder()
                .userId("jane.inventory")
                .email("jane.inventory@werkflow.com")
                .fullName("Jane Inventory Manager")
                .department("Inventory")
                .groups(List.of("INVENTORY_STAFF", "INVENTORY_MANAGER"))
                .roles(List.of("USER", "INVENTORY_MANAGER"))
                .doaLevel(2)
                .build();
    }

    /**
     * Substitute Approver (for delegation testing)
     * Use case: Receives delegated approval tasks
     */
    public static JwtUserContext createSubstituteApprover() {
        return JwtUserContext.builder()
                .userId("alex.substitute")
                .email("alex.substitute@werkflow.com")
                .fullName("Alex Substitute")
                .department("Finance")
                .groups(List.of("FINANCE_STAFF", "CFO"))
                .roles(List.of("USER", "EXECUTIVE"))
                .doaLevel(4)
                .build();
    }

    // ==================== Scenario 1: $500 IT Server Upgrade (Level 1) ====================

    /**
     * Creates CapEx request for $500 IT server upgrade
     * Expected: Routes to IT Department Manager (Level 1 DOA)
     */
    public static Map<String, Object> createCapEx500ServerUpgrade() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("requestId", "CAPEX-2024-001");
        variables.put("requestNumber", "CAPEX-2024-001");
        variables.put("requestType", "CapEx");
        variables.put("category", "IT_EQUIPMENT");
        variables.put("amount", new BigDecimal("500.00"));
        variables.put("requestAmount", new BigDecimal("500.00"));
        variables.put("title", "Server Upgrade - Dell PowerEdge");
        variables.put("description", "Upgrade existing server to handle increased load");
        variables.put("requester", "john.it");
        variables.put("requesterEmail", "john.it@werkflow.com");
        variables.put("requesterName", "John IT Employee");
        variables.put("department", "IT");
        variables.put("departmentName", "IT");
        variables.put("businessJustification", "Current server is 5 years old and cannot handle peak traffic");
        variables.put("expectedBenefits", "Improved system performance and reduced downtime");
        variables.put("priority", "NORMAL");
        variables.put("urgency", "Medium");
        variables.put("requestDate", LocalDate.now().toString());
        variables.put("expectedCompletionDate", LocalDate.now().plusMonths(1).toString());
        variables.put("budgetYear", 2024);
        variables.put("doaLevel", 1); // Level 1 approval required
        variables.put("budgetAvailable", true);
        return variables;
    }

    // ==================== Scenario 2: $7,500 Marketing Equipment with Delegation (Level 2) ====================

    /**
     * Creates CapEx request for $7,500 printing equipment
     * Expected: Routes to Marketing Head (Level 2 DOA) who delegates to CFO
     */
    public static Map<String, Object> createCapEx7500PrintingEquipment() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("requestId", "CAPEX-2024-002");
        variables.put("requestNumber", "CAPEX-2024-002");
        variables.put("requestType", "CapEx");
        variables.put("category", "OFFICE_EQUIPMENT");
        variables.put("amount", new BigDecimal("7500.00"));
        variables.put("requestAmount", new BigDecimal("7500.00"));
        variables.put("title", "Industrial Printer for Marketing Collateral");
        variables.put("description", "High-capacity industrial printer for in-house marketing materials");
        variables.put("requester", "mary.marketing");
        variables.put("requesterEmail", "mary.marketing@werkflow.com");
        variables.put("requesterName", "Mary Marketing Specialist");
        variables.put("department", "Marketing");
        variables.put("departmentName", "Marketing");
        variables.put("businessJustification", "Reduce outsourcing costs by bringing printing in-house");
        variables.put("expectedBenefits", "Save $15K annually on printing vendor costs");
        variables.put("priority", "HIGH");
        variables.put("urgency", "High");
        variables.put("requestDate", LocalDate.now().toString());
        variables.put("expectedCompletionDate", LocalDate.now().plusMonths(2).toString());
        variables.put("budgetYear", 2024);
        variables.put("doaLevel", 2); // Level 2 approval required
        variables.put("budgetAvailable", true);
        return variables;
    }

    // ==================== Scenario 3: $75K Network Infrastructure with Rejection (Level 3) ====================

    /**
     * Creates CapEx request for $75,000 network infrastructure
     * Expected: Routes to Finance Manager (Level 3 DOA) who initially rejects, then re-approves
     */
    public static Map<String, Object> createCapEx75KNetworkInfrastructure() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("requestId", "CAPEX-2024-003");
        variables.put("requestNumber", "CAPEX-2024-003");
        variables.put("requestType", "CapEx");
        variables.put("category", "NETWORK_INFRASTRUCTURE");
        variables.put("amount", new BigDecimal("75000.00"));
        variables.put("requestAmount", new BigDecimal("75000.00"));
        variables.put("title", "Enterprise Network Infrastructure Upgrade");
        variables.put("description", "Upgrade switches, routers, and firewall for company-wide network");
        variables.put("requester", "john.it");
        variables.put("requesterEmail", "john.it@werkflow.com");
        variables.put("requesterName", "John IT Employee");
        variables.put("department", "IT");
        variables.put("departmentName", "IT");
        variables.put("businessJustification", "Current network equipment is end-of-life. Security vulnerabilities identified.");
        variables.put("expectedBenefits", "Enhanced network security, 10x speed improvement, support for 500+ users");
        variables.put("priority", "CRITICAL");
        variables.put("urgency", "Critical");
        variables.put("requestDate", LocalDate.now().toString());
        variables.put("expectedCompletionDate", LocalDate.now().plusMonths(3).toString());
        variables.put("budgetYear", 2024);
        variables.put("doaLevel", 3); // Level 3 approval required
        variables.put("budgetAvailable", true);
        return variables;
    }

    /**
     * Creates resubmission variables for scenario 3 after initial rejection
     */
    public static Map<String, Object> createCapEx75KResubmission() {
        Map<String, Object> variables = createCapEx75KNetworkInfrastructure();
        variables.put("requestId", "CAPEX-2024-003-R1");
        variables.put("requestNumber", "CAPEX-2024-003-R1");
        variables.put("originalRequestId", "CAPEX-2024-003");
        variables.put("resubmissionVersion", 1);
        variables.put("businessJustification", "Current network equipment is end-of-life. Security vulnerabilities identified. "
                + "Updated with detailed ROI analysis: $50K saved annually on security incidents. "
                + "Phased implementation plan attached to reduce upfront cost.");
        variables.put("expectedBenefits", "Enhanced network security, 10x speed improvement, support for 500+ users. "
                + "Prevent potential data breaches (estimated cost: $200K per incident). "
                + "Enable remote work for 200+ employees.");
        return variables;
    }

    // ==================== Scenario 4: $250K Building Renovation (Level 4 Executive) ====================

    /**
     * Creates CapEx request for $250,000 building renovation
     * Expected: Routes to CFO/Executive (Level 4 DOA)
     */
    public static Map<String, Object> createCapEx250KBuildingRenovation() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("requestId", "CAPEX-2024-004");
        variables.put("requestNumber", "CAPEX-2024-004");
        variables.put("requestType", "CapEx");
        variables.put("category", "BUILDING_RENOVATION");
        variables.put("amount", new BigDecimal("250000.00"));
        variables.put("requestAmount", new BigDecimal("250000.00"));
        variables.put("title", "Office Building Renovation - Energy Efficiency Upgrade");
        variables.put("description", "Comprehensive renovation: HVAC, lighting, insulation, solar panels");
        variables.put("requester", "tom.facilities");
        variables.put("requesterEmail", "tom.facilities@werkflow.com");
        variables.put("requesterName", "Tom Facilities Manager");
        variables.put("department", "Facilities");
        variables.put("departmentName", "Facilities");
        variables.put("businessJustification", "Reduce energy costs by 40%. Current building systems are 20 years old. "
                + "Eligible for $75K government green energy rebate.");
        variables.put("expectedBenefits", "Save $60K annually on energy costs. ROI in 4.2 years. "
                + "Improve employee comfort and productivity. Reduce carbon footprint by 50 tons/year.");
        variables.put("priority", "CRITICAL");
        variables.put("urgency", "High");
        variables.put("requestDate", LocalDate.now().toString());
        variables.put("expectedCompletionDate", LocalDate.now().plusMonths(6).toString());
        variables.put("budgetYear", 2024);
        variables.put("doaLevel", 4); // Level 4 executive approval required
        variables.put("budgetAvailable", true);
        return variables;
    }

    // ==================== Delegation Test Data ====================

    /**
     * Creates delegation request from Marketing Head to CFO
     */
    public static Map<String, Object> createDelegationToSubstitute(String originalAssignee,
                                                                     String delegateTo,
                                                                     String reason) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("originalAssignee", originalAssignee);
        variables.put("delegateTo", delegateTo);
        variables.put("delegationReason", reason);
        variables.put("delegationTimestamp", System.currentTimeMillis());
        variables.put("delegationDate", LocalDate.now().toString());
        variables.put("isDelegated", true);
        return variables;
    }

    // ==================== Approval/Rejection Decision Data ====================

    /**
     * Creates approval decision variables
     */
    public static Map<String, Object> createApprovalDecision(String approverId,
                                                               String approverName,
                                                               String comments) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("approved", true);
        variables.put("approvalDecision", "APPROVED");
        variables.put("approverId", approverId);
        variables.put("approverName", approverName);
        variables.put("approverComments", comments);
        variables.put("approvalTimestamp", System.currentTimeMillis());
        variables.put("approvalDate", LocalDate.now().toString());
        return variables;
    }

    /**
     * Creates rejection decision with detailed feedback
     */
    public static Map<String, Object> createRejectionDecision(String approverId,
                                                                String approverName,
                                                                String reason) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("approved", false);
        variables.put("approvalDecision", "REJECTED");
        variables.put("approverId", approverId);
        variables.put("approverName", approverName);
        variables.put("rejectionReason", reason);
        variables.put("approverComments", reason);
        variables.put("rejectionTimestamp", System.currentTimeMillis());
        variables.put("rejectionDate", LocalDate.now().toString());
        return variables;
    }

    // ==================== Finance Validation Data ====================

    /**
     * Creates Finance budget validation result
     */
    public static Map<String, Object> createFinanceBudgetValidation(boolean budgetAvailable,
                                                                      String budgetCode,
                                                                      BigDecimal availableBalance) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("budgetAvailable", budgetAvailable);
        variables.put("budgetCode", budgetCode);
        variables.put("availableBalance", availableBalance);
        variables.put("budgetCheckTimestamp", System.currentTimeMillis());
        variables.put("budgetCheckDate", LocalDate.now().toString());
        return variables;
    }

    /**
     * Creates Chart of Accounts assignment
     */
    public static Map<String, Object> createCOAAssignment(String coaCode, String coaDescription) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("coaCode", coaCode);
        variables.put("coaDescription", coaDescription);
        variables.put("coaTimestamp", System.currentTimeMillis());
        return variables;
    }

    // ==================== Procurement PO Generation Data ====================

    /**
     * Creates PO generation variables
     */
    public static Map<String, Object> createPOGeneration(String poNumber,
                                                          String vendorName,
                                                          BigDecimal poAmount) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("poNumber", poNumber);
        variables.put("poGenerated", true);
        variables.put("vendorName", vendorName);
        variables.put("vendorId", "VENDOR-" + vendorName.toUpperCase().replace(" ", "-"));
        variables.put("poAmount", poAmount);
        variables.put("poDate", LocalDate.now().toString());
        variables.put("poGenerationTimestamp", System.currentTimeMillis());
        return variables;
    }

    // ==================== Inventory Asset Tracking Data ====================

    /**
     * Creates asset reception variables
     */
    public static Map<String, Object> createAssetReception(String assetTag,
                                                            String assetName,
                                                            BigDecimal assetValue,
                                                            int quantity) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("assetReceived", true);
        variables.put("assetTag", assetTag);
        variables.put("assetName", assetName);
        variables.put("assetValue", assetValue);
        variables.put("assetQuantity", quantity);
        variables.put("receptionDate", LocalDate.now().toString());
        variables.put("receptionTimestamp", System.currentTimeMillis());
        return variables;
    }

    /**
     * Creates depreciation schedule variables
     */
    public static Map<String, Object> createDepreciationSchedule(int usefulLifeYears,
                                                                   String depreciationMethod,
                                                                   BigDecimal annualDepreciation) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("usefulLifeYears", usefulLifeYears);
        variables.put("depreciationMethod", depreciationMethod);
        variables.put("annualDepreciation", annualDepreciation);
        variables.put("depreciationStartDate", LocalDate.now().toString());
        return variables;
    }

    /**
     * Creates phased asset delivery variables (for large projects)
     */
    public static Map<String, Object> createPhasedDelivery(int phaseNumber,
                                                            int totalPhases,
                                                            BigDecimal phaseAmount) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("phasedDelivery", true);
        variables.put("currentPhase", phaseNumber);
        variables.put("totalPhases", totalPhases);
        variables.put("phaseAmount", phaseAmount);
        variables.put("phaseDeliveryDate", LocalDate.now().plusMonths(phaseNumber).toString());
        return variables;
    }

    // ==================== Finance Capitalization Data ====================

    /**
     * Creates asset capitalization variables
     */
    public static Map<String, Object> createAssetCapitalization(String ledgerAccountCode,
                                                                  BigDecimal capitalizedAmount,
                                                                  LocalDate capitalizationDate) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("capitalized", true);
        variables.put("ledgerAccountCode", ledgerAccountCode);
        variables.put("capitalizedAmount", capitalizedAmount);
        variables.put("capitalizationDate", capitalizationDate.toString());
        variables.put("capitalizationTimestamp", System.currentTimeMillis());
        return variables;
    }

    // ==================== Notification Data ====================

    /**
     * Creates notification tracking variables
     */
    public static Map<String, Object> createNotificationTracking(String notificationType,
                                                                   String recipientEmail,
                                                                   String subject) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("notificationType", notificationType);
        variables.put("recipientEmail", recipientEmail);
        variables.put("notificationSubject", subject);
        variables.put("notificationSent", true);
        variables.put("notificationTimestamp", System.currentTimeMillis());
        return variables;
    }
}
