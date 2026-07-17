package com.company.hrms.module.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.hrms.module.auth.entity.OperationLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLog> {
}
