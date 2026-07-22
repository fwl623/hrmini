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

/** 角色中文名（列表/标签展示用） */
export const ROLE_LABELS: Record<string, string> = {
  SYS_ADMIN: '系统管理员',
  HR_STAFF: 'HR专员',
  DEPT_MANAGER: '部门负责人',
  FINANCE: '财务专员',
  FINANCE_MANAGER: '财务经理',
  EMPLOYEE: '普通员工',
};

export function roleLabel(code?: string | null): string {
  if (!code) return '—';
  return ROLE_LABELS[code] || code;
}

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

/** 是否仅有普通员工角色（无任何管理端角色）→ 只能进 /portal */
export function isEmployeeOnly(roles: string[] = []): boolean {
  const list = roles.map((r) => String(r).trim().toUpperCase()).filter(Boolean);
  if (!list.length) return false;
  const hasEmployee = list.includes(ROLES.EMPLOYEE);
  const hasAdmin = list.some((role) => ADMIN_ROLES.includes(role as RoleCode));
  return hasEmployee && !hasAdmin;
}

/**
 * 持有任一「业务管理」菜单/系统 API 权限码时，可进入 /admin
 * （纯 EMPLOYEE 不受此项影响，见 isEmployeeOnly）。
 * 不含 menu:workbench / ai:chat——普通员工种子权限常带这两项。
 */
export const ADMIN_ENTRY_PERMISSIONS: string[] = [
  'menu:system',
  'menu:org',
  'menu:employee',
  'menu:onboarding',
  'menu:approval',
  'menu:attendance',
  'menu:payroll',
  'menu:workflow',
  'system:user:view',
  'system:user:edit',
  'system:role:view',
  'system:role:edit',
  'org:dept:view',
  'org:dept:edit',
  'org:position:view',
  'org:position:edit',
  'ai:knowledge:manage',
  'payroll:view',
  'attendance:manage',
  'workflow:manage',
  'approval:handle',
];

export function hasAdminEntryPermission(permissions: string[] = []): boolean {
  if (!permissions.length) return false;
  const set = new Set(permissions);
  return ADMIN_ENTRY_PERMISSIONS.some((code) => set.has(code));
}

/** 是否允许进入管理端：有管理角色，或（非纯员工且）持有管理端权限码 */
export function canAccessAdmin(roles: string[] = [], permissions: string[] = []): boolean {
  if (isEmployeeOnly(roles)) return false;
  const list = roles.map((r) => String(r).trim().toUpperCase()).filter(Boolean);
  const hasAdminRole = list.some((role) => ADMIN_ROLES.includes(role as RoleCode));
  return hasAdminRole || hasAdminEntryPermission(permissions);
}

export const API_BASE = '/api/v1';
