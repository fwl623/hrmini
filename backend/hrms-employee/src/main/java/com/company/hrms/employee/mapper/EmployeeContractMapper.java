package com.company.hrms.employee.mapper;

import com.company.hrms.employee.entity.EmployeeContract;
import org.apache.ibatis.annotations.Mapper;

/**
 * 员工合同 Mapper
 */
@Mapper
public interface EmployeeContractMapper {
    EmployeeContract selectByEmployeeId(Long employeeId);
    int insert(EmployeeContract contract);
    int updateById(EmployeeContract contract);
}
