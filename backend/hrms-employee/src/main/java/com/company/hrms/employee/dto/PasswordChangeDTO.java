package com.company.hrms.employee.dto;

import lombok.Data;

/**
 * 修改密码请求参数
 * <p>
 * PUT /api/v1/profile/security/password
 * 需提供旧密码、新密码及确认密码。
 * 新密码需满足强度要求：8位以上，包含大小写字母+数字。
 * </p>
 */
@Data
public class PasswordChangeDTO {

    /** 当前密码（BCrypt 校验） */
    private String oldPassword;

    /** 新密码（8位以上，含大小写字母+数字） */
    private String newPassword;

    /** 确认新密码（须与 newPassword 一致） */
    private String confirmPassword;
}
