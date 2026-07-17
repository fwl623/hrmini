package com.company.hrms.employee.service;

import com.company.hrms.employee.vo.SensitiveFieldVO;

/**
 * 敏感字段服务接口
 * <p>
 * 提供敏感字段的二次验证和 AES-256-GCM 解密功能。
 * 验证机制独立于工资条 verify Key，不产生冲突。
 * 查看记录记入 operation_log 审计日志。
 * </p>
 */
public interface SensitiveFieldService {

    /**
     * 获取敏感字段明文
     *
     * @param employeeId 员工ID
     * @param field      字段名（idNumber / bankAccount）
     * @param password   当前用户密码（二次验证）
     * @return 解密后的明文值
     */
    SensitiveFieldVO reveal(Long employeeId, String field, String password);
}
