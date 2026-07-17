package com.company.hrms.employee.mapper;

import com.company.hrms.employee.entity.EmployeeTransferHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 调岗历史 Mapper
 * <p>
 * 对应表 employee_transfer_history，记录员工调岗记录（时间倒序）。
 * </p>
 */
@Mapper
public interface EmployeeTransferHistoryMapper {
    List<EmployeeTransferHistory> selectByEmployeeId(@Param("employeeId") Long employeeId);
    int insert(EmployeeTransferHistory history);
}
