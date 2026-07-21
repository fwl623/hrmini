package com.company.hrms.module.auth.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 部门负责人 → 自动授予 DEPT_MANAGER 的来源标记。
 */
@Mapper
public interface DeptHeadAutoRoleMapper {

    @Select("SELECT COUNT(1) FROM dept_head_auto_role WHERE user_id = #{userId}")
    int countByUserId(@Param("userId") Long userId);

    @Insert("""
            INSERT INTO dept_head_auto_role (user_id, employee_id)
            VALUES (#{userId}, #{employeeId})
            ON DUPLICATE KEY UPDATE employee_id = VALUES(employee_id)
            """)
    int upsert(@Param("userId") Long userId, @Param("employeeId") Long employeeId);

    @Delete("DELETE FROM dept_head_auto_role WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);
}
