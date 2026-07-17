package com.company.hrms.employee.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登录日志响应
 * <p>
 * 用于 GET /api/v1/profile/security/login-logs 接口。
 * 返回当前用户的登录记录列表，仅本人可见（@DataScope SELF）。
 * </p>
 */
@Data
public class LoginLogVO {

    /** 登录时间 */
    private LocalDateTime loginTime;

    /** 登录IP地址 */
    private String ip;

    /** 设备信息（解析自 User-Agent） */
    private String device;

    /** 登录地点（IP 归属地，可选） */
    private String location;

    /** 是否登录成功（true=成功 false=失败） */
    private Boolean success;
}
