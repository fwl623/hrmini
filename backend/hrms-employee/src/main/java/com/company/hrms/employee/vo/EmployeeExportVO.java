package com.company.hrms.employee.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.time.LocalDate;

/**
 * 员工花名册导出 VO
 * <p>
 * 用于 Excel 导出的数据模型，使用 @ExcelProperty 注解定义列名。
 * </p>
 */
@Data
public class EmployeeExportVO {

    @ExcelProperty("工号")
    private String empNo;

    @ExcelProperty("姓名")
    private String name;

    @ExcelProperty("部门")
    private String department;

    @ExcelProperty("职位")
    private String position;

    @ExcelProperty("职级")
    private String grade;

    @ExcelProperty("在职状态")
    private String employmentStatus;

    @ExcelProperty("入职日期")
    private LocalDate hireDate;
}
