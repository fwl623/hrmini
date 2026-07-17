package com.company.hrms.module.payroll.dto;

import lombok.Data;
import java.math.BigDecimal;

/* SchemeItemDTO */
@Data
public class SchemeItemDTO {
    private String itemCode;
    private String itemName;
    private String itemType;      // FIXED/VARIABLE/ATTENDANCE_DEDUCT/SS_DEDUCT/HF_DEDUCT/TAX
    private String calcRule;
    private String baseField;
    private BigDecimal ratio;
    private Integer sortOrder;
}
