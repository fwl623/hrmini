package com.company.hrms.payroll.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.hrms.payroll.entity.PayrollSchemeItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 账套工资项目 Mapper
 */
@Mapper
public interface PayrollSchemeItemMapper extends BaseMapper<PayrollSchemeItem> {

    /**
     * 根据账套ID查询工资项目
     */
    List<PayrollSchemeItem> selectBySchemeId(@Param("schemeId") Long schemeId);

    /**
     * 根据账套ID删除工资项目
     */
    int deleteBySchemeId(@Param("schemeId") Long schemeId);
}
