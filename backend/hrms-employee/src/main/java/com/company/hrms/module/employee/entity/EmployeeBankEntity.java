package com.company.hrms.module.employee.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.hrms.common.annotation.EncryptedField;

/**
 * 员工银行信息
 * 对应表: employee_bank
 * 银行卡号启用 AES 加密存储
 */
@TableName("employee_bank")
public class EmployeeBankEntity {

    /** 员工ID（主键，关联 employee.id） */
    @TableId
    private Long employeeId;

    /** 银行卡号密文（AES-256-GCM 加密存储） */
    @EncryptedField
    private String bankAccountEnc;

    /** 银行卡号后四位（明文展示） */
    private String bankAccountTail;

    /** 开户行 */
    private String bankName;

    // ===== Getters & Setters =====

    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }

    public String getBankAccountEnc() { return bankAccountEnc; }
    public void setBankAccountEnc(String bankAccountEnc) { this.bankAccountEnc = bankAccountEnc; }

    public String getBankAccountTail() { return bankAccountTail; }
    public void setBankAccountTail(String bankAccountTail) { this.bankAccountTail = bankAccountTail; }

    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }
}
