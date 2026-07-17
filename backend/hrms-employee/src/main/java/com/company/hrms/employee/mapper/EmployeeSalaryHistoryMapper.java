package com.company.hrms.employee.mapper;

import com.company.hrms.employee.entity.EmployeeSalaryHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 调薪历史 Mapper
 * <p>
 * 对应表 employee_salary_history，每次薪资档案编辑自动写入变更记录。
 * 用于薪资审计和变更追溯。
 * </p>
 */
@Mapper
public interface EmployeeSalaryHistoryMapper {
    List<EmployeeSalaryHistory> selectByEmployeeId(@Param("employeeId") Long employeeId);
    int insert(EmployeeSalaryHistory history);
}
