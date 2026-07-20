package com.company.hrms.employee.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 调岗生效参数（由 workflow 审批通过后回调）。
 */
@Data
public class TransferEffectDTO {
    private Long transferAppId;
    private Long newDepartmentId;
    private Long newPositionId;
    private String newJobLevel;
    private Long newManagerId;
    /** 调岗后基本工资（绝对值）；有值则同步更新薪资档案 */
    private BigDecimal newBaseSalary;
    private LocalDate effectiveDate;
    private String reason;
}
