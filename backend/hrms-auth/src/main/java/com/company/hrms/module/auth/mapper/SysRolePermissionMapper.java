package com.company.hrms.module.auth.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SysRolePermissionMapper {

    @Insert("INSERT INTO sys_role_permission (role_id, permission_id) VALUES (#{roleId}, #{permissionId})")
    int insert(@Param("roleId") Long roleId, @Param("permissionId") Long permissionId);

    @Delete("DELETE FROM sys_role_permission WHERE role_id = #{roleId}")
    int deleteByRoleId(@Param("roleId") Long roleId);
}
