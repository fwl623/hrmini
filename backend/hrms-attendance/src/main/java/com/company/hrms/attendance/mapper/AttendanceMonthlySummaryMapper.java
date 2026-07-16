package com.company.hrms.attendance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.hrms.attendance.entity.AttendanceMonthlySummary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 月考勤汇总 Mapper
 */
@Mapper
public interface AttendanceMonthlySummaryMapper extends BaseMapper<AttendanceMonthlySummary> {

    /**
     * 查询员工某月考勤汇总
     */
    @Select("SELECT * FROM attendance_monthly_summary WHERE employee_id = #{employeeId} AND period = #{period}")
    AttendanceMonthlySummary selectByEmployeeAndPeriod(@Param("employeeId") Long employeeId,
                                                       @Param("period") String period);
}
