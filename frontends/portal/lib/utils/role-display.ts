// Maps Keycloak role strings to display names using the same logic as backend RoleDisplayService

const ROLE_SUFFIXES = [
  { suffix: 'department_head',   doaLevel: 2, display: 'Department Head',    deptScoped: true  },
  { suffix: 'senior_manager',    doaLevel: 1, display: 'Senior Manager',     deptScoped: true  },
  { suffix: 'lead_manager',      doaLevel: 1, display: 'Lead Manager',       deptScoped: true  },
  { suffix: 'employee',          doaLevel: 0, display: 'Employee',           deptScoped: true  },
  { suffix: 'global_management', doaLevel: 3, display: 'Global Management',  deptScoped: false },
  { suffix: 'c_suite',           doaLevel: 4, display: 'C-Suite',            deptScoped: false },
  { suffix: 'super_admin',       doaLevel: 4, display: 'Super Admin',        deptScoped: false },
  { suffix: 'admin',             doaLevel: 3, display: 'Admin',              deptScoped: false },
  { suffix: 'basic',             doaLevel: 0, display: 'Employee',           deptScoped: false },
] as const

export function getDisplayRole(roles: string[], departmentCode?: string): string {
  if (!roles || roles.length === 0) return 'Employee'

  let bestRole: (typeof ROLE_SUFFIXES)[number] | null = null
  for (const role of roles) {
    const lower = role.toLowerCase()
    for (const def of ROLE_SUFFIXES) {
      if (lower === def.suffix || lower.endsWith('_' + def.suffix)) {
        if (!bestRole || def.doaLevel > bestRole.doaLevel) {
          bestRole = def
        }
      }
    }
  }

  if (!bestRole) return 'Employee'

  if (bestRole.deptScoped && departmentCode) {
    return `${titleCase(departmentCode)} ${bestRole.display}`
  }

  return bestRole.display
}

export function getDoaLevel(roles: string[]): number {
  if (!roles || roles.length === 0) return 0
  let maxDoa = 0
  for (const role of roles) {
    const lower = role.toLowerCase()
    for (const def of ROLE_SUFFIXES) {
      if (lower === def.suffix || lower.endsWith('_' + def.suffix)) {
        maxDoa = Math.max(maxDoa, def.doaLevel)
      }
    }
  }
  return maxDoa
}

function titleCase(str: string): string {
  return str.split(/[_\s]/).map(w => w.charAt(0).toUpperCase() + w.slice(1).toLowerCase()).join(' ')
}
