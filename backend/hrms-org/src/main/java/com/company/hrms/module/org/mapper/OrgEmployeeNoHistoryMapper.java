package com.company.hrms.module.org.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.hrms.module.org.entity.EmployeeNoHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * org 侧工号历史 Mapper（与 employee 模块 {@code EmployeeNoHistoryMapper} 同表不同 Bean，避免启动冲突）。
 */
@Mapper
public interface OrgEmployeeNoHistoryMapper extends BaseMapper<EmployeeNoHistory> {

    @Select("""
            SELECT id, employee_no, year, dept_code, employee_id, reuse_flag, created_at
            FROM employee_no_history
            WHERE year = #{year} AND dept_code = #{deptCode} AND reuse_flag = 1
            ORDER BY id ASC
            LIMIT 1
            """)
    EmployeeNoHistory selectOneReusable(@Param("year") String year, @Param("deptCode") String deptCode);

    @Select("""
            SELECT MAX(CAST(SUBSTRING(employee_no, 7, 3) AS UNSIGNED))
            FROM employee_no_history
            WHERE year = #{year} AND dept_code = #{deptCode}
            """)
    Integer selectMaxSeq(@Param("year") String year, @Param("deptCode") String deptCode);

    /**
     * 种子/历史遗漏时 history 可能为空，需与 employee 表已占用工号取更大序号，避免 uk_employee_no 冲突。
     */
    @Select("""
            SELECT MAX(CAST(SUBSTRING(employee_no, 7, 3) AS UNSIGNED))
            FROM employee
            WHERE employee_no LIKE CONCAT(#{year}, #{deptCode}, '%')
              AND CHAR_LENGTH(employee_no) >= 9
              AND (deleted = 0 OR deleted IS NULL)
            """)
    Integer selectMaxSeqFromEmployee(@Param("year") String year, @Param("deptCode") String deptCode);
}
