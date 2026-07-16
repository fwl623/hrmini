package com.company.hrms.employee.service;

import com.company.hrms.employee.dto.MobileChangeApplyDTO;
import com.company.hrms.employee.entity.EmployeeMobileChangeApplication;

import java.util.List;

/**
 * 手机号变更服务
 *
 * 流程：员工提交申请 → MOBILE_CHANGE 审批 → 审批通过后同步 auth
 *
 * ⚠️ 审批依赖 C 组 (郭策) 的审批引擎
 * ⚠️ 同步 username 依赖 A 组 (李俊毅) 的 auth 内部 Feign
 */
public interface MobileChangeService {

    /** 提交手机号变更申请（创建记录 + 发起审批） */
    void apply(Long employeeId, Long userId, MobileChangeApplyDTO dto);

    /** 查询本人申请记录 */
    List<EmployeeMobileChangeApplication> listMyApplications(Long employeeId);

    /** 撤销申请（仅 PENDING 可撤销） */
    void cancel(Long employeeId, Long appId);

    /** 审批通过回调（由审批引擎调用或 MQ 消费）：更新 employee.mobile + 同步 sys_user.username */
    void onApproved(Long appId);
}
