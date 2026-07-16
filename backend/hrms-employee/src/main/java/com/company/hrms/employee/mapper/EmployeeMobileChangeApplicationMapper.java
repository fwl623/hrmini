package com.company.hrms.employee.mapper;

import com.company.hrms.employee.entity.EmployeeMobileChangeApplication;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 手机号变更申请 Mapper
 */
@Mapper
public interface EmployeeMobileChangeApplicationMapper {
    EmployeeMobileChangeApplication selectById(@Param("id") Long id);
    List<EmployeeMobileChangeApplication> selectByEmployeeId(@Param("employeeId") Long employeeId);
    List<EmployeeMobileChangeApplication> selectPending();
    int insert(EmployeeMobileChangeApplication app);
    int updateById(EmployeeMobileChangeApplication app);
}
