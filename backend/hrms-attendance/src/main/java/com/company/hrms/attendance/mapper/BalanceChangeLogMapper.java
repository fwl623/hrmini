package com.company.hrms.attendance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.hrms.attendance.entity.BalanceChangeLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 假期余额变动日志 Mapper
 */
@Mapper
public interface BalanceChangeLogMapper extends BaseMapper<BalanceChangeLog> {
}
