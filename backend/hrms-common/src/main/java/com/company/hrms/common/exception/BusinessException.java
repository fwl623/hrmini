package com.company.hrms.common.exception;

import lombok.Getter;

/**
 * 业务异常
 * 由全局异常处理器统一捕获并返回 { code, message }
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    /** 参数校验失败 */
    public static BusinessException paramError(String message) {
        return new BusinessException(10001, message);
    }

    /** 无权限 */
    public static BusinessException noPermission(String message) {
        return new BusinessException(20002, message);
    }

    /** 无字段权限 */
    public static BusinessException fieldNoPermission() {
        return new BusinessException(20003, "字段权限不足");
    }
}
