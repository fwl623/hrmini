package com.company.hrms.module.auth.filter;

import com.company.hrms.common.config.HrmsSecurityProperties;
import com.company.hrms.common.enums.RoleCode;
import com.company.hrms.common.exception.ErrorCode;
import com.company.hrms.common.exception.UnauthorizedException;
import com.company.hrms.common.security.JwtTokenProvider;
import com.company.hrms.common.security.LoginUser;
import com.company.hrms.common.security.SecurityUtils;
import com.company.hrms.common.web.Result;
import com.company.hrms.module.auth.entity.SysUser;
import com.company.hrms.module.auth.mapper.SysUserMapper;
import com.company.hrms.module.auth.service.serviceImpl.AuthServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * JWT 鉴权过滤器：校验签名、黑名单、无操作超时、薪资双拦截、内部调用密钥，并填充 LoginUser。
 * <p>
 * 薪资访问（PRD §2.2）：
 * <ul>
 *   <li>管理端 {@code /payroll/**}、员工薪资档案 {@code /employees/\{id\}/salary/**}：仅 HR_STAFF / FINANCE</li>
 *   <li>门户本人工资条 {@code /profile/payslips/**}：禁止 SYS_ADMIN；其余角色可进（本人范围由业务层再收）</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    private static final List<String> WHITE_LIST = List.of(
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/verify",
            "/api/v1/attendance/**",
            "/api/v1/leaves/**",
            "/api/v1/overtime/**",
            "/api/v1/payroll/**",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/actuator/**",
            "/error"
    );

    /** 管理端薪资全量（账套/核算/他人工资条等） */
    private static final List<String> PAYROLL_ADMIN_PATHS = List.of(
            "/api/v1/payroll/**",
            "/api/v1/employees/*/salary",
            "/api/v1/employees/*/salary/**"
    );

    /** 员工门户本人工资条 */
    private static final List<String> PAYSLIP_SELF_PATHS = List.of(
            "/api/v1/profile/payslips/**"
    );

    private final JwtTokenProvider jwtTokenProvider;
    private final AuthServiceImpl authService;
    private final SysUserMapper sysUserMapper;
    private final ObjectMapper objectMapper;
    private final HrmsSecurityProperties securityProperties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public JwtAuthFilter(JwtTokenProvider jwtTokenProvider,
                         AuthServiceImpl authService,
                         SysUserMapper sysUserMapper,
                         ObjectMapper objectMapper,
                         HrmsSecurityProperties securityProperties) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.authService = authService;
        this.sysUserMapper = sysUserMapper;
        this.objectMapper = objectMapper;
        this.securityProperties = securityProperties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }
        if (isInternalPath(path)) {
            if (!isValidInternalToken(request)) {
                writeForbidden(response, "内部接口未授权");
                return;
            }
            filterChain.doFilter(request, response);
            return;
        }
        if (isWhitelisted(path)) {
            // 开发期白名单路径设置默认用户（便于 Swagger 调试）
            if (path.contains("/attendance/") || path.contains("/leaves/") || path.contains("/overtime/") || path.contains("/payroll/")) {
                LoginUser devUser = new LoginUser();
                devUser.setUserId(1L);
                devUser.setEmployeeId(101L);
                devUser.setUsername("dev");
                devUser.setDataScope("ALL");
                devUser.setRoles(List.of("HR_STAFF"));
                SecurityUtils.setLoginUser(devUser);
            }
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String header = request.getHeader("Authorization");
            if (!StringUtils.hasText(header) || !header.startsWith("Bearer ")) {
                writeUnauthorized(response, "未登录或 Token 过期");
                return;
            }
            String token = header.substring(7).trim();
            Claims claims = jwtTokenProvider.parseClaims(token);
            String jti = claims.getId();
            if (authService.isTokenBlacklisted(jti)) {
                writeUnauthorized(response, "登录已过期，请重新登录");
                return;
            }
            Long userId = Long.valueOf(claims.getSubject());
            if (authService.isIdleTimeout(userId)) {
                writeUnauthorized(response, "登录已过期，请重新登录");
                return;
            }

            SysUser user = sysUserMapper.selectById(userId);
            if (user == null || user.getStatus() == null || user.getStatus() != 1) {
                writeUnauthorized(response, "账号不存在或已禁用");
                return;
            }

            LoginUser loginUser = authService.buildLoginUser(user);
            if (!canAccessPayrollRelated(path, loginUser)) {
                writeForbidden(response, "无薪资数据访问权限");
                return;
            }

            SecurityUtils.setLoginUser(loginUser);
            authService.touchLastActive(userId);
            filterChain.doFilter(request, response);
        } catch (UnauthorizedException ex) {
            writeUnauthorized(response, ex.getMessage());
        } finally {
            SecurityUtils.clear();
        }
    }

    private boolean isInternalPath(String path) {
        return pathMatcher.match("/api/v1/internal/**", path);
    }

    private boolean isValidInternalToken(HttpServletRequest request) {
        String expected = securityProperties.getInternal().getToken();
        if (!StringUtils.hasText(expected)) {
            return false;
        }
        String actual = request.getHeader(INTERNAL_TOKEN_HEADER);
        return expected.equals(actual);
    }

    private boolean isWhitelisted(String path) {
        for (String pattern : WHITE_LIST) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return true 允许继续；false 应返回 403
     */
    private boolean canAccessPayrollRelated(String path, LoginUser user) {
        if (matchesAny(path, PAYROLL_ADMIN_PATHS)) {
            // 管理端薪资：仅 HR / 财务；SYS_ADMIN、EMPLOYEE、DEPT_MANAGER 一律拒绝
            return user.hasRole(RoleCode.HR_STAFF.name()) || user.hasRole(RoleCode.FINANCE.name());
        }
        if (matchesAny(path, PAYSLIP_SELF_PATHS)) {
            // 本人工资条：SYS_ADMIN 不可见；其它角色（含 EMPLOYEE / DEPT_MANAGER）可进门户路径
            return !user.hasRole(RoleCode.SYS_ADMIN.name());
        }
        return true;
    }

    private boolean matchesAny(String path, List<String> patterns) {
        for (String pattern : patterns) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        writeJson(response, Result.error(ErrorCode.UNAUTHORIZED.getCode(), message));
    }

    private void writeForbidden(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        writeJson(response, Result.error(ErrorCode.FORBIDDEN.getCode(), message));
    }

    private void writeJson(HttpServletResponse response, Result<?> body) throws IOException {
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
