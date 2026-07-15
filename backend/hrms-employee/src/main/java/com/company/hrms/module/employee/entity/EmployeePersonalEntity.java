package com.company.hrms.module.employee.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.hrms.common.annotation.EncryptedField;

import java.time.LocalDate;

/**
 * 员工个人信息
 * 对应表: employee_personal
 * 身份证字段启用 AES 加密存储
 */
@TableName("employee_personal")
public class EmployeePersonalEntity {

    /** 员工ID（主键，关联 employee.id） */
    @TableId
    private Long employeeId;

    /** 身份证密文（AES-256-GCM 加密存储） */
    @EncryptedField
    private String idNumberEnc;

    /** 身份证 SHA-256 哈希（用于精确检索） */
    private String idNumberHash;

    /** 生日 */
    private LocalDate birthday;

    /** 户籍地址 */
    private String householdAddress;

    /** 现居地址 */
    private String residenceAddress;

    /** 紧急联系人姓名 */
    private String emergencyContact;

    /** 紧急联系人电话 */
    private String emergencyPhone;

    // ===== Getters & Setters =====

    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }

    public String getIdNumberEnc() { return idNumberEnc; }
    public void setIdNumberEnc(String idNumberEnc) { this.idNumberEnc = idNumberEnc; }

    public String getIdNumberHash() { return idNumberHash; }
    public void setIdNumberHash(String idNumberHash) { this.idNumberHash = idNumberHash; }

    public LocalDate getBirthday() { return birthday; }
    public void setBirthday(LocalDate birthday) { this.birthday = birthday; }

    public String getHouseholdAddress() { return householdAddress; }
    public void setHouseholdAddress(String householdAddress) { this.householdAddress = householdAddress; }

    public String getResidenceAddress() { return residenceAddress; }
    public void setResidenceAddress(String residenceAddress) { this.residenceAddress = residenceAddress; }

    public String getEmergencyContact() { return emergencyContact; }
    public void setEmergencyContact(String emergencyContact) { this.emergencyContact = emergencyContact; }

    public String getEmergencyPhone() { return emergencyPhone; }
    public void setEmergencyPhone(String emergencyPhone) { this.emergencyPhone = emergencyPhone; }
}
