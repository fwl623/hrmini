package com.company.hrms.module.employee.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.hrms.common.entity.BaseEntity;
import com.company.hrms.common.annotation.EncryptedField;
import com.company.hrms.module.employee.enums.EmploymentStatus;
import com.company.hrms.module.employee.enums.EmploymentType;
import com.company.hrms.module.employee.enums.Gender;

import java.time.LocalDate;

/**
 * 员工主表
 * 对应表: employee
 */
@TableName("employee")
public class EmployeeEntity extends BaseEntity {

    /** 工号（展示用） */
    private String employeeNo;

    /** 系统账号ID */
    private Long userId;

    /** 姓名 */
    private String name;

    /** 性别 MALE/FEMALE */
    private Gender gender;

    /** 手机号（登录账号，唯一索引） */
    private String mobile;

    /** 邮箱 */
    private String email;

    /** 部门ID */
    private Long departmentId;

    /** 职位ID */
    private Long positionId;

    /** 职级，如 P5 */
    private String grade;

    /** 直属上级ID */
    private Long managerId;

    /** 工作地点 */
    private String workLocation;

    /** 入职日期 */
    private LocalDate hireDate;

    /** 用工类型 fulltime/parttime/intern */
    private EmploymentType employmentType;

    /** 在职状态 10=试用期 20=正式 30=待离职 40=已离职 */
    @TableField("employment_status")
    private EmploymentStatus employmentStatus;

    /** 最后工作日 */
    private LocalDate lastWorkDay;

    /** 试用薪资比例 0.80~1.00 */
    private java.math.BigDecimal probationPayRatio;

    // ===== Getters & Setters =====

    public String getEmployeeNo() { return employeeNo; }
    public void setEmployeeNo(String employeeNo) { this.employeeNo = employeeNo; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Gender getGender() { return gender; }
    public void setGender(Gender gender) { this.gender = gender; }

    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }

    public Long getPositionId() { return positionId; }
    public void setPositionId(Long positionId) { this.positionId = positionId; }

    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }

    public Long getManagerId() { return managerId; }
    public void setManagerId(Long managerId) { this.managerId = managerId; }

    public String getWorkLocation() { return workLocation; }
    public void setWorkLocation(String workLocation) { this.workLocation = workLocation; }

    public LocalDate getHireDate() { return hireDate; }
    public void setHireDate(LocalDate hireDate) { this.hireDate = hireDate; }

    public EmploymentType getEmploymentType() { return employmentType; }
    public void setEmploymentType(EmploymentType employmentType) { this.employmentType = employmentType; }

    public EmploymentStatus getEmploymentStatus() { return employmentStatus; }
    public void setEmploymentStatus(EmploymentStatus employmentStatus) { this.employmentStatus = employmentStatus; }

    public LocalDate getLastWorkDay() { return lastWorkDay; }
    public void setLastWorkDay(LocalDate lastWorkDay) { this.lastWorkDay = lastWorkDay; }

    public java.math.BigDecimal getProbationPayRatio() { return probationPayRatio; }
    public void setProbationPayRatio(java.math.BigDecimal probationPayRatio) { this.probationPayRatio = probationPayRatio; }
}
