package com.company.hrms.payroll.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.hrms.payroll.entity.PayrollDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * MyBatis-Plus Mapper interface for the {@link PayrollDetail} entity.
 * <p>
 * Operates on the {@code payroll_detail} table, which stores individual
 * payroll calculation results for each employee within a batch cycle.
 * Inherits common CRUD methods from {@link BaseMapper}.
 * </p>
 */
@Mapper
public interface PayrollDetailMapper extends BaseMapper<PayrollDetail> {

    /**
     * Retrieve all payroll detail records belonging to a specific batch.
     * <p>
     * SQL: {@code SELECT * FROM payroll_detail WHERE batch_id = #{batchId}}
     * </p>
     *
     * @param batchId the unique identifier of the payroll batch (not null)
     * @return a list of matching {@link PayrollDetail} records, or an empty list if none found
     */
    @Select("SELECT * FROM payroll_detail WHERE batch_id = #{batchId}")
    List<PayrollDetail> selectByBatchId(@Param("batchId") Long batchId);

    /**
     * Retrieve a single payroll detail record for a specific employee within a
     * specific batch.
     * <p>
     * SQL: {@code SELECT * FROM payroll_detail WHERE batch_id = #{batchId}
     * AND employee_id = #{employeeId}}
     * </p>
     *
     * @param batchId    the unique identifier of the payroll batch (not null)
     * @param employeeId the unique identifier of the employee (not null)
     * @return the matching {@link PayrollDetail} record, or {@code null} if no
     *         such record exists
     */
    @Select("SELECT * FROM payroll_detail WHERE batch_id = #{batchId} AND employee_id = #{employeeId}")
    PayrollDetail selectByBatchAndEmployee(@Param("batchId") Long batchId,
                                           @Param("employeeId") Long employeeId);

    /**
     * Insert multiple payroll detail records in a single batch operation.
     * <p>
     * The corresponding SQL is defined in the XML mapper file
     * (e.g. {@code PayrollDetailMapper.xml}) and typically uses a
     * {@code <foreach>} loop inside an {@code INSERT ALL} or
     * multi-row insert statement.
     * </p>
     *
     * @param list the list of {@link PayrollDetail} entities to insert (not null,
     *             must not contain null elements)
     * @return the number of rows successfully inserted
     */
    int batchInsert(@Param("list") List<PayrollDetail> list);
}
