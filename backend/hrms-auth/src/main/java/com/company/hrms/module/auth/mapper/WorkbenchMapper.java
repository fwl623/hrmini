package com.company.hrms.module.auth.mapper;

import com.company.hrms.module.auth.dto.WorkbenchSummaryVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 工作台聚合查询（只读跨表统计，不依赖他模块 Mapper）。
 */
@Mapper
public interface WorkbenchMapper {

    @Select("""
            SELECT COUNT(1) FROM employee
            WHERE deleted = 0 AND employment_status IN (10, 20, 30)
            """)
    Long countActiveEmployees();

    @Select("""
            SELECT COUNT(1) FROM employee
            WHERE deleted = 0
              AND hire_date >= DATE_FORMAT(CURDATE(), '%Y-%m-01')
              AND hire_date < DATE_ADD(DATE_FORMAT(CURDATE(), '%Y-%m-01'), INTERVAL 1 MONTH)
            """)
    Long countNewHiresThisMonth();

    @Select("""
            SELECT COUNT(1) FROM approval_task WHERE status = 'PENDING'
            """)
    Long countPendingApprovals();

    @Select("""
            SELECT COUNT(1) FROM attendance_daily_summary
            WHERE summary_date = CURDATE()
              AND day_status NOT IN ('NORMAL', 'LEAVE')
            """)
    Long countAttendanceAnomaliesToday();

    @Select("""
            SELECT
              (SELECT COUNT(DISTINCT employee_id) FROM attendance_record
               WHERE punch_date = CURDATE()) AS punched,
              (SELECT COUNT(1) FROM employee
               WHERE deleted = 0 AND employment_status IN (10, 20, 30)) AS total
            """)
    Map<String, Object> todayPunchStats();

    @Select("""
            SELECT d.name AS deptName, COUNT(e.id) AS headcount
            FROM department d
            LEFT JOIN employee e ON e.department_id = d.id
              AND e.deleted = 0 AND e.employment_status IN (10, 20, 30)
            WHERE d.deleted = 0
            GROUP BY d.id, d.name
            ORDER BY headcount DESC
            LIMIT 10
            """)
    List<WorkbenchSummaryVO.DeptHeadcountStat> listDepartmentStats();

    @Select("""
            SELECT DATE_FORMAT(login_time, '%Y-%m-%d') AS date, COUNT(1) AS cnt
            FROM login_log
            WHERE success = 1
              AND login_time >= DATE_SUB(CURDATE(), INTERVAL 6 DAY)
            GROUP BY DATE_FORMAT(login_time, '%Y-%m-%d')
            ORDER BY date ASC
            """)
    List<Map<String, Object>> listLoginTrendLast7Days();
}
