package com.company.hrms.common.field;

import com.company.hrms.common.security.LoginUser;
import org.springframework.stereotype.Component;

/**
 * 字段权限裁剪（身份证/银行卡/薪资等）。对接员工模块时按 PRD §2.3 矩阵完善。
 * <p>
 * 先提供显式调用入口，避免空 Aspect 误导「加了注解就自动生效」。
 */
@Component
public class FieldPermissionFilter {

    /**
     * 按当前用户角色裁剪 VO 敏感字段（无权限置 null 或脱敏）。
     */
    public <T> T filter(T dto, LoginUser user, Long recordEmployeeId) {
        // TODO: 按角色矩阵裁剪 idNumber / bankCard / salaryInfo 等
        return dto;
    }
}
