package com.company.hrms.module.employee.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 敏感字段脱敏工具
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SensitiveFieldMaskUtil {

    /**
     * 手机号脱敏：138****0001
     */
    public static String maskMobile(String mobile) {
        if (mobile == null || mobile.length() < 7) return mobile;
        return mobile.substring(0, 3) + "****" + mobile.substring(7);
    }

    /**
     * 身份证脱敏：110***********1234
     */
    public static String maskIdNumber(String idNumber) {
        if (idNumber == null || idNumber.length() < 10) return idNumber;
        return idNumber.substring(0, 3) + "***********" + idNumber.substring(idNumber.length() - 4);
    }

    /**
     * 银行卡脱敏：****1234
     */
    public static String maskBankAccount(String tail) {
        if (tail == null) return null;
        return "****" + tail;
    }

    /**
     * 邮箱脱敏：z***@company.com
     */
    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) return email;
        String prefix = email.substring(0, email.indexOf("@"));
        String suffix = email.substring(email.indexOf("@"));
        if (prefix.length() <= 1) return email;
        return prefix.charAt(0) + "***" + suffix;
    }
}
