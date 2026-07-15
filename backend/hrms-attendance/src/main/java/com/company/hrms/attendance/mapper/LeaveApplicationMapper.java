package com.company.hrms.attendance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.hrms.attendance.entity.LeaveApplication;
import org.apache.ibatis.annotations.Mapper;

/**
 * 请假申请 Mapper
 */
@Mapper
public interface LeaveApplicationMapper extends BaseMapper<LeaveApplication> {
}
