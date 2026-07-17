package com.company.hrms.common.field;

import com.company.hrms.common.enums.RoleCode;
import com.company.hrms.common.security.LoginUser;
import com.company.hrms.common.web.PageResult;
import org.springframework.stereotype.Component;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 字段权限裁剪（PRD §2.3）：按角色将敏感字段置 null。
 * <p>
 * Service 须显式调用 {@link #filter}；注解 {@link FieldPermission} 仅为文档标记。
 */
@Component
public class FieldPermissionFilter {

    private static final Set<String> IDENTITY_SENSITIVE = Set.of(
            "idNumber", "idNumberEnc", "idCard", "idCardNo",
            "emergencyContact", "emergencyPhone", "emergencyName",
            "bankAccount", "bankCard", "bankCardNumber", "bankAccountEnc"
    );

    private static final Set<String> SALARY_SENSITIVE = Set.of(
            "salaryInfo", "baseSalary", "salary", "probationPayRatio",
            "payrollSchemeId", "schemeId", "grossPay", "netPay", "takeHome"
    );

    /**
     * 按当前用户角色裁剪 VO 敏感字段（无权限置 null）。
     *
     * @param recordEmployeeId 被访问档案的 employeeId；列表项可分别传入
     */
    public <T> T filter(T dto, LoginUser user, Long recordEmployeeId) {
        if (dto == null || user == null) {
            return dto;
        }
        boolean canIdentity = canViewIdentity(user, recordEmployeeId);
        boolean canSalary = canViewSalary(user, recordEmployeeId);
        if (canIdentity && canSalary) {
            return dto;
        }
        walk(dto, canIdentity, canSalary, new IdentityHashMap<>());
        return dto;
    }

    /** 列表快捷：对集合内每个元素裁剪（recordEmployeeId 从元素 employeeId/id 字段尝试读取）。 */
    public <T> T filterAll(T dto, LoginUser user) {
        if (dto == null || user == null) {
            return dto;
        }
        if (dto instanceof PageResult<?> page && page.getList() != null) {
            for (Object item : page.getList()) {
                filter(item, user, extractEmployeeId(item));
            }
            return dto;
        }
        if (dto instanceof Collection<?> col) {
            for (Object item : col) {
                filter(item, user, extractEmployeeId(item));
            }
            return dto;
        }
        return filter(dto, user, extractEmployeeId(dto));
    }

    static boolean canViewIdentity(LoginUser user, Long recordEmployeeId) {
        if (user.hasRole(RoleCode.HR_STAFF.name())) {
            return true;
        }
        if (user.hasRole(RoleCode.SYS_ADMIN.name())) {
            return true;
        }
        // DEPT_MANAGER 不可看下属身份证，但本人档案可看
        if (isSelf(user, recordEmployeeId)) {
            return true;
        }
        if (user.hasRole(RoleCode.DEPT_MANAGER.name())) {
            return false;
        }
        return false;
    }

    static boolean canViewSalary(LoginUser user, Long recordEmployeeId) {
        if (user.hasRole(RoleCode.SYS_ADMIN.name())) {
            return false;
        }
        if (user.hasRole(RoleCode.HR_STAFF.name()) || user.hasRole(RoleCode.FINANCE.name())) {
            return true;
        }
        if (user.hasRole(RoleCode.DEPT_MANAGER.name())) {
            return false;
        }
        return isSelf(user, recordEmployeeId);
    }

    private static boolean isSelf(LoginUser user, Long recordEmployeeId) {
        return recordEmployeeId != null
                && user.getEmployeeId() != null
                && recordEmployeeId.equals(user.getEmployeeId());
    }

    private void walk(Object obj, boolean canIdentity, boolean canSalary, IdentityHashMap<Object, Boolean> seen) {
        if (obj == null || seen.containsKey(obj)) {
            return;
        }
        Class<?> type = obj.getClass();
        if (type.isPrimitive() || type.isEnum() || isJdkType(type)) {
            return;
        }
        seen.put(obj, Boolean.TRUE);

        if (obj instanceof Collection<?> col) {
            for (Object item : col) {
                walk(item, canIdentity, canSalary, seen);
            }
            return;
        }
        if (obj instanceof Map<?, ?> map) {
            for (Object value : map.values()) {
                walk(value, canIdentity, canSalary, seen);
            }
            return;
        }
        if (type.isArray()) {
            int len = Array.getLength(obj);
            for (int i = 0; i < len; i++) {
                walk(Array.get(obj, i), canIdentity, canSalary, seen);
            }
            return;
        }

        for (Field field : type.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            field.setAccessible(true);
            try {
                String name = field.getName();
                if (!canIdentity && IDENTITY_SENSITIVE.contains(name)) {
                    if (!field.getType().isPrimitive()) {
                        field.set(obj, null);
                    }
                    continue;
                }
                if (!canSalary && SALARY_SENSITIVE.contains(name)) {
                    if (!field.getType().isPrimitive()) {
                        field.set(obj, null);
                    }
                    continue;
                }
                Object value = field.get(obj);
                walk(value, canIdentity, canSalary, seen);
            } catch (IllegalAccessException ignored) {
                // skip
            }
        }
    }

    private static Long extractEmployeeId(Object item) {
        if (item == null) {
            return null;
        }
        for (String name : new String[]{"employeeId", "id"}) {
            try {
                Field f = findField(item.getClass(), name);
                if (f != null && (f.getType() == Long.class || f.getType() == long.class)) {
                    f.setAccessible(true);
                    Object v = f.get(item);
                    if (v instanceof Long l) {
                        return l;
                    }
                }
            } catch (Exception ignored) {
                // next
            }
        }
        return null;
    }

    private static Field findField(Class<?> type, String name) {
        Class<?> c = type;
        while (c != null && c != Object.class) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        return null;
    }

    private static boolean isJdkType(Class<?> type) {
        Package p = type.getPackage();
        if (p == null) {
            return type.getName().startsWith("java.");
        }
        String name = p.getName();
        return name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("jakarta.");
    }
}
