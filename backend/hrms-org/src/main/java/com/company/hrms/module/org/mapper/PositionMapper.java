package com.company.hrms.module.org.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.hrms.module.org.entity.Position;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PositionMapper extends BaseMapper<Position> {

    @Select("SELECT COUNT(1) FROM position WHERE department_id = #{departmentId} AND deleted = 0")
    int countByDepartmentId(@Param("departmentId") Long departmentId);

    /** 合并时：源部门职位改挂目标部门 */
    @Update("""
            UPDATE position SET department_id = #{toDeptId}, updated_at = NOW()
            WHERE department_id = #{fromDeptId} AND deleted = 0
            """)
    int reassignDepartment(@Param("fromDeptId") Long fromDeptId, @Param("toDeptId") Long toDeptId);
}
