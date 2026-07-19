package com.company.hrms.employee.mapper;

import com.company.hrms.employee.entity.EmployeeContract;
import org.apache.ibatis.annotations.Mapper;

/**
 * 员工合同 Mapper
 * <p>
 * 对应表 employee_contract，存储合同类型、期限、基本工资等信息。
 * 与 employee 一对一关系（uk_employee）。
 * </p>
 */
@Mapper
public interface EmployeeContractMapper {
    EmployeeContract selectByEmployeeId(Long employeeId);
    int insert(EmployeeContract contract);
    int updateById(EmployeeContract contract);

    /** 账套名称（跨表只读，避免 hrms-employee 依赖 payroll 模块） */
    @org.apache.ibatis.annotations.Select(
            "SELECT name FROM payroll_scheme WHERE id = #{schemeId} AND (deleted = 0 OR deleted IS NULL) LIMIT 1")
    String selectSchemeName(@org.apache.ibatis.annotations.Param("schemeId") Long schemeId);
}
