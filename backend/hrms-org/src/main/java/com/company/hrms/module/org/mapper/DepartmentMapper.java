package com.company.hrms.module.org.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.hrms.module.org.entity.Department;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DepartmentMapper extends BaseMapper<Department> {

    @Select("SELECT COUNT(1) FROM department WHERE parent_id = #{parentId} AND deleted = 0")
    int countChildren(@Param("parentId") Long parentId);

    /** 含已逻辑删除，用于 uk_code 唯一校验 */
    @Select("SELECT COUNT(1) FROM department WHERE code = #{code}")
    int countByCode(@Param("code") String code);

    @Select("SELECT COUNT(1) FROM department WHERE code = #{code} AND id <> #{excludeId}")
    int countByCodeExclude(@Param("code") String code, @Param("excludeId") Long excludeId);
}
