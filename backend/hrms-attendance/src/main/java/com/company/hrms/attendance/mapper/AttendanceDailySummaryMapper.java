package com.company.hrms.attendance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.hrms.attendance.entity.AttendanceDailySummary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

/**
 * MyBatis-Plus Mapper 接口，负责 {@link AttendanceDailySummary} 实体（日考勤汇总表）的数据库访问。
 * <p>
 * 继承 {@link BaseMapper} 获得通用的 CRUD 操作（insert、deleteById、updateById、selectById、selectList 等），
 * 并在此声明针对日考勤汇总业务场景的自定义查询与写入方法。
 * </p>
 * <p>
 * 对应的表结构：attendance_daily_summary 表，按员工 + 日期维度存储每日考勤汇总数据，
 * 包括出勤状态、打卡时间、迟到/早退/缺勤标记等统计信息，是月考勤统计和工资核算的基础数据源。
 * </p>
 */
@Mapper
public interface AttendanceDailySummaryMapper extends BaseMapper<AttendanceDailySummary> {

    /**
     * 根据员工 ID 与汇总日期查询当日的考勤汇总记录。
     * <p>
     * SQL 逻辑：<br>
     * {@code SELECT * FROM attendance_daily_summary WHERE employee_id = #{employeeId} AND summary_date = #{summaryDate}}<br>
     * 精确匹配一条记录（employee_id + summary_date 应为唯一约束）。
     * </p>
     *
     * @param employeeId  员工 ID，对应 employee 表主键
     * @param summaryDate 汇总日期（yyyy-MM-dd）
     * @return 匹配的 {@link AttendanceDailySummary} 对象；若不存在则返回 {@code null}
     */
    @Select("SELECT * FROM attendance_daily_summary WHERE employee_id = #{employeeId} AND summary_date = #{summaryDate}")
    AttendanceDailySummary selectByEmployeeAndDate(@Param("employeeId") Long employeeId,
                                                   @Param("summaryDate") LocalDate summaryDate);

    /**
     * 查询指定员工在指定日期范围内的所有日考勤汇总记录。
     * <p>
     * SQL 逻辑：<br>
     * {@code SELECT * FROM attendance_daily_summary WHERE employee_id = #{employeeId} AND summary_date BETWEEN #{startDate} AND #{endDate}}<br>
     * 返回该员工在起始日期（含）至结束日期（含）之间的全部汇总行，通常用于生成月考勤报表。
     * </p>
     *
     * @param employeeId 员工 ID，对应 employee 表主键
     * @param startDate  起始日期（包含），格式 yyyy-MM-dd
     * @param endDate    结束日期（包含），格式 yyyy-MM-dd
     * @return 匹配的 {@link AttendanceDailySummary} 列表；若指定范围内无记录则返回空列表
     */
    @Select("SELECT * FROM attendance_daily_summary WHERE employee_id = #{employeeId} AND summary_date BETWEEN #{startDate} AND #{endDate}")
    List<AttendanceDailySummary> selectByEmployeeAndPeriod(@Param("employeeId") Long employeeId,
                                                           @Param("startDate") LocalDate startDate,
                                                           @Param("endDate") LocalDate endDate);

    /**
     * 批量插入日考勤汇总记录。
     * <p>
     * 该方法在日终批量处理（每天考勤数据归档）时使用，将多条 {@link AttendanceDailySummary} 记录一次性写入数据库，
     * 相比逐条 insert 能显著降低连接开销。具体 SQL 由 MyBatis 映射文件或 MyBatis-Plus 提供的批量注入器实现。
     * </p>
     *
     * @param list 待插入的日考勤汇总记录集合，不应为 {@code null} 或空集合
     * @return 实际成功写入的行数；正常情况下应等于 {@code list.size()}
     */
    int batchInsert(@Param("list") List<AttendanceDailySummary> list);
}
