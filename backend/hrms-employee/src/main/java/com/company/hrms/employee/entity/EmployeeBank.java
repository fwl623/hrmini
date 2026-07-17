package com.company.hrms.employee.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 员工银行卡信息表实体
 * <p>
 * 对应表: employee_bank (DDL #26)
 * 存储员工的银行卡信息，银行卡号使用 AES-256-GCM 加密存储。
 * 与 employee 表一对一关系，仅 HR 专员可查看完整卡号。
 * </p>
 */
@Data
public class EmployeeBank {

    /** 员工ID（主键，关联 employee.id） */
    private Long employeeId;

    /** 银行卡号密文（AES-256-GCM 加密存储） */
    private String bankAccountEnc;

    /** 银行卡号后四位（明文展示） */
    private String bankAccountTail;

    /** 开户行 */
    private String bankName;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
