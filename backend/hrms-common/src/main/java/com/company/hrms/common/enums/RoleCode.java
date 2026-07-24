package com.company.hrms.common.enums;

/**
 * 预置角色编码（与 sys_role.code 一致，不可随意增删）。
 * <ul>
 *   <li>SYS_ADMIN — 系统管理员，非薪资全量（NONE_PAYROLL）</li>
 *   <li>HR_STAFF — 人事专员</li>
 *   <li>DEPT_MANAGER — 部门负责人（本部门树）</li>
 *   <li>FINANCE / FINANCE_MANAGER — 财务专员 / 财务经理</li>
 *   <li>EMPLOYEE — 普通员工（SELF）</li>
 * </ul>
 */
public enum RoleCode {

    SYS_ADMIN,
    HR_STAFF,
    DEPT_MANAGER,
    /** 财务专员：薪资核算/报表，无审批中心 */
    FINANCE,
    /** 财务经理：薪资域 + 调岗调薪等财务审批 */
    FINANCE_MANAGER,
    EMPLOYEE
}
