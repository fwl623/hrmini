package com.company.hrms.employee.mapper;

import com.company.hrms.employee.entity.EmployeeBank;
import org.apache.ibatis.annotations.Mapper;

/**
 * 员工银行卡 Mapper
 * <p>
 * 对应表 employee_bank，银行卡号使用 AES-256-GCM 加密存储。
 * 仅展示后四位，完整卡号需二次验证。
 * </p>
 */
@Mapper
public interface EmployeeBankMapper {
    EmployeeBank selectById(Long employeeId);
    int insert(EmployeeBank bank);
    int updateById(EmployeeBank bank);
}
