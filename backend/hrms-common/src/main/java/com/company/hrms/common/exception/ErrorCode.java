package com.company.hrms.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 业务错误码（附录 K.2）及对应 HTTP 状态。
 * <ul>
 *   <li>409：冲突（重复提交、幂等、资源已存在）</li>
 *   <li>422：业务拒绝（规则不满足）</li>
 * </ul>
 */
public enum ErrorCode {

    SUCCESS(0, "success", HttpStatus.OK),

    PARAM_INVALID(10001, "参数校验失败", HttpStatus.BAD_REQUEST),

    UNAUTHORIZED(20001, "未登录或 Token 过期", HttpStatus.UNAUTHORIZED),
    FORBIDDEN(20002, "无权限", HttpStatus.FORBIDDEN),
    FIELD_FORBIDDEN(20003, "字段权限不足", HttpStatus.FORBIDDEN),

    RESOURCE_CONFLICT(40901, "资源已存在", HttpStatus.CONFLICT),
    ACCOUNT_LOCKED(42201, "账号已锁定，请 15 分钟后再试", HttpStatus.UNPROCESSABLE_ENTITY),

    DEPT_LEVEL_EXCEEDED(30001, "部门层级超过 5 层", HttpStatus.UNPROCESSABLE_ENTITY),
    /**
     * 附录 K 保留码。当前合并实现为直接转移员工，org 模块已不再抛出；
     * 删除非空请用 {@link #DEPT_NOT_EMPTY}。
     */
    @Deprecated
    DEPT_MERGE_HAS_EMPLOYEE(30002, "部门合并前尚有员工", HttpStatus.UNPROCESSABLE_ENTITY),
    EMPLOYEE_STATUS_INVALID(30003, "员工状态不允许此操作", HttpStatus.UNPROCESSABLE_ENTITY),
    TRANSFER_DEPT_UNCHANGED(30004, "调岗部门未变更", HttpStatus.UNPROCESSABLE_ENTITY),
    /** 删除部门时仍有子部门/员工/职位 */
    DEPT_NOT_EMPTY(30005, "部门下仍有子部门、员工或职位，无法删除", HttpStatus.UNPROCESSABLE_ENTITY),
    /** 部门编码变更会影响工号规则 */
    DEPT_CODE_LOCKED(30006, "部门下仍有员工时不允许修改编码", HttpStatus.UNPROCESSABLE_ENTITY),

    ATTENDANCE_MONTH_LOCKED(40001, "考勤月已锁定", HttpStatus.UNPROCESSABLE_ENTITY),
    MAKEUP_LIMIT_EXCEEDED(40002, "补卡次数超限（2次/月）", HttpStatus.UNPROCESSABLE_ENTITY),
    LEAVE_BALANCE_INSUFFICIENT(40003, "请假余额不足", HttpStatus.UNPROCESSABLE_ENTITY),
    PUNCH_OUT_OF_RANGE(40004, "不在打卡有效范围", HttpStatus.UNPROCESSABLE_ENTITY),

    PAYROLL_BATCH_EXISTS(50001, "算薪批次已存在", HttpStatus.CONFLICT),
    PAYROLL_IN_PROGRESS(50002, "算薪进行中，请勿重复操作", HttpStatus.CONFLICT),
    SALARY_PROFILE_MISSING(50003, "员工无薪资档案", HttpStatus.UNPROCESSABLE_ENTITY),
    ATTENDANCE_NOT_LOCKED(50004, "考勤数据未锁定", HttpStatus.UNPROCESSABLE_ENTITY),
    PAYSLIP_NOT_AVAILABLE(50005, "工资条尚未发放或当前不可查看", HttpStatus.UNPROCESSABLE_ENTITY),

    APPROVAL_ALREADY_HANDLED(60001, "审批已处理", HttpStatus.CONFLICT),
    APPROVAL_STATE_INVALID(60002, "审批已超时 / 状态不允许撤销", HttpStatus.UNPROCESSABLE_ENTITY),
    DELEGATION_CONFLICT(60003, "委托规则冲突（同时仅 1 条）", HttpStatus.CONFLICT),
    PAYSLIP_VERIFY_FAILED(60004, "工资条二次验证未通过或已过期", HttpStatus.UNPROCESSABLE_ENTITY),

    SYSTEM_ERROR(90001, "系统内部错误", HttpStatus.INTERNAL_SERVER_ERROR);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    /** 按业务码反查枚举，未知码按业务拒绝 422 处理。 */
    public static ErrorCode fromCode(int code) {
        for (ErrorCode value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        return null;
    }
}
