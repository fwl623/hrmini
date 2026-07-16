package com.company.hrms.common.exception;

/**
 * 未登录 / Token 过期 → HTTP 401 + code 20001。
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException() {
        super(ErrorCode.UNAUTHORIZED.getMessage());
    }

    public UnauthorizedException(String message) {
        super(message);
    }
}
