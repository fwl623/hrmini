package com.company.hrms.payroll.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.hrms.payroll.entity.PayrollAdjustment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * MyBatis-Plus Mapper for the {@link PayrollAdjustment} entity.
 * <p>
 * Maps to the {@code payroll_adjustment} table and provides data access operations
 * for payroll adjustment records (e.g., bonuses, deductions, corrections applied to
 * a specific payroll detail line). Inherits generic CRUD methods (insert, update,
 * delete, selectById, selectPage, etc.) from {@link BaseMapper} without requiring
 * additional XML or SQL declarations.
 * </p>
 *
 * @see PayrollAdjustment
 * @see BaseMapper
 */
@Mapper
public interface PayrollAdjustmentMapper extends BaseMapper<PayrollAdjustment> {

    /**
     * Retrieves all payroll adjustment records that belong to a given payroll detail.
     * <p>
     * Executes {@code SELECT * FROM payroll_adjustment WHERE detail_id = #{detailId}},
     * filtering the adjustment rows by the foreign key column {@code detail_id}.
     * This is commonly used when displaying or recalculating the full breakdown of
     * adjustments (bonus, deduction, penalty, etc.) attached to one payroll detail line.
     * </p>
     *
     * @param detailId the primary key of the parent payroll detail record
     *                 ({@code payroll_detail.id}); must not be {@code null}
     * @return a list of {@link PayrollAdjustment} records associated with the given
     *         detail ID, or an empty list if none exist
     */
    @Select("SELECT * FROM payroll_adjustment WHERE detail_id = #{detailId}")
    List<PayrollAdjustment> selectByDetailId(@Param("detailId") Long detailId);
}
