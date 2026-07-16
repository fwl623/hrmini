import { ROLES } from '@/constants/roles';

/**
 * Umi access 权限（对齐系分 §2.3 / access.ts）
 * 菜单门控 + PermissionButton；数据权限仍由后端 DataScope 保证
 */
export default function access(initialState: API.InitialState) {
  const { roleCode, permissions = [], roles = [] } = initialState?.currentUser ?? {};
  const has = (code: string) => permissions.includes(code);
  const isHr = [ROLES.SYS_ADMIN, ROLES.HR_STAFF].includes(roleCode ?? '');

  return {
    canSysAdmin: roleCode === ROLES.SYS_ADMIN,
    canHr: isHr,
    canFinance: roleCode === ROLES.FINANCE,
    canManager: roleCode === ROLES.DEPT_MANAGER,
    canEmployee: roles.includes(ROLES.EMPLOYEE),
    canPortal: roles.includes(ROLES.EMPLOYEE),
    canViewPayroll:
      roleCode !== ROLES.SYS_ADMIN &&
      (has('payroll:view') || [ROLES.HR_STAFF, ROLES.FINANCE].includes(roleCode ?? '')),
    canApprove: has('approval:handle'),
    canImport: roleCode === ROLES.HR_STAFF,
    canManageOrg: has('org:dept:edit') || has('menu:org') || isHr,
    canManageAttendance: has('attendance:manage') || has('menu:attendance'),
    canManageWorkflow: has('workflow:manage'),
    canManageSystem: roleCode === ROLES.SYS_ADMIN || has('menu:system'),
    canViewEmployee: has('menu:employee') || isHr,
    hasPermission: (code: string) => has(code),
  };
}
