package com.company.hrms.module.employee.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.hrms.module.employee.entity.EmployeeResignationRequestEntity;
import org.springframework.stereotype.Repository;

/**
 * 员工离职申请 Mapper
 */
@Repository
public interface EmployeeResignationRequestMapper extends BaseMapper<EmployeeResignationRequestEntity> {
}
