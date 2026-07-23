package com.company.hrms.module.attendance.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 月考勤汇总导出 VO
 * <p>
 * 用于 Excel 导出的数据模型。
 * </p>
 */
@Data
public class MonthlySummaryExportVO {

    @ExcelProperty("员工ID")
    private Long employeeId;

    @ExcelProperty("员工姓名")
    private String employeeName;

    @ExcelProperty("账期")
    private String period;

    @ExcelProperty("应出勤天数")
    private Integer shouldAttendDays;

    @ExcelProperty("实际出勤天数")
    private BigDecimal actualAttendDays;

    @ExcelProperty("迟到次数")
    private Integer lateCount;

    @ExcelProperty("早退次数")
    private Integer earlyLeaveCount;

    @ExcelProperty("旷工天数")
    private BigDecimal absentDays;

    @ExcelProperty("请假天数")
    private BigDecimal leaveDays;

    @ExcelProperty("加班时长")
    private BigDecimal overtimeHours;
}
