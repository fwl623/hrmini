package com.company.hrms.common.enums;

/**
 * 预置角色编码（不可随意增删）。
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
