package com.company.hrms.module.auth.service;

/**
 * 部门负责人变更时同步 DEPT_MANAGER 角色。
 * <p>
 * 规则：新负责人若尚无该角色则自动授予并打上来源标记；
 * 旧负责人仅在「不再担任任何部门负责人」且角色来源为自动授予时回收。
 * <p>
 * userId 由调用方（org）从 employee 解析后传入，避免 auth 直查员工表。
 */
public interface DeptHeadRoleSyncService {

    /** 指定为部门负责人：无 DEPT_MANAGER 则自动授予并标记来源 */
    void grantAutoDeptManager(Long employeeId, Long userId);

    /**
     * 卸任负责人后尝试回收。
     *
     * @param remainingHeadDepts 落库后仍担任负责人的部门数；&gt;0 则不回收
     */
    void revokeAutoDeptManager(Long employeeId, Long userId, int remainingHeadDepts);
}
