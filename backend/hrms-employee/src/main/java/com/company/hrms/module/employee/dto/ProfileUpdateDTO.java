package com.company.hrms.module.employee.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 员工门户个人信息编辑请求
 * PUT /api/v1/profile/me
 * 仅允许编辑：email, residenceAddress, emergencyContact, emergencyPhone
 */
@Data
@Schema(description = "门户个人信息编辑请求")
public class ProfileUpdateDTO {

    @Email
    @Size(max = 128)
    @Schema(description = "邮箱")
    private String email;

    @Size(max = 256)
    @Schema(description = "现居地址")
    private String residenceAddress;

    @Size(max = 64)
    @Schema(description = "紧急联系人姓名")
    private String emergencyContact;

    @Schema(description = "紧急联系人电话")
    private String emergencyPhone;
}
