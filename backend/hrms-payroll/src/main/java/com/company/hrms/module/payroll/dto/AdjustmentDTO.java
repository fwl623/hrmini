package com.company.hrms.module.payroll.dto;

import lombok.Data;

@Data
public class AdjustmentDTO {
    private String itemCode;
    private Double adjustAmount;
    private String reason;
}
