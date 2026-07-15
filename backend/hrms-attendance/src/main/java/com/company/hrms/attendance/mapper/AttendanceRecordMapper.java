package com.company.hrms.attendance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.hrms.attendance.entity.AttendanceRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

/**
 * 打卡流水 Mapper
 */
@Mapper
public interface AttendanceRecordMapper extends BaseMapper<AttendanceRecord> {

    /**
     * 查询员工某日某类型的打卡记录
     */
    @Select("SELECT * FROM attendance_record WHERE employee_id = #{employeeId} AND punch_date = #{punchDate} AND punch_type = #{punchType} LIMIT 1")
    AttendanceRecord selectByEmployeeAndDateAndType(@Param("employeeId") Long employeeId,
                                                    @Param("punchDate") LocalDate punchDate,
                                                    @Param("punchType") String punchType);

    /**
     * 查询员工某日的所有打卡记录
     */
    @Select("SELECT * FROM attendance_record WHERE employee_id = #{employeeId} AND punch_date = #{punchDate}")
    List<AttendanceRecord> selectByEmployeeAndDate(@Param("employeeId") Long employeeId,
                                                   @Param("punchDate") LocalDate punchDate);

    /**
     * 查询员工某日最早上班打卡
     */
    @Select("SELECT * FROM attendance_record WHERE employee_id = #{employeeId} AND punch_date = #{punchDate} AND punch_type = 'IN' ORDER BY punch_time ASC LIMIT 1")
    AttendanceRecord selectEarliestInByEmployeeAndDate(@Param("employeeId") Long employeeId,
                                                       @Param("punchDate") LocalDate punchDate);

    /**
     * 查询员工某日最晚下班打卡
     */
    @Select("SELECT * FROM attendance_record WHERE employee_id = #{employeeId} AND punch_date = #{punchDate} AND punch_type = 'OUT' ORDER BY punch_time DESC LIMIT 1")
    AttendanceRecord selectLatestOutByEmployeeAndDate(@Param("employeeId") Long employeeId,
                                                      @Param("punchDate") LocalDate punchDate);
}
