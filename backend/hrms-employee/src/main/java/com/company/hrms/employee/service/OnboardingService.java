package com.company.hrms.employee.service;

/**
 * 入职建档服务
 *
 * 流程：confirm → create employee → auth create user → MQ event
 *
 * ⚠️ 调 A 组 (李俊毅) 的 auth 建号 — 先约定接口，联调验证
 * ⚠️ MQ 事件通知考勤/薪资模块 — 待 MQ 基础设施就绪
 */
public interface OnboardingService {

    /**
     * 确认入职：创建员工档案 + 系统账号 + 发送 MQ 事件
     *
     * @param applicationId 入职申请ID（来自 hrms-workflow）
     * @param actualOnboardDate 实际入职日期
     * @return 新建员工ID
     */
    Long confirm(Long applicationId, java.time.LocalDate actualOnboardDate);
}
