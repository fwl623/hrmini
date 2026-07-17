package com.company.hrms.workflow.support;

import com.company.hrms.common.security.LoginUser;
import com.company.hrms.common.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 当前用户：开发期 X-User-Id 优先（便于模拟审批人 1002），其次 JWT LoginUser，最后固定测试用户。
 */
@Component
public class CurrentUserProvider {

    public static final long FALLBACK_USER_ID = 1002L;

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

    public String displayName(long userId) {
        return switch ((int) userId) {
            case 1001 -> "HR李四";
            case 1002 -> "部门负责人王五";
            case 1003 -> "HR审批人赵六";
            case 1004 -> "代审人孙七";
            default -> "用户" + userId;
        };
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
