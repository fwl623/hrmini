package com.company.hrms.module.attendance.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 预览请假天数 VO
 */
@Data
@AllArgsConstructor
public class CalcDaysVO {
    private BigDecimal days;
}
