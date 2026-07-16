package com.company.hrms.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 业务异常：HTTP 按附录 K 映射（冲突 409 / 业务拒绝 422），body.code 为业务码。
 */
public class BusinessException extends RuntimeException {

    private final int code;
    private final HttpStatus httpStatus;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.httpStatus = errorCode.getHttpStatus();
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
        this.httpStatus = errorCode.getHttpStatus();
    }

    /**
     * 自定义业务码；HTTP 能反查 {@link ErrorCode} 则用其映射，否则默认 422。
     */
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
        ErrorCode known = ErrorCode.fromCode(code);
        this.httpStatus = known != null ? known.getHttpStatus() : HttpStatus.UNPROCESSABLE_ENTITY;
    }

    public int getCode() {
        return code;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
