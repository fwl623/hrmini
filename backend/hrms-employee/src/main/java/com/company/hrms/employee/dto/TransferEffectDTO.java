package com.company.hrms.employee.dto;

import lombok.Data;

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
    private LocalDate effectiveDate;
    private String reason;
}
