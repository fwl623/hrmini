package com.company.hrms.module.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.hrms.module.auth.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    @Select("""
            SELECT r.code FROM sys_role r
            INNER JOIN sys_user_role ur ON ur.role_id = r.id
            WHERE ur.user_id = #{userId}
            """)
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);

    @Select("""
            SELECT r.data_scope FROM sys_role r
            INNER JOIN sys_user_role ur ON ur.role_id = r.id
            WHERE ur.user_id = #{userId}
            ORDER BY FIELD(r.data_scope, 'ALL', 'NONE_PAYROLL', 'PAYROLL', 'DEPT_TREE', 'SELF')
            LIMIT 1
            """)
    String selectPrimaryDataScope(@Param("userId") Long userId);

    @Select("""
            SELECT DISTINCT p.code FROM sys_permission p
            INNER JOIN sys_role_permission rp ON rp.permission_id = p.id
            INNER JOIN sys_user_role ur ON ur.role_id = rp.role_id
            WHERE ur.user_id = #{userId}
            """)
    List<String> selectPermissionCodesByUserId(@Param("userId") Long userId);

    @Select("""
            SELECT department_id FROM employee
            WHERE id = #{employeeId} AND deleted = 0
            LIMIT 1
            """)
    Long selectDeptIdByEmployeeId(@Param("employeeId") Long employeeId);

    /** 按角色查启用用户（审批派单用） */
    @Select("""
            SELECT u.id FROM sys_user u
            INNER JOIN sys_user_role ur ON ur.user_id = u.id
            INNER JOIN sys_role r ON r.id = ur.role_id
            WHERE r.code = #{roleCode} AND u.status = 1
            ORDER BY u.id ASC
            """)
    List<Long> selectUserIdsByRoleCode(@Param("roleCode") String roleCode);
}
