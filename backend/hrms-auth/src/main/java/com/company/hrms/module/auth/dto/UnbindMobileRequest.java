package com.company.hrms.module.auth.dto;

import jakarta.validation.constraints.NotBlank;

public class UnbindMobileRequest {

    @NotBlank(message = "短信验证码不能为空")
    private String smsCode;

    public String getSmsCode() {
        return smsCode;
    }

    public void setSmsCode(String smsCode) {
        this.smsCode = smsCode;
    }
}
