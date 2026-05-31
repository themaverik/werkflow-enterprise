import path from 'path'

export const STORAGE_STATES = {
  admin: path.join(__dirname, '../.auth/admin.json'),
  manager: path.join(__dirname, '../.auth/manager.json'),
  employee: path.join(__dirname, '../.auth/employee.json'),
}

export const TEST_USERS = {
  admin: {
    username: process.env.E2E_ADMIN_USER || 'admin',
    password: process.env.E2E_ADMIN_PASSWORD || 'REDACTED_PASSWORD',
    roles: ['super_admin'],
    doaLevel: 4,
    department: 'IT',
  },
  manager: {
    username: process.env.E2E_MANAGER_USER || 'john.manager',
    password: process.env.E2E_MANAGER_PASSWORD || 'manager123',
    roles: ['doa_approver_level2'],
    doaLevel: 2,
    department: 'Finance',
  },
  employee: {
    username: process.env.E2E_EMPLOYEE_USER || 'jane.employee',
    password: process.env.E2E_EMPLOYEE_PASSWORD || 'employee123',
    roles: ['employee'],
    doaLevel: 0,
    department: 'HR',
  },
}
