package com.company.hrms.module.payroll.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

/* BatchDetailVO */
@Data
public class BatchDetailVO {
    private Long id;
    private String period;
    private String status;
    private int totalCount;
    private int successCount;
    private int anomalyCount;
    private int progress;
}
