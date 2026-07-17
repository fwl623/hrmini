package com.company.hrms.employee.dto;

import lombok.Data;

/**
 * 手机号变更申请请求参数
 * <p>
 * POST /api/v1/profile/mobile-change-applications
 * 员工提交手机号变更申请，需提供新手机号、短信验证码及变更原因。
 * 提交后发起 MOBILE_CHANGE 审批，审批通过后同步更新 employee.mobile 和 sys_user.username。
 * </p>
 */
@Data
public class MobileChangeApplyDTO {

    /** 新手机号（11位） */
    private String newMobile;

    /** 短信验证码 */
    private String smsCode;

    /** 变更原因 */
    private String reason;
}
