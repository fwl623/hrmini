package com.company.hrms.employee.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 员工主表实体
 * <p>
 * 对应表: employee (DDL #21)
 * 员工档案的核心表，存储员工的基础信息和工作信息。
 * 敏感字段（身份证号、银行卡号）存储在扩展表中（employee_personal、employee_bank）。
 * </p>
 */
@Data
public class Employee {

    /** 主键ID（作 employee_id 业务主键使用，永不复用） */
    private Long id;

    /** 工号，格式：年份(4位)+部门编码(2位)+序号(3位)，如 202401005 */
    private String employeeNo;

    /** 系统账号ID，关联 sys_user.id */
    private Long userId;

    /** 姓名 */
    private String name;

    /** 性别 MALE/FEMALE */
    private String gender;

    /** 手机号（登录账号，唯一索引 uk_mobile） */
    private String mobile;

    /** 邮箱 */
    private String email;

    /** 所属部门ID，关联 department.id */
    private Long departmentId;

    /** 职位ID，关联 position.id */
    private Long positionId;

    /** 职级，如 P5、M2 */
    private String grade;

    /** 直属上级ID，关联 employee.id */
    private Long managerId;

    /** 工作地点 */
    private String workLocation;

    /** 入职日期 */
    private LocalDate hireDate;

    /** 用工类型：fulltime/parttime/intern */
    private String employmentType;

    /** 在职状态：10=试用期 20=正式 30=待离职 40=已离职 */
    private Integer employmentStatus;

    /** 最后工作日 */
    private LocalDate lastWorkDay;

    /** 试用薪资比例 0.80~1.00 */
    private BigDecimal probationPayRatio;

    /** 试用期结束日 */
    private LocalDate probationEndDate;

    /** 逻辑删除：0=否 1=是 */
    private Integer deleted;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 列表查询 JOIN 部门名（非表字段） */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String departmentName;

    /** 列表查询 JOIN 职位名（非表字段） */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String positionName;
}
