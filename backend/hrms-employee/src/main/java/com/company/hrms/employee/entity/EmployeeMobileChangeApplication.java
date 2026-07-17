package com.company.hrms.employee.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 手机号变更申请表实体
 * <p>
 * 对应表: employee_mobile_change_application (DDL #31)
 * 员工发起手机号变更申请后创建记录，走 MOBILE_CHANGE 审批流程。
 * 审批通过后同步更新 employee.mobile 和 sys_user.username。
 * </p>
 */
@Data
public class EmployeeMobileChangeApplication {

    /** 主键ID */
    private Long id;

    /** 审批实例ID，关联 approval_instance.id（C组审批引擎） */
    private Long instanceId;

    /** 员工ID，关联 employee.id */
    private Long employeeId;

    /** 系统用户ID，关联 sys_user.id */
    private Long userId;

    /** 旧手机号（变更前） */
    private String oldMobile;

    /** 新手机号（变更目标） */
    private String newMobile;

    /** 提交前已验证新号：0=否 1=是 */
    private Integer smsVerified;

    /** 变更原因 */
    private String reason;

    /** 申请状态：PENDING=审批中 APPROVED=已通过 REJECTED=已驳回 CANCELLED=已撤销 */
    private String status;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
