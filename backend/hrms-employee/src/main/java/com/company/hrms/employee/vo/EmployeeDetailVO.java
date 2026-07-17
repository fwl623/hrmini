package com.company.hrms.employee.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * 员工详情响应
 * <p>
 * 用于 GET /api/v1/employees/{id} 接口。
 * 敏感字段（身份证号、薪资信息等）由 FieldPermissionFilter 后置处理自动脱敏/置空。
 * 前端根据 fieldPermissions 映射控制 UI 展示。
 * </p>
 */
@Data
public class EmployeeDetailVO {

    // ========== 基础信息 ==========
    /** 员工ID */
    private Long employeeId;
    /** 工号 */
    private String empNo;
    /** 姓名 */
    private String name;
    /** 性别 MALE/FEMALE */
    private String gender;
    /** 手机号（脱敏：138****1234） */
    private String mobile;
    /** 邮箱 */
    private String email;

    // ========== 工作信息 ==========
    /** 部门ID */
    private Long departmentId;
    /** 部门名称 */
    private String department;
    /** 职位ID */
    private Long positionId;
    /** 职位名称 */
    private String position;
    /** 职级 */
    private String grade;
    /** 直属上级ID */
    private Long managerId;
    /** 直属上级姓名 */
    private String managerName;
    /** 工作地点 */
    private String workLocation;

    // ========== 状态与日期 ==========
    /** 在职状态（probation/regular/pending_resign/resigned） */
    private String employmentStatus;
    /** 用工类型（fulltime/parttime/intern） */
    private String employmentType;
    /** 入职日期 */
    private LocalDate hireDate;
    /** 试用期薪资比例 */
    private BigDecimal probationPayRatio;

    // ========== 敏感字段（脱敏后） ==========
    /** 身份证号（脱敏：3301**********1234） */
    private String idNumber;
    /** 生日 */
    private LocalDate birthday;
    /** 户籍地址 */
    private String householdAddress;
    /** 现居地址 */
    private String residenceAddress;
    /** 紧急联系人姓名 */
    private String emergencyContact;
    /** 紧急联系人电话 */
    private String emergencyPhone;
    /** 银行卡号（脱敏：****1234） */
    private String bankAccount;
    /** 开户行 */
    private String bankName;

    // ========== 权限元信息 ==========
    /** 字段权限映射，格式：fieldName -> "view" | "hidden" */
    private Map<String, String> fieldPermissions;
}
