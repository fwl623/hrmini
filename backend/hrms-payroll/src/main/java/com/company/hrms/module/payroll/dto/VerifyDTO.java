package com.company.hrms.module.payroll.dto;

import lombok.Data;

/** 工资条二次验证请求 DTO */
@Data
public class VerifyDTO {
    private String password;
}
