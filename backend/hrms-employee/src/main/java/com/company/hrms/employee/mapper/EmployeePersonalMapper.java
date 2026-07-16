package com.company.hrms.employee.mapper;

import com.company.hrms.employee.entity.EmployeePersonal;
import org.apache.ibatis.annotations.Mapper;

/**
 * 员工个人信息 Mapper
 */
@Mapper
public interface EmployeePersonalMapper {
    EmployeePersonal selectById(Long employeeId);
    int insert(EmployeePersonal personal);
    int updateById(EmployeePersonal personal);
}
