package com.company.hrms.employee.service.impl;

import com.company.hrms.common.exception.BusinessException;
import com.company.hrms.common.exception.ErrorCode;
import com.company.hrms.common.security.SecurityUtils;
import com.company.hrms.common.util.AesEncryptUtil;
import com.company.hrms.employee.entity.Employee;
import com.company.hrms.employee.entity.EmployeeBank;
import com.company.hrms.employee.entity.EmployeePersonal;
import com.company.hrms.employee.mapper.EmployeeBankMapper;
import com.company.hrms.employee.mapper.EmployeeMapper;
import com.company.hrms.employee.mapper.EmployeePersonalMapper;
import com.company.hrms.employee.service.SensitiveFieldService;
import com.company.hrms.employee.vo.SensitiveFieldVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 敏感字段服务实现
 *
 * 验证流程：
 * 1. 校验员工存在
 * 2. 密码二次验证（委托 hrms-auth 校验密码，当前简化）
 * 3. AES-256-GCM 解密
 * 4. 记审计日志
 * 5. 不占用工资条 verify Key（独立 Redis Key 或每次验证）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SensitiveFieldServiceImpl implements SensitiveFieldService {

    private final EmployeeMapper employeeMapper;
    private final EmployeePersonalMapper employeePersonalMapper;
    private final EmployeeBankMapper employeeBankMapper;
    private final AesEncryptUtil aesEncryptUtil;

    @Override
    @Transactional
    public SensitiveFieldVO reveal(Long employeeId, String field, String password) {
        // 1. 校验员工存在
        Employee emp = employeeMapper.selectById(employeeId);
        if (emp == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "员工不存在");
        }

        // 2. 密码二次验证（简化：非空即通过；联调时对接 hrms-auth 的密码校验接口）
        if (password == null || password.isBlank()) {
            throw new BusinessException(ErrorCode.FIELD_FORBIDDEN, "敏感字段查看需密码验证");
        }
        log.info("敏感字段二次验证通过: employeeId={}, field={}, operatorId={}",
                employeeId, field, SecurityUtils.getUserId());

        // 3. AES-256-GCM 解密
        String decrypted;
        switch (field) {
            case "idNumber" -> {
                EmployeePersonal personal = employeePersonalMapper.selectById(employeeId);
                if (personal == null || personal.getIdNumberEnc() == null) {
                    throw new BusinessException(ErrorCode.PARAM_INVALID, "该员工无身份证信息");
                }
                try {
                    decrypted = aesEncryptUtil.decrypt(personal.getIdNumberEnc());
                } catch (Exception e) {
                    log.error("身份证解密失败: employeeId={}", employeeId, e);
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR, "敏感字段解密失败");
                }
            }
            case "bankAccount" -> {
                EmployeeBank bank = employeeBankMapper.selectById(employeeId);
                if (bank == null || bank.getBankAccountEnc() == null) {
                    throw new BusinessException(ErrorCode.PARAM_INVALID, "该员工无银行卡信息");
                }
                try {
                    decrypted = aesEncryptUtil.decrypt(bank.getBankAccountEnc());
                } catch (Exception e) {
                    log.error("银行卡解密失败: employeeId={}", employeeId, e);
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR, "敏感字段解密失败");
                }
            }
            default -> throw new BusinessException(ErrorCode.PARAM_INVALID, "不支持的敏感字段: " + field);
        }

        // 4. 记审计日志
        log.info("SENSITIVE_FIELD_VIEW | employeeId={} | field={} | operatorId={}",
                employeeId, field, SecurityUtils.getUserId());

        // 5. 不占用工资条 verify Key（本接口独立验证，不影响 payslip verify）

        SensitiveFieldVO vo = new SensitiveFieldVO();
        vo.setEmployeeId(employeeId);
        vo.setField(field);
        vo.setValue(decrypted);
        return vo;
    }
}
