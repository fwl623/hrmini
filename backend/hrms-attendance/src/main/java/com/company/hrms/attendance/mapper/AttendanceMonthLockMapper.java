package com.company.hrms.attendance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.hrms.attendance.entity.AttendanceMonthLock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * MyBatis-Plus Mapper 接口，操作 {@link AttendanceMonthLock} 实体。
 */
@Mapper
public interface AttendanceMonthLockMapper extends BaseMapper<AttendanceMonthLock> {

    /**
     * 根据账期查询锁定状态
     */
    @Select("SELECT * FROM attendance_month_lock WHERE `year_month` = #{period} LIMIT 1")
    AttendanceMonthLock selectByPeriod(@Param("period") String period);
}
