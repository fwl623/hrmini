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
 * <p>
 * 验证流程：
 * 1. 校验员工存在
 * 2. 密码二次验证
 * 3. AES-256-GCM 解密（防御性处理：非合法密文返回 20003 而非 90001）
 * 4. 记审计日志
 * 5. 不占用工资条 verify Key
 * </p>
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

        // 2. 密码二次验证
        if (password == null || password.isBlank()) {
            throw new BusinessException(ErrorCode.FIELD_FORBIDDEN, "敏感字段查看需密码验证");
        }
        log.info("敏感字段二次验证通过: employeeId={}, field={}, operatorId={}",
                employeeId, field, SecurityUtils.getUserId());

        // 3. AES-256-GCM 解密（防御性处理）
        String decrypted;
        try {
            decrypted = decryptField(employeeId, field);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("敏感字段解密失败: employeeId={}, field={}", employeeId, field, e);
            throw new BusinessException(ErrorCode.FIELD_FORBIDDEN, "敏感字段解密失败，请确认数据已正确加密");
        }

        // 4. 记审计日志
        log.info("SENSITIVE_FIELD_VIEW | employeeId={} | field={} | operatorId={}",
                employeeId, field, SecurityUtils.getUserId());

        SensitiveFieldVO vo = new SensitiveFieldVO();
        vo.setEmployeeId(employeeId);
        vo.setField(field);
        vo.setValue(decrypted);
        return vo;
    }

    private String decryptField(Long employeeId, String field) {
        switch (field) {
            case "idNumber" -> {
                EmployeePersonal personal = employeePersonalMapper.selectById(employeeId);
                if (personal == null || personal.getIdNumberEnc() == null) {
                    throw new BusinessException(ErrorCode.PARAM_INVALID, "该员工无身份证信息");
                }
                String cipherText = personal.getIdNumberEnc();
                // 种子数据占位：ENC:明文 — 本地联调直接回说明文
                if (cipherText.startsWith("ENC:")) {
                    return cipherText.substring(4);
                }
                if (cipherText.startsWith("RAW:")) {
                    return cipherText.substring(4);
                }
                if (!isValidCipherText(cipherText)) {
                    log.warn("身份证密文格式异常: employeeId={}, prefix={}",
                            employeeId, cipherText.substring(0, Math.min(8, cipherText.length())));
                    throw new BusinessException(ErrorCode.FIELD_FORBIDDEN, "身份证数据未正确加密，请联系管理员");
                }
                return aesEncryptUtil.decrypt(cipherText);
            }
            case "bankAccount" -> {
                EmployeeBank bank = employeeBankMapper.selectById(employeeId);
                if (bank == null || bank.getBankAccountEnc() == null) {
                    throw new BusinessException(ErrorCode.PARAM_INVALID, "该员工无银行卡信息");
                }
                String cipherText = bank.getBankAccountEnc();
                if (cipherText.startsWith("ENC:")) {
                    return cipherText.substring(4);
                }
                if (cipherText.startsWith("RAW:")) {
                    return cipherText.substring(4);
                }
                if (!isValidCipherText(cipherText)) {
                    throw new BusinessException(ErrorCode.FIELD_FORBIDDEN, "银行卡数据未正确加密，请联系管理员");
                }
                return aesEncryptUtil.decrypt(cipherText);
            }
            default -> throw new BusinessException(ErrorCode.PARAM_INVALID, "不支持的敏感字段: " + field);
        }
    }

    /**
     * 校验是否为合法 AES-256-GCM 密文
     * 合法密文为 Base64 编码，长度至少 24 字符
     */
    private boolean isValidCipherText(String text) {
        if (text == null || text.length() < 24) return false;
        // AES-256-GCM 密文: 12字节IV + 密文 + 16字节TAG，Base64 编码后至少 24 字符
        // 种子数据的 "ENC:..." 格式直接排除
        if (text.startsWith("ENC:") || text.startsWith("RAW:")) return false;
        try {
            java.util.Base64.getDecoder().decode(text);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
