package com.company.hrms.attendance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.hrms.attendance.entity.AttendanceSupplement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 补卡申请 Mapper
 */
@Mapper
public interface AttendanceSupplementMapper extends BaseMapper<AttendanceSupplement> {

    /**
     * 统计员工某月补卡次数
     */
    @Select("SELECT COUNT(*) FROM attendance_supplement WHERE employee_id = #{employeeId} AND DATE_FORMAT(created_at, '%Y-%m') = #{ym} AND status = 'APPROVED'")
    int countByEmployeeAndMonth(@Param("employeeId") Long employeeId, @Param("ym") String ym);
}
