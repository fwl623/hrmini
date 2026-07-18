package com.company.hrms.common.datascope;

/**
 * 当前请求的数据权限 SQL 片段（由拦截器写入，XML 中可用 ${dataScope}）。
 */
public final class DataScopeContext {

    private static final ThreadLocal<String> SQL = new ThreadLocal<>();

    private DataScopeContext() {
    }

    public static void set(String sqlFragment) {
        // 允许 "" 表示「明确不过滤」；与未 set（null）区分
        SQL.set(sqlFragment);
    }

    /** @return null 表示未设置；空串表示明确不过滤 */
    public static String get() {
        return SQL.get();
    }

    public static void clear() {
        SQL.remove();
    }
}
