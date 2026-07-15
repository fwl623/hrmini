package com.company.hrms.module.employee.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 手机号变更申请
 * POST /api/v1/profile/mobile-change-applications
 */
@Data
@Schema(description = "手机号变更申请")
public class MobileChangeApplyDTO {

    @NotBlank
    @Pattern(regexp = "^1[3-9]\\d{9}$")
    @Schema(description = "新手机号")
    private String newMobile;

    @NotBlank
    @Schema(description = "短信验证码")
    private String smsCode;

    @Schema(description = "变更原因")
    private String reason;
}
