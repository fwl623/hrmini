package com.company.hrms.employee.service;

import com.company.hrms.employee.vo.SensitiveFieldVO;

/**
 * 敏感字段服务
 * - AES-256-GCM 解密
 * - 二次验证态校验（不占用工资条 verify Key）
 * - 查看记审计日志
 */
public interface SensitiveFieldService {

    /**
     * 获取敏感字段明文
     * @param employeeId 员工ID
     * @param field 字段名（idNumber / bankAccount）
     * @param password 当前登录用户密码（二次验证）
     * @return 明文值
     */
    SensitiveFieldVO reveal(Long employeeId, String field, String password);
}
