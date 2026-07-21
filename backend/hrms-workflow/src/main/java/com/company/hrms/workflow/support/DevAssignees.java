package com.company.hrms.workflow.support;

/**
 * 开发期写死审批人（真实 AssigneeResolver 后续补）。
 * 预留接口见 {@link AssigneeResolver}。
 */
public final class DevAssignees {

    /** 部门负责人 / 原部门 / 直属上级 */
    public static final long DEPT_MANAGER = 1002L;
    /** 新部门负责人 */
    public static final long NEW_DEPT_MANAGER = 1002L;
    /** HR 备案 / HR 审批（默认李四 userId=1001） */
    public static final long HR_STAFF = 1001L;
    /** 财务调薪确认（含调薪的调岗） */
    public static final long FINANCE = 1008L;

    private DevAssignees() {
    }
}
