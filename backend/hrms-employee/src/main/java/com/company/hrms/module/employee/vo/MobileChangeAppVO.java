package com.company.hrms.module.employee.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 手机号变更申请VO
 * GET /api/v1/employees/mobile-change-applications（HR端）
 * GET /api/v1/profile/mobile-change-applications（门户端）
 */
@Data
@Schema(description = "手机号变更申请")
public class MobileChangeAppVO {

    @Schema(description = "申请ID")
    private Long id;

    @Schema(description = "员工ID")
    private Long employeeId;

    @Schema(description = "员工姓名")
    private String employeeName;

    @Schema(description = "员工工号")
    private String empNo;

    @Schema(description = "原手机号")
    private String oldMobile;

    @Schema(description = "新手机号")
    private String newMobile;

    @Schema(description = "变更原因")
    private String reason;

    @Schema(description = "状态 pending/approved/rejected/cancelled")
    private String status;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
