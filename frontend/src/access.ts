import { ROLES, type RoleCode } from '@/constants/roles';

function roleIn(role: string | undefined, list: readonly RoleCode[]): boolean {
  return !!role && (list as readonly string[]).includes(role);
}

/**
 * Umi access 权限（对齐 PRD §2.1 / §2.2）
 * 菜单门控 + PermissionButton；数据权限仍由后端 DataScope 保证
 *
 * FINANCE：仅薪资全量 + 本人档案（门户）；不可见组织/审批/考勤/花名册
 */
export default function access(initialState: API.InitialState) {
  const { roleCode, permissions = [], roles = [] } = initialState?.currentUser ?? {};
  const has = (code: string) => permissions.includes(code);
  /** 人事业务主角色（含管理员配置侧）；不含 FINANCE */
  const isHr = roleIn(roleCode, [ROLES.SYS_ADMIN, ROLES.HR_STAFF]);
  /** 组织架构可读：HR/管理员/部门主管；财务按 PRD 不可见组织管理 */
  const isOrgReader = isHr || roleCode === ROLES.DEPT_MANAGER;
  const isFinance = roleCode === ROLES.FINANCE;

  return {
    canSysAdmin: roleCode === ROLES.SYS_ADMIN,
    canHr: isHr,
    canFinance: isFinance,
    canManager: roleCode === ROLES.DEPT_MANAGER,
    canEmployee: roles.includes(ROLES.EMPLOYEE),
    canPortal: roles.includes(ROLES.EMPLOYEE),
    canViewPayroll:
      roleCode !== ROLES.SYS_ADMIN &&
      (has('payroll:view') || roleIn(roleCode, [ROLES.HR_STAFF, ROLES.FINANCE])),
    canApprove:
      !isFinance &&
      (has('approval:handle') ||
        has('menu:workflow') ||
        isHr ||
        roleCode === ROLES.DEPT_MANAGER),
    canImport: roleCode === ROLES.HR_STAFF,
    canManageOrg: !isFinance && (has('menu:org') || isHr || has('org:dept:edit')),
    /** 部门查看：与后端 OrgAccessGuard.requireDeptRead 对齐；FINANCE 硬关 */
    canViewDept:
      !isFinance &&
      (has('org:dept:view') ||
        has('org:dept:edit') ||
        has('menu:org') ||
        isOrgReader),
    canEditDept: !isFinance && (has('org:dept:edit') || isHr),
    canViewPosition:
      !isFinance &&
      (has('org:position:view') ||
        has('org:position:edit') ||
        has('menu:org') ||
        isOrgReader),
    canEditPosition: !isFinance && (has('org:position:edit') || isHr),
    canManageAttendance:
      !isFinance &&
      (has('attendance:manage') || has('menu:attendance') || isHr),
    canManageWorkflow:
      !isFinance &&
      (has('workflow:manage') ||
        has('menu:workflow') ||
        has('approval:handle') ||
        isHr ||
        roleCode === ROLES.DEPT_MANAGER),
    canManageSystem: roleCode === ROLES.SYS_ADMIN || has('menu:system'),
    canViewEmployee:
      !isFinance &&
      (has('menu:employee') || isHr || roleCode === ROLES.DEPT_MANAGER),
    hasPermission: (code: string) => has(code),
  };
}
