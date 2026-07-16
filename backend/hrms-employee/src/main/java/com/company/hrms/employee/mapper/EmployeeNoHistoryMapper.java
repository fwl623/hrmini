package com.company.hrms.employee.mapper;

import com.company.hrms.employee.entity.EmployeeNoHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 工号复用历史 Mapper
 */
@Mapper
public interface EmployeeNoHistoryMapper {
    List<EmployeeNoHistory> selectReusable(@Param("year") String year, @Param("deptCode") String deptCode);
    int insert(EmployeeNoHistory history);
    int updateById(EmployeeNoHistory history);
}
