package com.company.hrms.employee.mapper;

import com.company.hrms.employee.entity.EmployeeSalaryProfile;
import org.apache.ibatis.annotations.Mapper;

/**
 * 员工薪资档案 Mapper
 * <p>
 * 对应表 employee_salary_profile，存储算薪参数（基本工资/社保基数等）。
 * </p>
 */
@Mapper
public interface EmployeeSalaryProfileMapper {
    EmployeeSalaryProfile selectByEmployeeId(Long employeeId);
    int insert(EmployeeSalaryProfile profile);
    int updateById(EmployeeSalaryProfile profile);
}
