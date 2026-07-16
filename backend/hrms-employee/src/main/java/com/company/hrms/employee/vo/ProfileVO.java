package com.company.hrms.employee.vo;

import lombok.Data;
import java.util.Set;

/**
 * 门户个人信息
 * GET /api/v1/profile/me
 */
@Data
public class ProfileVO {
    private Long employeeId;
    private String empNo;
    private String name;
    private String mobile;
    private String email;
    private String department;
    private String position;
    private String grade;
    private String hireDate;
    private String residenceAddress;
    private String emergencyContact;
    private String emergencyPhone;
    private Set<String> editableFields;
}
