import { ROLES, type RoleCode } from '@/constants/roles';

function roleIn(role: string | undefined, list: readonly RoleCode[]): boolean {
  return !!role && (list as readonly string[]).includes(role);
}

/**
 * Umi access 权限（对齐 PRD §2.1 / §2.2）
 * 菜单门控优先认权限码（角色管理可分配）；角色作兼容兜底。
 * 数据权限仍由后端 DataScope 保证。
 *
 * DEPT_MANAGER：组织架构只读 + 本部门花名册 + 审批中心；无增删改组织 / 手机号变更 / 入转调离管理台
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
  /** 组织架构只读：HR/管理员 + 部门负责人；财务不可见；编辑仍仅 HR/管理员 */
  const isOrgReader = isHr || isDeptManager;

  return {
    canSysAdmin: roleCode === ROLES.SYS_ADMIN,
    canHr: isHr,
    canFinance: isFinanceFamily,
    canFinanceManager: isFinanceManager,
    canManager: isDeptManager,
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
    /** 审批中心：权限码或 HR/主管/财务经理（下属请假/转正/调岗/离职待办） */
    canApprove:
      has('approval:handle') ||
      has('menu:workflow') ||
      has('menu:approval') ||
      isHr ||
      isFinanceManager ||
      isDeptManager,
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
    /** 考勤管理台：HR；部门主管走审批中心批假，不进本组管理菜单 */
    canManageAttendance:
      !isFinanceFamily &&
      (has('attendance:manage') || has('menu:attendance') || isHr),
    /**
     * 入转调离管理台（入职/转正/调岗发起与列表）：仅 HR/管理员或显式权限码。
     * 部门主管不进此菜单，仅在审批中心处理待办。
     */
    canManageWorkflow:
      !isFinanceFamily &&
      !isDeptManager &&
      (has('workflow:manage') ||
        has('menu:onboarding') ||
        isHr),
    canManageResignation: (isHr || has('menu:onboarding')) && !isDeptManager,
    /** 手机号变更 HR 待办：仅 HR/管理员 */
    canManageMobileChange: isHr,
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
      (has('menu:employee') || isHr || isDeptManager),
    /** 人力资源数据概览：HR/主管/管理员；财务可看成本相关块 */
    canViewAnalytics:
      isHr ||
      isDeptManager ||
      roleCode === ROLES.SYS_ADMIN ||
      isFinanceFamily,
    /** AI 对话 / 知识库：按权限码；有知识库权限时也显示助理菜单父级 */
    canUseAiAssistant: has('ai:chat') || has('ai:knowledge:manage'),
    /** 知识库管理：认 ai:knowledge:manage（不再写死仅 SYS_ADMIN） */
    canManageAiKnowledge:
      roleCode === ROLES.SYS_ADMIN || has('ai:knowledge:manage'),
    hasPermission: (code: string) => has(code),
  };
}
