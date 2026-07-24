package com.company.hrms.common.filter;

import com.company.hrms.common.util.TraceIdUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 从请求头 {@code X-Trace-Id} 读取或生成 traceId，写入 MDC 与响应头，便于全链路日志关联。
 * 顺序最高，早于 JWT 等业务 Filter。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String traceId = request.getHeader(TraceIdUtil.HEADER);
            if (traceId == null || traceId.isBlank()) {
                traceId = TraceIdUtil.create();
            }
            TraceIdUtil.set(traceId);
            response.setHeader(TraceIdUtil.HEADER, traceId);
            filterChain.doFilter(request, response);
        } finally {
            TraceIdUtil.clear();
        }
    }
}
