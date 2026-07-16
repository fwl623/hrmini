package com.company.hrms.payroll.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.hrms.payroll.entity.PayTaxBracket;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * MyBatis-Plus Mapper for the {@link PayTaxBracket} entity.
 * <p>
 * Maps the {@code pay_tax_bracket} table, which stores progressive tax rate brackets
 * used for individual income tax (IIT) calculation. Each row defines a tax bracket
 * for a given tax year, including the minimum taxable income threshold, the tax rate,
 * and the quick deduction amount.
 * </p>
 * <p>
 * Inherits from {@link BaseMapper}, which provides standard CRUD operations
 * (insert, delete, update, selectById, selectList, etc.) without additional
 * implementation.
 * </p>
 */
@Mapper
public interface PayTaxBracketMapper extends BaseMapper<PayTaxBracket> {

    /**
     * Retrieves all tax brackets for a given tax year, ordered by the minimum
     * taxable income ({@code min_taxable}) in ascending order.
     * <p>
     * The SQL query filters by {@code tax_year} and sorts by {@code min_taxable ASC},
     * ensuring the brackets are returned in the order they should be evaluated
     * during tax calculation (lowest threshold first).
     * </p>
     *
     * @param taxYear the tax year to query (e.g., {@code 2024}). Must not be {@code null}.
     * @return a list of {@link PayTaxBracket} entries for the specified year,
     *         sorted by minimum taxable amount ascending; an empty list if no
     *         brackets are found for that year
     */
    @Select("SELECT * FROM pay_tax_bracket WHERE tax_year = #{taxYear} ORDER BY min_taxable ASC")
    List<PayTaxBracket> selectByTaxYear(@Param("taxYear") Integer taxYear);
}
