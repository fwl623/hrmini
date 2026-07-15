package com.company.hrms.payroll.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.hrms.payroll.entity.PayslipViewLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * Mapper interface for the {@link PayslipViewLog} entity.
 * <p>
 * Operates on the {@code payslip_view_log} table, which records every instance
 * of an employee viewing their payslip (payslip check / payslip inquiry log).
 * Each row captures which payslip was viewed, by whom, and at what time.
 * </p>
 * <p>
 * Inherits full CRUD and pagination support from MyBatis-Plus
 * {@link BaseMapper<PayslipViewLog>}, so no additional methods are required
 * for standard single-table operations.
 * </p>
 *
 * @see PayslipViewLog
 * @see BaseMapper
 */
@Mapper
public interface PayslipViewLogMapper extends BaseMapper<PayslipViewLog> {
}
