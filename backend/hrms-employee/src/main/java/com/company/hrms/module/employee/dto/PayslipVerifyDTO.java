package com.company.hrms.module.employee.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 工资条二次验证请求
 * POST /api/v1/profile/payslips/verify
 */
@Data
@Schema(description = "工资条二次验证请求")
public class PayslipVerifyDTO {

    @NotBlank
    @Schema(description = "验证类型 PASSWORD/SMS")
    private String verifyType;

    @NotBlank
    @Schema(description = "验证码（密码或短信验证码）")
    private String verifyCode;
}
