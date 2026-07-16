package com.company.hrms.employee.service;

import com.company.hrms.employee.dto.MobileChangeApplyDTO;
import com.company.hrms.employee.entity.EmployeeMobileChangeApplication;

import java.util.List;

/**
 * 手机号变更服务接口
 * <p>
 * 完整流程：员工提交申请 → 创建审批实例（MOBILE_CHANGE）→ HR 审批通过/驳回
 * → 通过后：更新 employee.mobile + Feign 调 auth 同步 username
 * <p>
 * 状态机：PENDING → APPROVED / REJECTED / CANCELLED
 * <p>
 * ⚠️ 审批实例创建依赖 C 组审批引擎（联调时启用）
 * ⚠️ 同步 username 依赖 A 组 auth 内部 Feign（联调时启用）
 */
public interface MobileChangeService {

    /** 提交手机号变更申请（创建记录 + 校验新手机号唯一） */
    void apply(Long employeeId, Long userId, MobileChangeApplyDTO dto);

    /** 查询本人申请记录 */
    List<EmployeeMobileChangeApplication> listMyApplications(Long employeeId);

    /** 撤销申请（仅 PENDING → CANCELLED） */
    void cancel(Long employeeId, Long appId);

    /** HR 审批通过（PENDING → APPROVED + 更新 mobile + 同步 auth） */
    void approve(Long appId);

    /** HR 审批驳回（PENDING → REJECTED） */
    void reject(Long appId);

    /** 查询全部待审批列表（HR 端） */
    List<EmployeeMobileChangeApplication> listPending();
}
