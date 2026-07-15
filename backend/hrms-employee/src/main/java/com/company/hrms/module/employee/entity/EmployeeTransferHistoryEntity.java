package com.company.hrms.module.employee.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.company.hrms.common.entity.BaseEntity;

import java.time.LocalDate;

/**
 * 员工调岗历史
 * 对应表: employee_transfer_history
 */
@TableName("employee_transfer_history")
public class EmployeeTransferHistoryEntity extends BaseEntity {

    /** 员工ID */
    private Long employeeId;

    /** 调岗申请ID */
    private Long transferAppId;

    /** 原部门ID */
    private Long fromDepartmentId;

    /** 新部门ID */
    private Long toDepartmentId;

    /** 原职位ID */
    private Long fromPositionId;

    /** 新职位ID */
    private Long toPositionId;

    /** 调岗日期 */
    private LocalDate transferDate;

    /** 调岗原因 */
    private String reason;

    // ===== Getters & Setters =====

    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }

    public Long getTransferAppId() { return transferAppId; }
    public void setTransferAppId(Long transferAppId) { this.transferAppId = transferAppId; }

    public Long getFromDepartmentId() { return fromDepartmentId; }
    public void setFromDepartmentId(Long fromDepartmentId) { this.fromDepartmentId = fromDepartmentId; }

    public Long getToDepartmentId() { return toDepartmentId; }
    public void setToDepartmentId(Long toDepartmentId) { this.toDepartmentId = toDepartmentId; }

    public Long getFromPositionId() { return fromPositionId; }
    public void setFromPositionId(Long fromPositionId) { this.fromPositionId = fromPositionId; }

    public Long getToPositionId() { return toPositionId; }
    public void setToPositionId(Long toPositionId) { this.toPositionId = toPositionId; }

    public LocalDate getTransferDate() { return transferDate; }
    public void setTransferDate(LocalDate transferDate) { this.transferDate = transferDate; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
