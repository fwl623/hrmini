package com.company.hrms.module.employee.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * 员工详情响应VO
 * GET /api/v1/employees/{id}
 * 敏感字段由 FieldPermissionFilter 自动脱敏/置空
 */
@Data
@Schema(description = "员工详情")
public class EmployeeDetailVO {

    // ===== 基础信息 =====
    @Schema(description = "员工ID")
    private Long employeeId;

    @Schema(description = "工号")
    private String empNo;

    @Schema(description = "姓名")
    private String name;

    @Schema(description = "性别")
    private String gender;

    @Schema(description = "手机号")
    private String mobile;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "生日")
    private LocalDate birthday;

    @Schema(description = "户籍地址")
    private String householdAddress;

    @Schema(description = "现居地址")
    private String residenceAddress;

    @Schema(description = "紧急联系人")
    private String emergencyContact;

    @Schema(description = "紧急联系人电话")
    private String emergencyPhone;

    // ===== 工作信息 =====
    @Schema(description = "部门ID")
    private Long departmentId;

    @Schema(description = "部门名称")
    private String department;

    @Schema(description = "职位ID")
    private Long positionId;

    @Schema(description = "职位名称")
    private String position;

    @Schema(description = "职级")
    private String grade;

    @Schema(description = "直属上级ID")
    private Long managerId;

    @Schema(description = "直属上级姓名")
    private String managerName;

    @Schema(description = "工作地点")
    private String workLocation;

    // ===== 状态与日期 =====
    @Schema(description = "在职状态")
    private String employmentStatus;

    @Schema(description = "用工类型")
    private String employmentType;

    @Schema(description = "入职日期")
    private LocalDate hireDate;

    @Schema(description = "试用薪资比例")
    private BigDecimal probationPayRatio;

    // ===== 敏感字段（脱敏后） =====
    @Schema(description = "身份证号（脱敏）")
    private String idNumber;

    @Schema(description = "银行卡号（脱敏）")
    private String bankAccount;

    @Schema(description = "开户行")
    private String bankName;

    // ===== 权限元信息 =====
    @Schema(description = "字段权限映射：field -> view/hidden")
    private Map<String, String> fieldPermissions;
}
