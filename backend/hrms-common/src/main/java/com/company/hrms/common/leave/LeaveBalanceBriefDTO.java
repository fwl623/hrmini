package com.company.hrms.common.leave;

import java.math.BigDecimal;

/**
 * 假期余额摘要（跨模块只读）。
 */
public class LeaveBalanceBriefDTO {

    private String leaveType;
    private String label;
    private BigDecimal balance;

    public LeaveBalanceBriefDTO() {
    }

    public LeaveBalanceBriefDTO(String leaveType, String label, BigDecimal balance) {
        this.leaveType = leaveType;
        this.label = label;
        this.balance = balance;
    }

    public String getLeaveType() {
        return leaveType;
    }

    public void setLeaveType(String leaveType) {
        this.leaveType = leaveType;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }
}
