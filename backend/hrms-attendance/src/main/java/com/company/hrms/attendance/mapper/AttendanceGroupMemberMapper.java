package com.company.hrms.attendance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.hrms.attendance.entity.AttendanceGroupMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 考勤组成员 Mapper
 */
@Mapper
public interface AttendanceGroupMemberMapper extends BaseMapper<AttendanceGroupMember> {

    /**
     * 根据考勤组ID查询成员
     */
    List<AttendanceGroupMember> selectByGroupId(@Param("groupId") Long groupId);

    /**
     * 根据考勤组ID删除所有成员
     */
    int deleteByGroupId(@Param("groupId") Long groupId);

    /**
     * 批量删除指定员工在所有考勤组中的成员关系（用于换组）
     */
    int deleteByEmployeeIds(@Param("employeeIds") List<Long> employeeIds);

    /**
     * 批量插入成员
     */
    int batchInsert(@Param("list") List<AttendanceGroupMember> list);
}
