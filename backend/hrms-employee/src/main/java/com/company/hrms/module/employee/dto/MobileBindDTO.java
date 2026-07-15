package com.company.hrms.module.employee.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 手机绑定请求
 * POST /api/v1/profile/security/mobile/bind
 */
@Data
@Schema(description = "手机绑定请求")
public class MobileBindDTO {

    @NotBlank
    @Pattern(regexp = "^1[3-9]\\d{9}$")
    @Schema(description = "手机号")
    private String mobile;

    @NotBlank
    @Schema(description = "短信验证码")
    private String smsCode;
}
