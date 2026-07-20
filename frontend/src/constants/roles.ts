/**
 * 角色与权限码（对齐后端系分 v1.7 / 附录 H）
 */
export const ROLES = {
  SYS_ADMIN: 'SYS_ADMIN',
  HR_STAFF: 'HR_STAFF',
  DEPT_MANAGER: 'DEPT_MANAGER',
  /** 财务专员：薪资域，无审批中心 */
  FINANCE: 'FINANCE',
  /** 财务经理：薪资域 + 财务审批（调岗调薪等） */
  FINANCE_MANAGER: 'FINANCE_MANAGER',
  EMPLOYEE: 'EMPLOYEE',
} as const;

export type RoleCode = (typeof ROLES)[keyof typeof ROLES];

export const ADMIN_ROLES: RoleCode[] = [
  ROLES.SYS_ADMIN,
  ROLES.HR_STAFF,
  ROLES.DEPT_MANAGER,
  ROLES.FINANCE,
  ROLES.FINANCE_MANAGER,
];

/** 多角色时取主角色（优先级高的在前） */
export const ROLE_PRIORITY: RoleCode[] = [
  ROLES.SYS_ADMIN,
  ROLES.HR_STAFF,
  ROLES.FINANCE_MANAGER,
  ROLES.FINANCE,
  ROLES.DEPT_MANAGER,
  ROLES.EMPLOYEE,
];

export function resolvePrimaryRole(roles: string[] = []): string {
  for (const role of ROLE_PRIORITY) {
    if (roles.includes(role)) {
      return role;
    }
  }
  return roles[0] ?? '';
}

export const API_BASE = '/api/v1';
