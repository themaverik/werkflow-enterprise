package com.werkflow.business.hr.service;

import com.werkflow.business.hr.entity.RoleLevel;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Computes human-readable role display names from Keycloak roles + department context.
 *
 * Examples:
 *   roles=["it_department_head"], dept="IT" → "IT Department Head"
 *   roles=["c_suite"]                       → "C-Suite"
 *   roles=["finance_senior_manager"]        → "Finance Senior Manager"
 */
@Service
public class RoleDisplayService {

    /**
     * Returns the primary display role label for a user given their Keycloak roles and dept code.
     */
    public String getDisplayRole(List<String> keycloakRoles, String departmentCode) {
        if (keycloakRoles == null || keycloakRoles.isEmpty()) {
            return "Employee";
        }

        // Priority: highest DoA role wins
        String bestRole = null;
        int bestDoa = -1;
        for (String role : keycloakRoles) {
            RoleLevel rl = RoleLevel.fromKeycloakRole(role);
            if (rl.getDefaultDoaLevel() > bestDoa) {
                bestDoa = rl.getDefaultDoaLevel();
                bestRole = role;
            }
        }

        if (bestRole == null) return "Employee";

        RoleLevel rl = RoleLevel.fromKeycloakRole(bestRole);

        if (rl.isDepartmentScoped() && departmentCode != null) {
            String deptCode = RoleLevel.extractDeptCode(bestRole);
            String deptDisplay = deptCode != null ? titleCase(deptCode) : titleCase(departmentCode);
            return deptDisplay + " " + titleCase(rl.getKeycloakSuffix().replace("_", " "));
        }

        if (rl == RoleLevel.C_SUITE) return "C-Suite";
        return titleCase(rl.getKeycloakSuffix().replace("_", " "));
    }

    /**
     * Returns the Keycloak role name to use when assigning a dept-scoped role to a user.
     * E.g. deptCode="IT", roleLevel=DEPARTMENT_HEAD → "it_department_head"
     */
    public String buildKeycloakRoleName(String deptCode, RoleLevel roleLevel) {
        if (!roleLevel.isDepartmentScoped()) {
            return roleLevel.getKeycloakSuffix();
        }
        return deptCode.toLowerCase() + "_" + roleLevel.getKeycloakSuffix();
    }

    private String titleCase(String input) {
        if (input == null || input.isBlank()) return "";
        String[] words = input.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)))
                  .append(word.substring(1).toLowerCase())
                  .append(" ");
            }
        }
        return sb.toString().trim();
    }
}
