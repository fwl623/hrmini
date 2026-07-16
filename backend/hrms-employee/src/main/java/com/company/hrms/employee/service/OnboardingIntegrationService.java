package com.company.hrms.employee.service;

import java.time.LocalDate;

/**
 * 入职建档集成服务
 * <p>
 * C 组审批通过后回调本服务，事务内完成：
 * 1. 生成工号（Redis INCR + 复用检查）
 * 2. 写入 employee / employee_personal / employee_contract / employee_bank
 * 3. Feign 调用 A 组 auth 创建系统账号
 * 4. 回写 employee.userId
 * 5. 发布 MQ 事件（通知考勤组分配、薪资档案初始化）
 * <p>
 * 回滚策略：auth 建号失败 → 事务回滚（@Transactional）
 * <p>
 * ⚠️ 依赖 C 组确认入职后触发
 * ⚠️ 依赖 A 组 auth 建号接口（联调时启用）
 */
public interface OnboardingIntegrationService {

    /**
     * 确认入职建档
     *
     * @param applicationId      入职申请ID（来自 C 组 workflow）
     * @param actualOnboardDate  实际入职日期
     * @return 新建员工ID
     */
    Long confirm(Long applicationId, LocalDate actualOnboardDate);
}
