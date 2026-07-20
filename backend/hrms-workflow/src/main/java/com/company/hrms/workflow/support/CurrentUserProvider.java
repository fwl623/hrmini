package com.company.hrms.workflow.support;

import com.company.hrms.common.security.LoginUser;
import com.company.hrms.common.security.SecurityUtils;
import com.company.hrms.workflow.mapper.OrgLookupMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

/**
 * 当前用户：开发期 X-User-Id 优先（便于模拟审批人 1002），其次 JWT LoginUser，最后固定测试用户。
 */
@Component
public class CurrentUserProvider {

    public static final long FALLBACK_USER_ID = 1002L;

    private final OrgLookupMapper orgLookupMapper;

    public CurrentUserProvider(OrgLookupMapper orgLookupMapper) {
        this.orgLookupMapper = orgLookupMapper;
    }

    public long requireUserId() {
        Long headerUserId = readHeaderUserId();
        if (headerUserId != null) {
            return headerUserId;
        }
        LoginUser login = SecurityUtils.getLoginUser();
        if (login != null && login.getUserId() != null) {
            return login.getUserId();
        }
        return FALLBACK_USER_ID;
    }

    /**
     * 审批人展示：优先「职位+姓名」；无员工档案时识别 SYS_ADMIN / 种子账号，避免出现「用户1」。
     */
    public String displayName(long userId) {
        if (userId > 0) {
            try {
                Map<String, Object> row = orgLookupMapper.selectEmployeeNameAndPositionByUserId(userId);
                if (row != null) {
                    String name = str(row.get("name"));
                    String position = str(row.get("positionName"));
                    if (!name.isEmpty() && !position.isEmpty()) {
                        return position + name;
                    }
                    if (!name.isEmpty()) {
                        return name;
                    }
                }
            } catch (Exception ignored) {
                // fall through
            }
            try {
                if (orgLookupMapper.countSysAdminRole(userId) > 0) {
                    return "系统管理员";
                }
            } catch (Exception ignored) {
                // fall through
            }
        }
        return switch ((int) userId) {
            case 1, 1005 -> "系统管理员";
            case 1001 -> "HR专员李四";
            case 1002 -> "部门经理王五";
            case 1003 -> "HR专员赵六";
            case 1004 -> "代审人孙七";
            case 1008 -> "财务钱八";
            default -> {
                try {
                    String username = orgLookupMapper.selectUsernameByUserId(userId);
                    if (username != null && !username.isBlank()) {
                        yield username.trim();
                    }
                } catch (Exception ignored) {
                    // fall through
                }
                yield "用户" + userId;
            }
        };
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    private Long readHeaderUserId() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return null;
        }
        HttpServletRequest request = attrs.getRequest();
        String raw = request.getHeader("X-User-Id");
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
