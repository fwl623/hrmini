package com.company.hrms.employee.dto;

import lombok.Data;

/**
 * 修改密码
 * PUT /api/v1/profile/security/password
 */
@Data
public class PasswordChangeDTO {
    private String oldPassword;
    private String newPassword;
    private String confirmPassword;
}
