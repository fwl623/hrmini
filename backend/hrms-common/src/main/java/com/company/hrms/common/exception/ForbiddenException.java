package com.company.hrms.common.exception;

/**
 * 无权限 → HTTP 403 + code 20002 / 20003。
 */
public class ForbiddenException extends RuntimeException {

    private final int code;

    public ForbiddenException() {
        this(ErrorCode.FORBIDDEN);
    }

    public ForbiddenException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    public ForbiddenException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
    }

    public int getCode() {
        return code;
    }
}
