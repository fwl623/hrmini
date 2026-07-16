package com.company.hrms.employee.vo;

import lombok.Data;

import java.time.LocalDate;

/**
 * 员工列表响应项
 * <p>
 * 用于 GET /api/v1/employees 花名册分页查询的列表返回项。
 * 字段根据当前用户角色权限进行脱敏处理：
 * - HR_STAFF：查看全部
 * - DEPT_MANAGER：仅本部门及下级
 * - EMPLOYEE：仅本人信息
 * </p>
 */
@Data
public class EmployeeListVO {

    /** 员工ID（业务主键，永不复用） */
    private Long employeeId;

    /** 工号（格式：YYYY+部门编码+序号） */
    private String empNo;

    /** 姓名 */
    private String name;

    /** 部门名称（JOIN department 表） */
    private String department;

    /** 职位名称（JOIN position 表） */
    private String position;

    /** 职级（如 P5、M2） */
    private String grade;

    /** 在职状态（probation/regular/pending_resign/resigned） */
    private String employmentStatus;

    /** 入职日期 */
    private LocalDate hireDate;
}
