package com.company.hrms.module.employee.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改密码请求
 * PUT /api/v1/profile/security/password
 */
@Data
@Schema(description = "修改密码请求")
public class PasswordChangeDTO {

    @NotBlank
    @Schema(description = "旧密码")
    private String oldPassword;

    @NotBlank
    @Size(min = 8)
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$",
             message = "密码需8位以上，包含大小写字母+数字")
    @Schema(description = "新密码（8位以上，包含大小写字母+数字）")
    private String newPassword;

    @NotBlank
    @Schema(description = "确认新密码")
    private String confirmPassword;
}
