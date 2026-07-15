package com.company.hrms.module.employee.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.company.hrms.common.entity.BaseEntity;
import com.company.hrms.module.employee.enums.ResignationRequestStatus;

import java.time.LocalDate;

/**
 * 员工离职申请
 * 对应表: employee_resignation_request
 *
 * PRD §5.4.1 员工发起离职申请 → 审批通过 → HR 发起正式离职
 */
@TableName("employee_resignation_request")
public class EmployeeResignationRequestEntity extends BaseEntity {

    /** 审批实例ID */
    private Long instanceId;

    /** 员工ID */
    private Long employeeId;

    /** 状态 PENDING / APPROVED / REJECTED / CANCELLED */
    private ResignationRequestStatus status;

    /** 期望离职日期 */
    private LocalDate expectedResignDate;

    /** 离职原因分类 VOLUNTARY/INVOLUNTARY/NEGOTIATED */
    private String reasonCategory;

    /** 离职类型 RESIGN/DISMISS/CONTRACT_EXPIRE/OTHER */
    private String resignationType;

    /** 详细说明 */
    private String reasonDetail;

    // ===== Getters & Setters =====

    public Long getInstanceId() { return instanceId; }
    public void setInstanceId(Long instanceId) { this.instanceId = instanceId; }

    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }

    public ResignationRequestStatus getStatus() { return status; }
    public void setStatus(ResignationRequestStatus status) { this.status = status; }

    public LocalDate getExpectedResignDate() { return expectedResignDate; }
    public void setExpectedResignDate(LocalDate expectedResignDate) { this.expectedResignDate = expectedResignDate; }

    public String getReasonCategory() { return reasonCategory; }
    public void setReasonCategory(String reasonCategory) { this.reasonCategory = reasonCategory; }

    public String getResignationType() { return resignationType; }
    public void setResignationType(String resignationType) { this.resignationType = resignationType; }

    public String getReasonDetail() { return reasonDetail; }
    public void setReasonDetail(String reasonDetail) { this.reasonDetail = reasonDetail; }
}
