package com.company.hrms.module.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class BindMobileRequest {

    @NotBlank
    @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不正确")
    private String mobile;

    @NotBlank(message = "短信验证码不能为空")
    private String smsCode;

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public String getSmsCode() {
        return smsCode;
    }

    public void setSmsCode(String smsCode) {
        this.smsCode = smsCode;
    }
}
