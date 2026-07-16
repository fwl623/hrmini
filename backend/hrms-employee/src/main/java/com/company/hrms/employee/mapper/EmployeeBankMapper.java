package com.company.hrms.employee.mapper;

import com.company.hrms.employee.entity.EmployeeBank;
import org.apache.ibatis.annotations.Mapper;

/**
 * 员工银行卡 Mapper
 */
@Mapper
public interface EmployeeBankMapper {
    EmployeeBank selectById(Long employeeId);
    int insert(EmployeeBank bank);
    int updateById(EmployeeBank bank);
}
