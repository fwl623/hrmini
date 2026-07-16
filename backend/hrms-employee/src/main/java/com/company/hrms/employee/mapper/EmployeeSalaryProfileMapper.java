package com.company.hrms.employee.mapper;

import com.company.hrms.employee.entity.EmployeeSalaryProfile;
import org.apache.ibatis.annotations.Mapper;

/**
 * 员工薪资档案 Mapper
 */
@Mapper
public interface EmployeeSalaryProfileMapper {
    EmployeeSalaryProfile selectByEmployeeId(Long employeeId);
    int insert(EmployeeSalaryProfile profile);
    int updateById(EmployeeSalaryProfile profile);
}
