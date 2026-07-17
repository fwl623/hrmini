package com.company.hrms.workflow.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 入职等流程校验部门/职位是否存在（只读）。
 */
@Mapper
public interface OrgLookupMapper {

    @Select("SELECT COUNT(1) FROM department WHERE id = #{id} AND deleted = 0")
    int countDepartment(@Param("id") Long id);

    @Select("SELECT COUNT(1) FROM position WHERE id = #{id} AND deleted = 0")
    int countPosition(@Param("id") Long id);
}
