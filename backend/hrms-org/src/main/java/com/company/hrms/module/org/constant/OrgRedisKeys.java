package com.company.hrms.module.org.constant;

/**
 * 组织架构模块 Redis Key 约定。
 */
public final class OrgRedisKeys {

    private OrgRedisKeys() {
    }

    /** 部门树缓存，TTL 5min；字段扩展时升版本避免旧缓存缺 parentId/description 等 */
    public static final String DEPT_TREE = "hrms:dept:tree:v2";

    /** 工号序号分布式锁 */
    public static String empSeqLock(String year, String deptCode) {
        return "hrms:emp:seq:" + year + ":" + deptCode;
    }
}
