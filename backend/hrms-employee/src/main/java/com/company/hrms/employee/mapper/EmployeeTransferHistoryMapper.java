package com.company.hrms.employee.mapper;

import com.company.hrms.employee.entity.EmployeeTransferHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 调岗历史 Mapper
 */
@Mapper
public interface EmployeeTransferHistoryMapper {
    List<EmployeeTransferHistory> selectByEmployeeId(@Param("employeeId") Long employeeId);
    int insert(EmployeeTransferHistory history);
}
