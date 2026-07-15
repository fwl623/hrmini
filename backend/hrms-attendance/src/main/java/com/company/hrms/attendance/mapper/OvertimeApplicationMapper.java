package com.company.hrms.attendance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.hrms.attendance.entity.OvertimeApplication;
import org.apache.ibatis.annotations.Mapper;

/**
 * 加班申请 Mapper
 */
@Mapper
public interface OvertimeApplicationMapper extends BaseMapper<OvertimeApplication> {
}
