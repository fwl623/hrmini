package com.company.hrms.common.datascope;

/**
 * 当前请求的数据权限 SQL 片段（由拦截器写入，XML 中可用 ${dataScope}）。
 */
public final class DataScopeContext {

    private static final ThreadLocal<String> SQL = new ThreadLocal<>();

    private DataScopeContext() {
    }

    public static void set(String sqlFragment) {
        SQL.set(sqlFragment == null ? "" : sqlFragment);
    }

    public static String get() {
        String sql = SQL.get();
        return sql == null ? "" : sql;
    }

    public static void clear() {
        SQL.remove();
    }
}
