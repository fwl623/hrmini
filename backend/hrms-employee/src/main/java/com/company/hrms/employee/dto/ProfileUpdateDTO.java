package com.company.hrms.employee.dto;

import lombok.Data;

/**
 * 门户编辑本人档案
 * PUT /api/v1/profile/me
 * 白名单：email, residenceAddress, emergencyContact, emergencyPhone
 */
@Data
public class ProfileUpdateDTO {
    private String email;
    private String residenceAddress;
    private String emergencyContact;
    private String emergencyPhone;
}
