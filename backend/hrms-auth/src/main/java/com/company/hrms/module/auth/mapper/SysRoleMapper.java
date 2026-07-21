package com.company.hrms.module.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.hrms.module.auth.entity.SysRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {

    @Select("""
            SELECT permission_id FROM sys_role_permission
            WHERE role_id = #{roleId}
            """)
    List<Long> selectPermissionIdsByRoleId(@Param("roleId") Long roleId);

    @Select("SELECT id FROM sys_role WHERE code = #{code} LIMIT 1")
    Long selectIdByCode(@Param("code") String code);
}
