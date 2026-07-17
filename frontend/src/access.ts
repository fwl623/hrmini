import { ROLES, type RoleCode } from '@/constants/roles';

function roleIn(role: string | undefined, list: readonly RoleCode[]): boolean {
  return !!role && (list as readonly string[]).includes(role);
}

/**
 * Umi access 权限（对齐系分 §2.3 / access.ts）
 * 菜单门控 + PermissionButton；数据权限仍由后端 DataScope 保证
 */
export default function access(initialState: API.InitialState) {
  const { roleCode, permissions = [], roles = [] } = initialState?.currentUser ?? {};
  const has = (code: string) => permissions.includes(code);
  const isHr = roleIn(roleCode, [ROLES.SYS_ADMIN, ROLES.HR_STAFF]);
  const isOrgReader = isHr || roleIn(roleCode, [ROLES.DEPT_MANAGER, ROLES.FINANCE]);

  return {
    canSysAdmin: roleCode === ROLES.SYS_ADMIN,
    canHr: isHr,
    canFinance: roleCode === ROLES.FINANCE,
    canManager: roleCode === ROLES.DEPT_MANAGER,
    canEmployee: roles.includes(ROLES.EMPLOYEE),
    canPortal: roles.includes(ROLES.EMPLOYEE),
    canViewPayroll:
      roleCode !== ROLES.SYS_ADMIN &&
      (has('payroll:view') || roleIn(roleCode, [ROLES.HR_STAFF, ROLES.FINANCE])),
    canApprove: has('approval:handle'),
    canImport: roleCode === ROLES.HR_STAFF,
    canManageOrg:
      has('org:dept:edit') ||
      has('org:dept:view') ||
      has('org:position:view') ||
      has('org:position:edit') ||
      has('menu:org') ||
      isHr,
    /** 部门查看：与后端 OrgAccessGuard.requireDeptRead 对齐 */
    canViewDept:
      has('org:dept:view') ||
      has('org:dept:edit') ||
      has('menu:org') ||
      isOrgReader,
    /** 部门编辑：与后端 requireDeptWrite 对齐（角色 HR/SYS_ADMIN 或 edit 权限码） */
    canEditDept: has('org:dept:edit') || isHr,
    /** 职位查看：与 requirePositionRead 对齐 */
    canViewPosition:
      has('org:position:view') ||
      has('org:position:edit') ||
      has('menu:org') ||
      isOrgReader,
    /** 职位编辑：与 requirePositionWrite 对齐 */
    canEditPosition: has('org:position:edit') || isHr,
    canManageAttendance: has('attendance:manage') || has('menu:attendance'),
    canManageWorkflow: has('workflow:manage'),
    canManageSystem: roleCode === ROLES.SYS_ADMIN || has('menu:system'),
    canViewEmployee: has('menu:employee') || isHr,
    hasPermission: (code: string) => has(code),
  };
}
