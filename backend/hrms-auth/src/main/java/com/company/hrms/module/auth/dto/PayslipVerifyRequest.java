package com.company.hrms.module.auth.dto;

import jakarta.validation.constraints.NotBlank;

public class PayslipVerifyRequest {

    @NotBlank
    private String verifyType;

    @NotBlank
    private String verifyCode;

    public String getVerifyType() {
        return verifyType;
    }

    public void setVerifyType(String verifyType) {
        this.verifyType = verifyType;
    }

    public String getVerifyCode() {
        return verifyCode;
    }

    public void setVerifyCode(String verifyCode) {
        this.verifyCode = verifyCode;
    }
}
