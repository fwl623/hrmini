package com.company.hrms.employee.mapper;

import com.company.hrms.employee.entity.EmployeePersonal;
import org.apache.ibatis.annotations.Mapper;

/**
 * 员工个人信息 Mapper
 * <p>
 * 对应表 employee_personal，身份证号使用 AES-256-GCM 加密存储。
 * 通过 id_number_hash 的 SHA-256 索引支持精确检索。
 * </p>
 */
@Mapper
public interface EmployeePersonalMapper {
    EmployeePersonal selectById(Long employeeId);
    int insert(EmployeePersonal personal);
    int updateById(EmployeePersonal personal);
}
