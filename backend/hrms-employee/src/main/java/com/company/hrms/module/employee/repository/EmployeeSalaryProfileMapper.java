package com.company.hrms.module.employee.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.hrms.module.employee.entity.EmployeeSalaryProfileEntity;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

/**
 * 员工薪资档案 Mapper
 */
@Repository
public interface EmployeeSalaryProfileMapper extends BaseMapper<EmployeeSalaryProfileEntity> {

    /**
     * 根据员工ID查询薪资档案
     */
    EmployeeSalaryProfileEntity selectByEmployeeId(@Param("employeeId") Long employeeId);
}
