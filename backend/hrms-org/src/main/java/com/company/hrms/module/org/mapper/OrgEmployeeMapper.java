package com.company.hrms.module.org.mapper;

import com.company.hrms.module.org.dto.DeptHeadcountRow;
import com.company.hrms.module.org.dto.EmpIdNameRow;
import com.company.hrms.module.org.dto.PositionHeadcountRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * org 模块对 employee 表的窄查询/更新（避免依赖 hrms-employee）。
 * <p>
 * 口径约定：
 * <ul>
 *   <li>active(10/20)：PRD 人数统计（试用+正式）</li>
 *   <li>belonging(NOT 40)：删除校验（仍挂在部门的人，含待离职）</li>
 * </ul>
 */
@Mapper
public interface OrgEmployeeMapper {

    /** PRD 人数：试用期 + 正式 */
    @Select("""
            SELECT COUNT(1) FROM employee
            WHERE department_id = #{departmentId}
              AND deleted = 0
              AND employment_status IN (10, 20)
            """)
    int countActiveByDeptId(@Param("departmentId") Long departmentId);

    @Select("""
            SELECT COUNT(1) FROM employee e
            INNER JOIN department d ON e.department_id = d.id AND d.deleted = 0
            WHERE e.deleted = 0
              AND e.employment_status IN (10, 20)
              AND d.path LIKE CONCAT(#{pathPrefix}, '%')
            """)
    int countActiveByPathPrefix(@Param("pathPrefix") String pathPrefix);

    /**
     * 删除/清空校验：仍归属本部门的员工（含待离职 30，不含已离职 40）。
     */
    @Select("""
            SELECT COUNT(1) FROM employee
            WHERE department_id = #{departmentId}
              AND deleted = 0
              AND employment_status <> 40
            """)
    int countBelongingByDeptId(@Param("departmentId") Long departmentId);

    /** 各部门在职人数（10/20），用于树聚合 */
    @Select("""
            SELECT department_id AS deptId, COUNT(1) AS cnt
            FROM employee
            WHERE deleted = 0 AND employment_status IN (10, 20)
            GROUP BY department_id
            """)
    List<DeptHeadcountRow> countActiveGroupByDept();

    @Select("SELECT name FROM employee WHERE id = #{employeeId} AND deleted = 0 LIMIT 1")
    String selectNameById(@Param("employeeId") Long employeeId);

    @Select("SELECT user_id FROM employee WHERE id = #{employeeId} AND deleted = 0 LIMIT 1")
    Long selectUserIdByEmployeeId(@Param("employeeId") Long employeeId);

    @SelectProvider(type = OrgEmployeeSqlProvider.class, method = "selectNamesByIds")
    List<EmpIdNameRow> selectNamesByIds(@Param("ids") List<Long> ids);

    @Update("""
            UPDATE employee SET department_id = #{toDeptId}, updated_at = NOW()
            WHERE department_id = #{fromDeptId} AND deleted = 0
            """)
    int transferDepartment(@Param("fromDeptId") Long fromDeptId, @Param("toDeptId") Long toDeptId);

    /** 各职位归属人数（含待离职 30，不含已离职 40） */
    @Select("""
            SELECT position_id AS positionId, COUNT(1) AS cnt
            FROM employee
            WHERE deleted = 0
              AND employment_status IN (10, 20, 30)
              AND position_id IS NOT NULL
            GROUP BY position_id
            """)
    List<PositionHeadcountRow> countBelongingGroupByPosition();

    @Select("""
            SELECT DISTINCT user_id FROM employee
            WHERE department_id = #{departmentId}
              AND deleted = 0
              AND user_id IS NOT NULL
            """)
    List<Long> selectUserIdsByDeptId(@Param("departmentId") Long departmentId);

    @Select("""
            SELECT COUNT(1) FROM employee
            WHERE position_id = #{positionId} AND deleted = 0
              AND employment_status IN (10, 20, 30)
            """)
    int countByPositionId(@Param("positionId") Long positionId);

    @Select("""
            SELECT COUNT(1) FROM employee
            WHERE id = #{employeeId} AND deleted = 0
              AND employment_status IN (10, 20, 30)
            """)
    int countActiveEmployeeById(@Param("employeeId") Long employeeId);

    @Select("""
            SELECT DISTINCT grade FROM employee
            WHERE position_id = #{positionId} AND deleted = 0
              AND employment_status IN (10, 20, 30)
              AND grade IS NOT NULL AND grade <> ''
            """)
    List<String> selectGradesByPositionId(@Param("positionId") Long positionId);
}
