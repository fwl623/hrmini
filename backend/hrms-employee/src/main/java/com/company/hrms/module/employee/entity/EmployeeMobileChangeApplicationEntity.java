package com.company.hrms.module.employee.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.company.hrms.common.entity.BaseEntity;
import com.company.hrms.module.employee.enums.MobileChangeStatus;

/**
 * 手机号变更申请
 * 对应表: employee_mobile_change_application
 */
@TableName("employee_mobile_change_application")
public class EmployeeMobileChangeApplicationEntity extends BaseEntity {

    /** 审批实例ID */
    private Long instanceId;

    /** 员工ID */
    private Long employeeId;

    /** 用户ID */
    private Long userId;

    /** 原手机号 */
    private String oldMobile;

    /** 新手机号 */
    private String newMobile;

    /** 是否已验证新手机号 */
    private Boolean smsVerified;

    /** 变更原因 */
    private String reason;

    /** 状态 PENDING / APPROVED / REJECTED / CANCELLED */
    private MobileChangeStatus status;

    // ===== Getters & Setters =====

    public Long getInstanceId() { return instanceId; }
    public void setInstanceId(Long instanceId) { this.instanceId = instanceId; }

    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getOldMobile() { return oldMobile; }
    public void setOldMobile(String oldMobile) { this.oldMobile = oldMobile; }

    public String getNewMobile() { return newMobile; }
    public void setNewMobile(String newMobile) { this.newMobile = newMobile; }

    public Boolean getSmsVerified() { return smsVerified; }
    public void setSmsVerified(Boolean smsVerified) { this.smsVerified = smsVerified; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public MobileChangeStatus getStatus() { return status; }
    public void setStatus(MobileChangeStatus status) { this.status = status; }
}
