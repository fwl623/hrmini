import { ROLES, type RoleCode } from '@/constants/roles';

function roleIn(role: string | undefined, list: readonly RoleCode[]): boolean {
  return !!role && (list as readonly string[]).includes(role);
}

/**
 * Umi access 权限（对齐 PRD §2.1 / §2.2）
 * 菜单门控优先认权限码（角色管理可分配）；角色作兼容兜底。
 * 数据权限仍由后端 DataScope 保证。
 *
 * FINANCE（专员）：薪资全量 + 本人档案；无审批中心
 * FINANCE_MANAGER（经理）：同上 + 审批中心（调岗调薪等待办）
 */
export default function access(initialState: API.InitialState) {
  const { roleCode, permissions = [], roles = [] } = initialState?.currentUser ?? {};
  const has = (code: string) => permissions.includes(code);
  /** 人事业务主角色（含管理员配置侧）；不含财务 */
  const isHr = roleIn(roleCode, [ROLES.SYS_ADMIN, ROLES.HR_STAFF]);
  /** 组织架构可读：HR/管理员/部门主管；财务不可见组织管理 */
  const isOrgReader = isHr || roleCode === ROLES.DEPT_MANAGER;
  const isFinanceFamily = roleIn(roleCode, [ROLES.FINANCE, ROLES.FINANCE_MANAGER]);
  const isFinanceManager = roleCode === ROLES.FINANCE_MANAGER;

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
        has('menu:payroll') ||
        roleIn(roleCode, [ROLES.HR_STAFF, ROLES.FINANCE, ROLES.FINANCE_MANAGER])),
    /** 门户本人工资条（≠ 管理端薪资全量）；SYS_ADMIN 功能账号不进个人中心 */
    canViewOwnPayslip:
      roleCode === ROLES.EMPLOYEE ||
      roles.includes(ROLES.EMPLOYEE) ||
      roleIn(roleCode, [
        ROLES.HR_STAFF,
        ROLES.FINANCE,
        ROLES.FINANCE_MANAGER,
        ROLES.DEPT_MANAGER,
      ]),
    /** 审批中心：权限码或 HR/主管/财务经理 */
    canApprove:
      has('approval:handle') ||
      has('menu:workflow') ||
      has('menu:approval') ||
      isHr ||
      isFinanceManager ||
      roleCode === ROLES.DEPT_MANAGER,
    canImport: roleCode === ROLES.HR_STAFF,
    canManageOrg: !isFinanceFamily && (has('menu:org') || isHr || has('org:dept:edit')),
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
        has('menu:onboarding') ||
        has('approval:handle') ||
        isHr ||
        roleCode === ROLES.DEPT_MANAGER),
    canManageResignation: isHr || has('menu:onboarding'),
    /** 系统设置：认 menu:system / system:* 权限码（角色管理可赋给任意角色） */
    canManageSystem:
      roleCode === ROLES.SYS_ADMIN ||
      has('menu:system') ||
      has('system:user:view') ||
      has('system:user:edit') ||
      has('system:role:view') ||
      has('system:role:edit'),
    canViewEmployee:
      !isFinanceFamily &&
      (has('menu:employee') || isHr || roleCode === ROLES.DEPT_MANAGER),
    /** AI 对话 / 知识库：按权限码；有知识库权限时也显示助理菜单父级 */
    canUseAiAssistant: has('ai:chat') || has('ai:knowledge:manage'),
    /** 知识库管理：认 ai:knowledge:manage（不再写死仅 SYS_ADMIN） */
    canManageAiKnowledge:
      roleCode === ROLES.SYS_ADMIN || has('ai:knowledge:manage'),
    hasPermission: (code: string) => has(code),
  };
}
