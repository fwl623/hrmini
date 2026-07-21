package com.company.hrms.module.auth.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 人力资源数据概览只读统计。
 */
@Mapper
public interface AnalyticsMapper {

    @Select("""
            SELECT COUNT(1) FROM employee
            WHERE deleted = 0 AND employment_status IN (10, 20, 30)
            """)
    Long countActiveEmployees();

    @Select("""
            SELECT COUNT(1) FROM employee
            WHERE deleted = 0
              AND hire_date >= #{from}
              AND hire_date <= #{to}
            """)
    Long countHiresBetween(@Param("from") String from, @Param("to") String to);

    @Select("""
            SELECT COUNT(1) FROM employee
            WHERE deleted = 0
              AND employment_status = 40
              AND last_work_day IS NOT NULL
              AND last_work_day >= #{from}
              AND last_work_day <= #{to}
            """)
    Long countResignationsBetween(@Param("from") String from, @Param("to") String to);

    @Select("""
            SELECT DATE_FORMAT(hire_date, '%Y-%m-%d') AS date, COUNT(1) AS cnt
            FROM employee
            WHERE deleted = 0
              AND hire_date >= #{from}
              AND hire_date <= #{to}
            GROUP BY DATE_FORMAT(hire_date, '%Y-%m-%d')
            ORDER BY date ASC
            """)
    List<Map<String, Object>> listHireTrend(@Param("from") String from, @Param("to") String to);

    @Select("""
            SELECT DATE_FORMAT(last_work_day, '%Y-%m-%d') AS date, COUNT(1) AS cnt
            FROM employee
            WHERE deleted = 0
              AND employment_status = 40
              AND last_work_day IS NOT NULL
              AND last_work_day >= #{from}
              AND last_work_day <= #{to}
            GROUP BY DATE_FORMAT(last_work_day, '%Y-%m-%d')
            ORDER BY date ASC
            """)
    List<Map<String, Object>> listResignTrend(@Param("from") String from, @Param("to") String to);

    @Select("""
            SELECT d.name AS deptName, COUNT(e.id) AS headcount
            FROM department d
            LEFT JOIN employee e ON e.department_id = d.id
              AND e.deleted = 0 AND e.employment_status IN (10, 20, 30)
            WHERE d.deleted = 0
            GROUP BY d.id, d.name
            HAVING headcount > 0
            ORDER BY headcount DESC
            LIMIT 12
            """)
    List<Map<String, Object>> listDepartmentDistribution();

    @Select("""
            SELECT process_type AS processType,
                   COUNT(1) AS submitted,
                   SUM(CASE WHEN UPPER(status) IN ('APPROVED', 'COMPLETED') THEN 1 ELSE 0 END) AS approved
            FROM approval_instance
            WHERE created_at >= #{fromTs}
              AND created_at < #{toTsExclusive}
            GROUP BY process_type
            ORDER BY submitted DESC
            """)
    List<Map<String, Object>> listWorkflowThroughput(
            @Param("fromTs") String fromTs,
            @Param("toTsExclusive") String toTsExclusive);

    @Select("""
            SELECT
              (SELECT COUNT(DISTINCT employee_id) FROM attendance_record
               WHERE punch_date >= #{from} AND punch_date <= #{to}) AS punched,
              (SELECT COUNT(1) FROM employee
               WHERE deleted = 0 AND employment_status IN (10, 20, 30)) AS total
            """)
    Map<String, Object> punchStatsBetween(@Param("from") String from, @Param("to") String to);

    @Select("""
            SELECT b.period AS period, COALESCE(SUM(d.net_salary), 0) AS netTotal
            FROM payroll_batch b
            INNER JOIN payroll_detail d ON d.batch_id = b.id AND d.calc_status = 'SUCCESS'
            WHERE b.period >= #{periodFrom}
              AND b.period <= #{periodTo}
            GROUP BY b.period
            ORDER BY b.period ASC
            """)
    List<Map<String, Object>> listCostTrend(
            @Param("periodFrom") String periodFrom,
            @Param("periodTo") String periodTo);

    @Select("""
            SELECT COUNT(1) AS submitted,
                   SUM(CASE WHEN UPPER(status) IN ('APPROVED', 'COMPLETED') THEN 1 ELSE 0 END) AS approved
            FROM approval_instance
            WHERE created_at >= #{fromTs}
              AND created_at < #{toTsExclusive}
            """)
    Map<String, Object> approvalStatsBetween(
            @Param("fromTs") String fromTs,
            @Param("toTsExclusive") String toTsExclusive);
}
