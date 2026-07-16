package com.company.hrms.employee.vo;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 登录日志
 * GET /api/v1/profile/security/login-logs
 */
@Data
public class LoginLogVO {
    private LocalDateTime loginTime;
    private String ip;
    private String device;
    private String location;
    private Boolean success;
}
