package com.werkflow.engine.fixtures;

import com.werkflow.engine.dto.JwtUserContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Factory class for creating test data for integration tests.
 * Provides pre-configured users, process variables, and workflow scenarios.
 */
public class TestDataFactory {

    // ==================== Test Users ====================

    /**
     * Creates a requester user (Level 0 - No approval authority).
     * Use case: Submit requests that need approval
     */
    public static JwtUserContext createRequester() {
        return JwtUserContext.builder()
                .userId("john.employee")
                .email("john.employee@werkflow.com")
                .fullName("John Employee")
                .department("Engineering")
                .groups(List.of("ENGINEERING_STAFF"))
                .roles(List.of("USER"))
                .doaLevel(0)
                .build();
    }

    /**
     * Creates a Department Manager user (Level 1 - $1K approval authority).
     * Use case: Approve requests up to $1,000
     */
    public static JwtUserContext createDepartmentManager() {
        return JwtUserContext.builder()
                .userId("sarah.manager")
                .email("sarah.manager@werkflow.com")
                .fullName("Sarah Manager")
                .department("Engineering")
                .groups(List.of("ENGINEERING_STAFF", "ENGINEERING_MANAGER"))
                .roles(List.of("USER", "MANAGER"))
                .doaLevel(1)
                .build();
    }

    /**
     * Creates a Department Head user (Level 2 - $10K approval authority).
     * Use case: Approve requests up to $10,000
     */
    public static JwtUserContext createDepartmentHead() {
        return JwtUserContext.builder()
                .userId("mike.head")
                .email("mike.head@werkflow.com")
                .fullName("Mike Head")
                .department("Engineering")
                .groups(List.of("ENGINEERING_STAFF", "ENGINEERING_HEAD"))
                .roles(List.of("USER", "DEPARTMENT_HEAD"))
                .doaLevel(2)
                .build();
    }

    /**
     * Creates a Director user (Level 3 - $50K approval authority).
     * Use case: Approve requests up to $50,000
     */
    public static JwtUserContext createDirector() {
        return JwtUserContext.builder()
                .userId("lisa.director")
                .email("lisa.director@werkflow.com")
                .fullName("Lisa Director")
                .department("Operations")
                .groups(List.of("OPERATIONS_STAFF", "OPERATIONS_DIRECTOR"))
                .roles(List.of("USER", "DIRECTOR"))
                .doaLevel(3)
                .build();
    }

    /**
     * Creates an Executive/CFO user (Level 4 - Unlimited approval authority).
     * Use case: Approve any amount
     */
    public static JwtUserContext createExecutive() {
        return JwtUserContext.builder()
                .userId("robert.cfo")
                .email("robert.cfo@werkflow.com")
                .fullName("Robert CFO")
                .department("Finance")
                .groups(List.of("FINANCE_STAFF", "EXECUTIVE"))
                .roles(List.of("USER", "EXECUTIVE", "CFO"))
                .doaLevel(4)
                .build();
    }

    /**
     * Creates an HR Manager user.
     * Use case: HR leave approval workflows
     */
    public static JwtUserContext createHrManager() {
        return JwtUserContext.builder()
                .userId("jennifer.hr")
                .email("jennifer.hr@werkflow.com")
                .fullName("Jennifer HR")
                .department("HR")
                .groups(List.of("HR_STAFF", "HR_MANAGER"))
                .roles(List.of("USER", "HR_MANAGER"))
                .doaLevel(2)
                .build();
    }

    /**
     * Creates a Procurement Manager user.
     * Use case: Procurement PR-to-PO workflows
     */
    public static JwtUserContext createProcurementManager() {
        return JwtUserContext.builder()
                .userId("david.procurement")
                .email("david.procurement@werkflow.com")
                .fullName("David Procurement")
                .department("Procurement")
                .groups(List.of("PROCUREMENT_STAFF", "PROCUREMENT_MANAGER"))
                .roles(List.of("USER", "PROCUREMENT_MANAGER"))
                .doaLevel(2)
                .build();
    }

    /**
     * Creates a Warehouse Staff user.
     * Use case: Inventory asset transfer workflows
     */
    public static JwtUserContext createWarehouseStaff() {
        return JwtUserContext.builder()
                .userId("tom.warehouse")
                .email("tom.warehouse@werkflow.com")
                .fullName("Tom Warehouse")
                .department("Warehouse")
                .groups(List.of("WAREHOUSE_STAFF"))
                .roles(List.of("USER", "WAREHOUSE_STAFF"))
                .doaLevel(1)
                .build();
    }

    /**
     * Creates a substitute approver for delegation testing.
     * Use case: Task delegation scenarios
     */
    public static JwtUserContext createSubstituteApprover() {
        return JwtUserContext.builder()
                .userId("alex.substitute")
                .email("alex.substitute@werkflow.com")
                .fullName("Alex Substitute")
                .department("Engineering")
                .groups(List.of("ENGINEERING_STAFF", "ENGINEERING_MANAGER"))
                .roles(List.of("USER", "MANAGER"))
                .doaLevel(2)
                .build();
    }

    // ==================== CapEx Approval Test Data ====================

