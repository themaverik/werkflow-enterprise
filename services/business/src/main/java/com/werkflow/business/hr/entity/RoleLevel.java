package com.werkflow.business.hr.entity;

/**
 * Department-scoped role levels.
 * Combined with department code for display: e.g. "it_department_head"
 * Independent roles are represented in Keycloak and mapped to DoA levels.
 */
public enum RoleLevel {

    // Department-scoped roles (require a department)
    DEPARTMENT_HEAD(2, "department_head"),
    SENIOR_MANAGER(1, "senior_manager"),
    LEAD_MANAGER(1, "lead_manager"),
    EMPLOYEE(0, "employee"),

    // Independent roles (no department required)
    GLOBAL_MANAGEMENT(3, "global_management"),
    C_SUITE(4, "c_suite"),
    SUPER_ADMIN(4, "super_admin"),
    ADMIN(3, "admin"),
    BASIC(0, "basic");

    private final int defaultDoaLevel;
    private final String keycloakSuffix;

    RoleLevel(int defaultDoaLevel, String keycloakSuffix) {
        this.defaultDoaLevel = defaultDoaLevel;
        this.keycloakSuffix = keycloakSuffix;
    }

    public int getDefaultDoaLevel() {
        return defaultDoaLevel;
    }

    public String getKeycloakSuffix() {
        return keycloakSuffix;
    }

    public boolean isDepartmentScoped() {
        return this == DEPARTMENT_HEAD || this == SENIOR_MANAGER
            || this == LEAD_MANAGER || this == EMPLOYEE;
    }

    /**
     * Map a Keycloak role string to a RoleLevel.
     * E.g. "it_department_head" → DEPARTMENT_HEAD, "c_suite" → C_SUITE
     */
    public static RoleLevel fromKeycloakRole(String role) {
        if (role == null) return BASIC;
        String lower = role.toLowerCase();
        for (RoleLevel rl : values()) {
            if (lower.endsWith("_" + rl.keycloakSuffix) || lower.equals(rl.keycloakSuffix)) {
                return rl;
            }
        }
        return BASIC;
    }

    /**
     * Extract department code prefix from a dept-scoped Keycloak role.
     * E.g. "it_department_head" → "IT"
     */
    public static String extractDeptCode(String role) {
        if (role == null) return null;
        String lower = role.toLowerCase();
        for (RoleLevel rl : values()) {
            if (rl.isDepartmentScoped() && lower.endsWith("_" + rl.keycloakSuffix)) {
                return lower.replace("_" + rl.keycloakSuffix, "").toUpperCase();
            }
        }
        return null;
    }
}
