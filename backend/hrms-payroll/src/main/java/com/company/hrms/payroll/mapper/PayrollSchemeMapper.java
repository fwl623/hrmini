package com.company.hrms.payroll.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.hrms.payroll.entity.PayrollScheme;
import org.apache.ibatis.annotations.Mapper;

/**
 * MyBatis-Plus Mapper 接口，用于操作薪资账套（PayrollScheme）实体对应的数据库表。
 *
 * <p>继承自 {@link BaseMapper}<{@link PayrollScheme}>，自动提供增删改查（CRUD）、分页、批量操作等通用方法，
 * 无需额外编写 XML 映射文件或 SQL 语句即可完成大部分数据访问需求。</p>
 *
 * <p>所属模块：hrms-payroll（薪资结算模块）</p>
 *
 * <h3>对应数据库表</h3>
 * <ul>
 *   <li><b>表名：</b>payroll_scheme（薪资账套表）</li>
 *   <li><b>主键策略：</b>由 {@link PayrollScheme} 实体类中定义的 {@code @TableId} 注解决定</li>
 * </ul>
 *
 * <h3>提供的能力（继承自 BaseMapper）</h3>
 * <ul>
 *   <li>{@code insert} / {@code insertBatch} — 插入单条/批量记录</li>
 *   <li>{@code deleteById} / {@code deleteByMap} / {@code delete} — 按 ID / 条件删除</li>
 *   <li>{@code updateById} / {@code update} — 按 ID / 条件更新</li>
 *   <li>{@code selectById} / {@code selectList} / {@code selectPage} — 按 ID / 条件 / 分页查询</li>
 *   <li>{@code selectCount} — 条件计数</li>
 * </ul>
 *
 * <h3>自定义扩展</h3>
 * <p>当通用方法无法满足业务需求时，可在此接口中添加自定义方法，
 * 配合 MyBatis-Plus 的注解（如 {@code @Select}、{@code @Update}）或 XML 映射文件实现复杂查询。</p>
 *
 * @see BaseMapper
 * @see PayrollScheme
 */
@Mapper
public interface PayrollSchemeMapper extends BaseMapper<PayrollScheme> {
}