    /**
     * Creates CapEx request variables for $500 amount (Level 1 approval).
     * Expected: Routes to Department Manager
     */
    public static Map<String, Object> createCapExRequest500() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("requestId", "CAPEX-2024-001");
        variables.put("requestType", "CapEx");
        variables.put("amount", new BigDecimal("500.00"));
        variables.put("description", "New laptop for developer");
        variables.put("requester", "john.employee");
        variables.put("requesterEmail", "john.employee@werkflow.com");
        variables.put("department", "Engineering");
        variables.put("justification", "Current laptop is 5 years old and cannot run latest IDE");
        variables.put("priority", 50);
        variables.put("urgency", "Medium");
        return variables;
    }

    /**
     * Creates CapEx request variables for $5,000 amount (Level 2 approval).
     * Expected: Routes to Department Head
     */
    public static Map<String, Object> createCapExRequest5000() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("requestId", "CAPEX-2024-002");
        variables.put("requestType", "CapEx");
        variables.put("amount", new BigDecimal("5000.00"));
        variables.put("description", "Development server hardware");
        variables.put("requester", "john.employee");
        variables.put("requesterEmail", "john.employee@werkflow.com");
        variables.put("department", "Engineering");
        variables.put("justification", "Need dedicated server for CI/CD pipeline");
        variables.put("priority", 75);
        variables.put("urgency", "High");
        return variables;
    }

    /**
     * Creates CapEx request variables for $100,000 amount (Level 4 approval).
     * Expected: Routes to Executive/CFO
     */
    public static Map<String, Object> createCapExRequest100K() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("requestId", "CAPEX-2024-003");
        variables.put("requestType", "CapEx");
        variables.put("amount", new BigDecimal("100000.00"));
        variables.put("description", "Enterprise data center upgrade");
        variables.put("requester", "john.employee");
        variables.put("requesterEmail", "john.employee@werkflow.com");
        variables.put("department", "IT");
        variables.put("justification", "Migrate to cloud infrastructure for scalability");
        variables.put("priority", 100);
        variables.put("urgency", "Critical");
        return variables;
    }

    // ==================== HR Leave Request Test Data ====================

    /**
     * Creates HR leave request variables (5 days annual leave).
     */
    public static Map<String, Object> createLeaveRequest() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("requestId", "LEAVE-2024-001");
        variables.put("requestType", "Leave");
        variables.put("leaveType", "Annual Leave");
        variables.put("employeeId", "john.employee");
        variables.put("employeeName", "John Employee");
        variables.put("employeeEmail", "john.employee@werkflow.com");
        variables.put("department", "Engineering");
        variables.put("managerId", "sarah.manager");
        variables.put("startDate", LocalDate.now().plusDays(30).toString());
        variables.put("endDate", LocalDate.now().plusDays(35).toString());
        variables.put("totalDays", 5);
        variables.put("reason", "Family vacation");
        variables.put("priority", 50);
        return variables;
    }

    // ==================== Procurement Test Data ====================

    /**
     * Creates purchase requisition variables.
     */
    public static Map<String, Object> createPurchaseRequisition() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("requestId", "PR-2024-001");
        variables.put("requestType", "Procurement");
        variables.put("prNumber", "PR-2024-001");
        variables.put("requesterId", "john.employee");
        variables.put("requesterName", "John Employee");
        variables.put("requesterEmail", "john.employee@werkflow.com");
        variables.put("department", "Engineering");
        variables.put("itemDescription", "Office supplies - 100 notebooks, 50 pens");
        variables.put("quantity", 150);
        variables.put("estimatedAmount", new BigDecimal("750.00"));
        variables.put("supplier", "Office Depot");
        variables.put("urgency", "Normal");
        variables.put("priority", 50);
        return variables;
    }

    // ==================== Inventory Asset Transfer Test Data ====================

    /**
     * Creates asset transfer request variables.
     */
    public static Map<String, Object> createAssetTransferRequest() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("requestId", "AT-2024-001");
        variables.put("requestType", "AssetTransfer");
        variables.put("assetId", "ASSET-12345");
        variables.put("assetName", "Dell Laptop XPS 15");
        variables.put("assetType", "IT Equipment");
        variables.put("fromLocation", "Building A - Floor 3");
        variables.put("toLocation", "Building B - Floor 2");
        variables.put("fromDepartment", "Engineering");
        variables.put("toDepartment", "Marketing");
        variables.put("requesterId", "john.employee");
        variables.put("requesterName", "John Employee");
        variables.put("requesterEmail", "john.employee@werkflow.com");
        variables.put("transferReason", "Employee transferred to Marketing department");
        variables.put("priority", 50);
        return variables;
    }

    // ==================== Task Delegation Test Data ====================

    /**
     * Creates delegation request variables.
     */
    public static Map<String, Object> createDelegationRequest(String originalAssignee,
                                                               String delegateTo,
                                                               String reason) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("originalAssignee", originalAssignee);
        variables.put("delegateTo", delegateTo);
        variables.put("delegationReason", reason);
        variables.put("delegationTimestamp", System.currentTimeMillis());
        return variables;
    }

    // ==================== Approval Decision Test Data ====================

    /**
     * Creates approval decision variables.
     */
    public static Map<String, Object> createApprovalDecision(boolean approved, String comments) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("approved", approved);
        variables.put("approvalDecision", approved ? "APPROVED" : "REJECTED");
        variables.put("approverComments", comments);
        variables.put("approvalTimestamp", System.currentTimeMillis());
        return variables;
    }

    /**
     * Creates rejection decision variables with detailed comments.
     */
    public static Map<String, Object> createRejectionDecision(String reason) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("approved", false);
        variables.put("approvalDecision", "REJECTED");
        variables.put("rejectionReason", reason);
        variables.put("approverComments", reason);
        variables.put("approvalTimestamp", System.currentTimeMillis());
        return variables;
    }
}
