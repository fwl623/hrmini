package com.company.hrms.employee.mapper;

import com.company.hrms.employee.entity.EmployeeNoHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 工号复用历史 Mapper
 * <p>
 * 对应表 employee_no_history，用于工号生成时查询可复用工号。
 * 离职员工的工号可被同部门新员工复用。
 * </p>
 */
@Mapper
public interface EmployeeNoHistoryMapper {
    /** 查询指定年份和部门下可复用的工号列表 */
    List<EmployeeNoHistory> selectReusable(@Param("year") String year, @Param("deptCode") String deptCode);
    int insert(EmployeeNoHistory history);
    int updateById(EmployeeNoHistory history);
}
