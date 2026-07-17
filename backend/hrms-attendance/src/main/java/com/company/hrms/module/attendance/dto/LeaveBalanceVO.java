package com.company.hrms.module.attendance.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 假期余额 VO
 */
@Data
@AllArgsConstructor
public class LeaveBalanceVO {
    private String leaveType;
    private BigDecimal balance;
}
