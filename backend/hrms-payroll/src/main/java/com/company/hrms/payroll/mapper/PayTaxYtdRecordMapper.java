package com.company.hrms.payroll.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.hrms.payroll.entity.PayTaxYtdRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * MyBatis-Plus Mapper 接口，操作 {@link PayTaxYtdRecord} 实体对应的数据库表。
 * <p>
 * 该接口继承 {@link BaseMapper}，自动获得标准的 CRUD 方法（insert、deleteById、selectById、updateById、
 * selectList 等），无需额外编写 SQL。同时提供了两个自定义查询方法，用于按员工和会计期间检索
 * 个税累计预扣记录（年度内截至当前期的累计收入和累计已缴税款）。
 * </p>
 *
 * @see PayTaxYtdRecord
 */
@Mapper
public interface PayTaxYtdRecordMapper extends BaseMapper<PayTaxYtdRecord> {

    /**
     * 根据员工 ID 和会计期间查询该员工指定期间的个税累计预扣记录。
     * <p>
     * 对应 SQL：
     * <pre>{@code SELECT * FROM pay_tax_ytd_record
     *  WHERE employee_id = #{employeeId} AND period = #{period}}</pre>
     * 返回该员工在给定扣缴期间（如 "2025-03"）的年度累计收入、累计免税收入、累计减除费用、
     * 累计专项扣除、累计专项附加扣除、累计已预扣预缴税额等数据。
     * </p>
     *
     * @param employeeId 员工 ID，对应 pay_tax_ytd_record.employee_id 字段
     * @param period     会计期间，格式通常为 "yyyy-MM"，如 "2025-03"
     * @return 匹配的 PayTaxYtdRecord 实体，若该员工在该期间无记录则返回 {@code null}
     */
    @Select("SELECT * FROM pay_tax_ytd_record WHERE employee_id = #{employeeId} AND period = #{period}")
    PayTaxYtdRecord selectByEmployeeAndPeriod(@Param("employeeId") Long employeeId,
                                              @Param("period") String period);

    /**
     * 根据员工 ID 查询该员工最新（最近一期）的个税累计预扣记录。
     * <p>
     * 对应 SQL：
     * <pre>{@code SELECT * FROM pay_tax_ytd_record
     *  WHERE employee_id = #{employeeId}
     *  ORDER BY period DESC LIMIT 1}</pre>
     * 按期间字段降序排列后取第一条，即最近一个扣缴期的年度累计数据。常用于获取员工截至当前期间
     * 的累计预扣税款余额，以计算本期应补（退）税额。
     * </p>
     *
     * @param employeeId 员工 ID，对应 pay_tax_ytd_record.employee_id 字段
     * @return 最新的 PayTaxYtdRecord 实体，若该员工没有任何记录则返回 {@code null}
     */
    @Select("SELECT * FROM pay_tax_ytd_record WHERE employee_id = #{employeeId} ORDER BY period DESC LIMIT 1")
    PayTaxYtdRecord selectLatestByEmployee(@Param("employeeId") Long employeeId);
}
