package com.company.hrms.employee.entity;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 调岗历史表实体
 * <p>
 * 对应表: employee_transfer_history (DDL #39)
 * 记录员工每次调岗的详细信息（原部门/新部门、原职位/新职位等）。
 * 调岗审批通过后自动写入一条记录，按时间倒序展示。
 * </p>
 */
@Data
public class EmployeeTransferHistory {

    /** 主键ID */
    private Long id;

    /** 员工ID，关联 employee.id */
    private Long employeeId;

    /** 调岗申请ID，关联 transfer_application.id */
    private Long transferAppId;

    /** 原部门ID，关联 department.id */
    private Long fromDepartmentId;

    /** 新部门ID，关联 department.id */
    private Long toDepartmentId;

    /** 原职位ID，关联 position.id */
    private Long fromPositionId;

    /** 新职位ID，关联 position.id */
    private Long toPositionId;

    /** 调岗日期 */
    private LocalDate transferDate;

    /** 调岗原因 */
    private String reason;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
