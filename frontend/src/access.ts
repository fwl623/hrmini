import { ROLES, type RoleCode } from '@/constants/roles';

function roleIn(role: string | undefined, list: readonly RoleCode[]): boolean {
  return !!role && (list as readonly string[]).includes(role);
}

/**
 * Umi access 权限（对齐 PRD §2.1 / §2.2）
 * 菜单门控 + PermissionButton；数据权限仍由后端 DataScope 保证
 *
 * DEPT_MANAGER：本部门花名册 + 审批中心 + 组织管理只读；无手机号变更 / 入转调离管理台
 * FINANCE（专员）：薪资全量 + 本人档案；无审批中心
 * FINANCE_MANAGER（经理）：同上 + 审批中心（调岗调薪等待办）
 */
export default function access(initialState: API.InitialState) {
  const { roleCode, permissions = [], roles = [] } = initialState?.currentUser ?? {};
  const has = (code: string) => permissions.includes(code);
  /** 人事业务主角色（含管理员配置侧）；不含财务 */
  const isHr = roleIn(roleCode, [ROLES.SYS_ADMIN, ROLES.HR_STAFF]);
  const isFinanceFamily = roleIn(roleCode, [ROLES.FINANCE, ROLES.FINANCE_MANAGER]);
  const isFinanceManager = roleCode === ROLES.FINANCE_MANAGER;
  const isDeptManager = roleCode === ROLES.DEPT_MANAGER;
  /** 组织架构只读：HR/管理员 + 部门负责人；财务不可见组织管理 */
  const isOrgReader = isHr || isDeptManager;

  return {
    canSysAdmin: roleCode === ROLES.SYS_ADMIN,
    canHr: isHr,
    canFinance: isFinanceFamily,
    canFinanceManager: isFinanceManager,
    canManager: roleCode === ROLES.DEPT_MANAGER,
    canEmployee: roles.includes(ROLES.EMPLOYEE),
    canPortal: roles.includes(ROLES.EMPLOYEE),
    /** 管理端薪资全量（账套/核算等）；SYS_ADMIN 禁入 */
    canViewPayroll:
      roleCode !== ROLES.SYS_ADMIN &&
      (has('payroll:view') ||
        roleIn(roleCode, [ROLES.HR_STAFF, ROLES.FINANCE, ROLES.FINANCE_MANAGER])),
    /** 门户本人工资条（含 SYS_ADMIN 本人；≠ 管理端薪资全量） */
    canViewOwnPayslip:
      roleCode === ROLES.SYS_ADMIN ||
      roleCode === ROLES.EMPLOYEE ||
      roles.includes(ROLES.EMPLOYEE) ||
      roleIn(roleCode, [
        ROLES.HR_STAFF,
        ROLES.FINANCE,
        ROLES.FINANCE_MANAGER,
        ROLES.DEPT_MANAGER,
      ]),
    /** 审批中心：HR/主管/财务经理（专员不可进） */
    canApprove:
      has('approval:handle') ||
      has('menu:workflow') ||
      isHr ||
      isFinanceManager ||
      roleCode === ROLES.DEPT_MANAGER,
    canImport: roleCode === ROLES.HR_STAFF,
    canManageOrg: !isFinanceFamily && (has('menu:org') || isOrgReader || has('org:dept:edit')),
    canViewDept:
      !isFinanceFamily &&
      (has('org:dept:view') ||
        has('org:dept:edit') ||
        has('menu:org') ||
        isOrgReader),
    canEditDept: !isFinanceFamily && (has('org:dept:edit') || isHr),
    canViewPosition:
      !isFinanceFamily &&
      (has('org:position:view') ||
        has('org:position:edit') ||
        has('menu:org') ||
        isOrgReader),
    canEditPosition: !isFinanceFamily && (has('org:position:edit') || isHr),
    canManageAttendance:
      !isFinanceFamily &&
      (has('attendance:manage') || has('menu:attendance') || isHr),
    canManageWorkflow:
      !isFinanceFamily &&
      (has('workflow:manage') ||
        has('menu:workflow') ||
        has('approval:handle') ||
        isHr ||
        roleCode === ROLES.DEPT_MANAGER),
    canManageResignation: isHr,
    canManageSystem: roleCode === ROLES.SYS_ADMIN,
    canViewEmployee:
      !isFinanceFamily &&
      (has('menu:employee') || isHr || roleCode === ROLES.DEPT_MANAGER),
    /** 已登录即可使用 AI 对话 */
    canUseAiAssistant: true,
    /** 仅 SYS_ADMIN 管理知识库 */
    canManageAiKnowledge: roleCode === ROLES.SYS_ADMIN,
    hasPermission: (code: string) => has(code),
  };
}
