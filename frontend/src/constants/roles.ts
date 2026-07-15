/**
 * 角色与权限码（对齐后端系分 v1.7 / 附录 H）
 */
export const ROLES = {
  SYS_ADMIN: 'SYS_ADMIN',
  HR_STAFF: 'HR_STAFF',
  DEPT_MANAGER: 'DEPT_MANAGER',
  FINANCE: 'FINANCE',
  EMPLOYEE: 'EMPLOYEE',
} as const;

export type RoleCode = (typeof ROLES)[keyof typeof ROLES];

export const ADMIN_ROLES: RoleCode[] = [
  ROLES.SYS_ADMIN,
  ROLES.HR_STAFF,
  ROLES.DEPT_MANAGER,
  ROLES.FINANCE,
];

export const API_BASE = '/api/v1';
