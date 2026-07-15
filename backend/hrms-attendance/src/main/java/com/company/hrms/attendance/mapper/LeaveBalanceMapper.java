package com.company.hrms.attendance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.hrms.attendance.entity.LeaveBalance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 假期余额 Mapper
 */
@Mapper
public interface LeaveBalanceMapper extends BaseMapper<LeaveBalance> {

    /**
     * 查询员工某类型假期余额
     */
    @Select("SELECT * FROM leave_balance WHERE employee_id = #{employeeId} AND leave_type = #{leaveType} AND year = #{year}")
    LeaveBalance selectByEmployeeAndTypeAndYear(@Param("employeeId") Long employeeId,
                                                @Param("leaveType") String leaveType,
                                                @Param("year") Integer year);

    /**
     * 查询员工所有假期余额
     */
    @Select("SELECT * FROM leave_balance WHERE employee_id = #{employeeId}")
    List<LeaveBalance> selectByEmployeeId(@Param("employeeId") Long employeeId);
}
