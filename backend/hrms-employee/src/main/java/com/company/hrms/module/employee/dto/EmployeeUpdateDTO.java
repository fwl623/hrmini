package com.company.hrms.module.employee.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 员工编辑请求（管理端）
 * PUT /api/v1/employees/{id}
 * 仅允许编辑白名单字段：name, gender, email, birthday, address, emergencyContact, emergencyPhone
 * 禁止直接修改：mobile, departmentId, positionId（须走审批流程）
 */
@Data
@Schema(description = "员工编辑请求（管理端白名单字段）")
public class EmployeeUpdateDTO {

    @Size(max = 64)
    @Schema(description = "姓名")
    private String name;

    @Schema(description = "性别 MALE/FEMALE")
    private String gender;

    @Email
    @Size(max = 128)
    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "生日 yyyy-MM-dd")
    private LocalDate birthday;

    @Size(max = 256)
    @Schema(description = "地址")
    private String address;

    @Size(max = 64)
    @Schema(description = "紧急联系人姓名")
    private String emergencyContact;

    @Schema(description = "紧急联系人电话")
    private String emergencyPhone;

    @Size(max = 128)
    @Schema(description = "工作地点")
    private String workLocation;
}
