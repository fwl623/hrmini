package com.company.hrms.module.employee.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 员工门户个人信息VO
 * GET /api/v1/profile/me
 */
@Data
@Schema(description = "门户个人信息")
public class ProfileVO {

    @Schema(description = "员工ID")
    private Long employeeId;

    @Schema(description = "工号")
    private String empNo;

    @Schema(description = "姓名")
    private String name;

    @Schema(description = "手机号（脱敏）")
    private String mobile;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "部门名称")
    private String department;

    @Schema(description = "职位名称")
    private String position;

    @Schema(description = "职级")
    private String grade;

    @Schema(description = "直属上级姓名")
    private String managerName;

    @Schema(description = "入职日期")
    private String hireDate;

    @Schema(description = "现居地址")
    private String residenceAddress;

    @Schema(description = "紧急联系人")
    private String emergencyContact;

    @Schema(description = "紧急联系人电话")
    private String emergencyPhone;

    @Schema(description = "可编辑字段白名单")
    private java.util.Set<String> editableFields;
}
