package com.company.hrms.attendance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.hrms.attendance.entity.AttendanceGroupScope;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 考勤组适用范围 Mapper
 */
@Mapper
public interface AttendanceGroupScopeMapper extends BaseMapper<AttendanceGroupScope> {

    /**
     * 根据考勤组ID查询适用范围
     */
    List<AttendanceGroupScope> selectByGroupId(@Param("groupId") Long groupId);

    /**
     * 根据考勤组ID删除适用范围
     */
    int deleteByGroupId(@Param("groupId") Long groupId);
}
