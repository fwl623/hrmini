package com.company.hrms.module.payroll.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 薪资批次明细导出 VO
 * <p>
 * 用于 Excel 导出的数据模型。
 * </p>
 */
@Data
public class PayrollDetailExportVO {

    @ExcelProperty("员工ID")
    private Long employeeId;

    @ExcelProperty("姓名")
    private String employeeName;

    @ExcelProperty("应发金额")
    private BigDecimal grossSalary;

    @ExcelProperty("实发金额")
    private BigDecimal netSalary;

    @ExcelProperty("核算状态")
    private String calcStatus;

    @ExcelProperty("异常标记")
    private String anomalyFlags;

    @ExcelProperty("手动调整")
    private String manualAdjusted;
}
