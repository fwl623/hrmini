package com.company.hrms.payroll.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.hrms.payroll.entity.PayrollBatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 核算批次 Mapper，负责对 {@link PayrollBatch} 实体进行数据库访问操作。
 *
 * <p>继承自 MyBatis-Plus 的 {@link BaseMapper}，自动获得基础的 CRUD 方法（如 insert、deleteById、selectById、updateById 等）。
 * 本接口额外定义了核算批次模块业务所需的定制查询与更新方法。</p>
 *
 * <p>核算是薪酬模块的核心流程之一，批次记录了某次发薪核算的元信息，包括账期、进度、发放结果等。</p>
 *
 * @see PayrollBatch
 */
@Mapper
public interface PayrollBatchMapper extends BaseMapper<PayrollBatch> {

    /**
     * 根据账期（period）查询核算批次。
     *
     * <p>执行的全表查询 SQL 等价于：</p>
     * <pre>{@code SELECT * FROM payroll_batch WHERE period = #{period}}</pre>
     * <p>账期格式通常为 "yyyyMM"，例如 "202407" 表示 2024 年 7 月。</p>
     *
     * @param period 账期字符串，格式 "yyyyMM"，不可为空
     * @return 匹配的核算批次对象；如果指定账期不存在则返回 {@code null}
     */
    @Select("SELECT * FROM payroll_batch WHERE period = #{period}")
    PayrollBatch selectByPeriod(@Param("period") String period);

    /**
     * 更新核算批次的核算进度数据，包括成功/异常人数及应发/实发总额。
     *
     * <p>执行的 SQL 等价于：</p>
     * <pre>{@code UPDATE payroll_batch SET success_count = #{successCount},
     * anomaly_count = #{anomalyCount}, gross_total = #{grossTotal},
     * net_total = #{netTotal} WHERE id = #{id}}</pre>
     * <p>此方法通常在单笔核算完成后由业务层调用，用于汇总当前批次的核算结果。</p>
     *
     * @param batch 包含更新数据的核算批次对象，其 {@code id} 字段用于定位目标行，
     *              {@code successCount}、{@code anomalyCount}、{@code grossTotal}、
     *              {@code netTotal} 为待更新的字段值，不可为 {@code null}
     * @return 受影响的记录行数（正常为 1，如果指定 id 不存在则为 0）
     */
    @Update("UPDATE payroll_batch SET success_count = #{successCount}, anomaly_count = #{anomalyCount}, gross_total = #{grossTotal}, net_total = #{netTotal} WHERE id = #{id}")
    int updateProgress(PayrollBatch batch);

    /**
     * 更新核算批次的处理状态。
     *
     * <p>执行的 SQL 等价于：</p>
     * <pre>{@code UPDATE payroll_batch SET status = #{status} WHERE id = #{id}}</pre>
     * <p>状态值通常定义在 {@code PayrollBatch} 实体或相关常量类中，例如：</p>
     * <ul>
     *   <li>INIT — 初始待核算</li>
     *   <li>PROCESSING — 核算中</li>
     *   <li>COMPLETED — 核算完成</li>
     *   <li>ARCHIVED — 已归档</li>
     * </ul>
     *
     * @param id     核算批次主键 ID，不可为空
     * @param status 目标状态字符串，不可为空
     * @return 受影响的记录行数（正常为 1，如果指定 id 不存在则为 0）
     */
    @Update("UPDATE payroll_batch SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status);
}
