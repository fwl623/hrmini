package com.company.hrms.module.payroll.dto;

import lombok.Data;

/* AdjustmentDTO */
@Data
public class AdjustmentDTO {
    private String itemCode;
    private Double adjustAmount;
    private String reason;
}
