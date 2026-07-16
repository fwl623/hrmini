package com.company.hrms.employee.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工号复用历史表实体
 * <p>
 * 对应表: employee_no_history (DDL #28)
 * 记录工号的占用与释放历史。员工离职后工号可被同部门新员工复用。
 * 工号生成优先查 reuse_flag=1 的记录。
 * </p>
 */
@Data
public class EmployeeNoHistory {

    /** 主键ID */
    private Long id;

    /** 工号 */
    private String employeeNo;

    /** 年份 */
    private String year;

    /** 部门编码 */
    private String deptCode;

    /** 占用该工号的员工ID（离职后置空） */
    private Long employeeId;

    /** 复用标志：0=占用中 1=可复用 */
    private Integer reuseFlag;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
