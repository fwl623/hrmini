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

    /**
     * 取可复用工号；须排除 employee 表仍占用的号码（离职只标 reuse 未腾出 uk_employee_no 时会撞唯一键）。
     */
    @Select("""
            SELECT h.id, h.employee_no, h.year, h.dept_code, h.employee_id, h.reuse_flag, h.created_at
            FROM employee_no_history h
            WHERE h.year = #{year} AND h.dept_code = #{deptCode} AND h.reuse_flag = 1
              AND NOT EXISTS (
                    SELECT 1 FROM employee e
                    WHERE e.employee_no = h.employee_no
                      AND (e.deleted = 0 OR e.deleted IS NULL)
              )
            ORDER BY h.id ASC
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
              AND CHAR_LENGTH(employee_no) = 9
              AND (deleted = 0 OR deleted IS NULL)
            """)
    Integer selectMaxSeqFromEmployee(@Param("year") String year, @Param("deptCode") String deptCode);
}
