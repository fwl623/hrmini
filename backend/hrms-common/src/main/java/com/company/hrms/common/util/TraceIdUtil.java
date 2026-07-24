package com.company.hrms.common.util;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * TraceId 生成与 SLF4J MDC 注入；与 {@link com.company.hrms.common.filter.TraceIdFilter}、
 * {@link com.company.hrms.common.web.Result} 中的 traceId 字段配合。
 */
public final class TraceIdUtil {

    public static final String TRACE_ID = "traceId";
    public static final String HEADER = "X-Trace-Id";

    private TraceIdUtil() {
    }

    public static String create() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static String get() {
        return MDC.get(TRACE_ID);
    }

    public static String getOrCreate() {
        String id = get();
        if (id == null || id.isBlank()) {
            id = create();
            set(id);
        }
        return id;
    }

    public static void set(String traceId) {
        MDC.put(TRACE_ID, traceId);
    }

    public static void clear() {
        MDC.remove(TRACE_ID);
    }
}
