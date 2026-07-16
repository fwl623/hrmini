package com.company.hrms.employee.service;

import com.company.hrms.employee.dto.OnboardingArchiveCommand;

/**
 * 入职建档服务：confirm → 员工档案 + 系统账号。
 */
public interface OnboardingService {

    /**
     * 确认入职：创建员工档案、个人信息、合同占位、系统账号。
     *
     * @return 新建员工 ID
     */
    Long confirm(OnboardingArchiveCommand command);
}
