package com.company.hrms.attendance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.hrms.attendance.entity.OvertimeLedger;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * MyBatis-Plus Mapper 接口，用于操作加班台账（OvertimeLedger）表。
 *
 * <p>加班台账（overtime_ledger 表）记录员工的加班工时明细，包括加班日期、时长、
 * 加班类型（工作日/休息日/节假日加班）、加班费率及对应的账期等信息。
 * 该 Mapper 继承 {@link BaseMapper<OvertimeLedger>}，自动获得实体基础的
 * 增删改查（CRUD）与分页能力，同时声明了自定义查询方法。</p>
 */
@Mapper
public interface OvertimeLedgerMapper extends BaseMapper<OvertimeLedger> {

    /**
     * 根据员工 ID 和账期查询该员工的加班台账记录。
     *
     * <p>SQL 逻辑：全表扫描（SELECT *）过滤出 {@code employee_id} 等于
     * {@code employeeId} 且 {@code period} 等于 {@code period} 的行。
     * 两条筛选条件通过 AND 连接，结果集可能包含多条记录（例如同一账期内
     * 存在多天加班）。</p>
     *
     * @param employeeId 员工主键 ID，对应 overtime_ledger.employee_id 字段
     * @param period     账期标识（如 "2024-07"），对应 overtime_ledger.period 字段
     * @return 匹配的员工加班台账列表；若无匹配记录，则返回空列表
     */
    @Select("SELECT * FROM overtime_ledger WHERE employee_id = #{employeeId} AND period = #{period}")
    List<OvertimeLedger> selectByEmployeeAndPeriod(@Param("employeeId") Long employeeId,
                                                   @Param("period") String period);

    /**
     * 根据账期查询所有加班台账记录
     */
    @Select("SELECT * FROM overtime_ledger WHERE period = #{period} ORDER BY employee_id, ledger_date")
    List<OvertimeLedger> selectByPeriod(@Param("period") String period);
}
