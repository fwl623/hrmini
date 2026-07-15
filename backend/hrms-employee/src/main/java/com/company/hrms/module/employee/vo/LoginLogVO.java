package com.company.hrms.module.employee.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登录日志VO
 * GET /api/v1/profile/security/login-logs
 */
@Data
@Schema(description = "登录日志")
public class LoginLogVO {

    @Schema(description = "登录时间")
    private LocalDateTime loginTime;

    @Schema(description = "IP地址")
    private String ip;

    @Schema(description = "设备信息")
    private String device;

    @Schema(description = "登录地点")
    private String location;

    @Schema(description = "是否成功")
    private Boolean success;
}
