package com.company.hrms.employee.mapper;

import com.company.hrms.employee.entity.EmployeeSalaryHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 调薪历史 Mapper
 */
@Mapper
public interface EmployeeSalaryHistoryMapper {
    List<EmployeeSalaryHistory> selectByEmployeeId(@Param("employeeId") Long employeeId);
    int insert(EmployeeSalaryHistory history);
}
