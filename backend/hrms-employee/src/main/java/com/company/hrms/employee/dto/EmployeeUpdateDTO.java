package com.company.hrms.employee.dto;

import lombok.Data;
import java.time.LocalDate;

/**
 * 员工编辑请求（HR管理端）
 * PUT /api/v1/employees/{id}
 * 仅支持白名单字段，非白名单返回 20003
 */
@Data
public class EmployeeUpdateDTO {
    private String name;
    private String gender;       // MALE/FEMALE
    private String email;
    private LocalDate birthday;
    private String residenceAddress; // 现居地址
    private String emergencyContact;
    private String emergencyPhone;
    private String workLocation;
}
