package com.company.hrms.attendance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.hrms.attendance.entity.HolidayCalendar;
import org.apache.ibatis.annotations.Mapper;

/**
 * 法定节假日 Mapper
 */
@Mapper
public interface HolidayCalendarMapper extends BaseMapper<HolidayCalendar> {
}
