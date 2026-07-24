package com.company.hrms.common.leave;

import java.util.List;

/**
 * 假期余额只读 SPI（实现位于 hrms-attendance）。
 * AI / 其他模块查本人额度时走此接口，避免直接依赖考勤实现类。
 */
public interface LeaveBalanceQueryService {

    /**
     * 查询指定员工假期余额（至少含年假、调休；无记录时余额为 0）。
     */
    List<LeaveBalanceBriefDTO> listBalances(Long employeeId);
}
