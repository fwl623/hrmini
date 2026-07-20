package com.company.hrms.workflow.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 入职等流程校验部门/职位是否存在，并读取审批/默认字段（只读）。
 */
@Mapper
public interface OrgLookupMapper {

    @Select("SELECT COUNT(1) FROM department WHERE id = #{id} AND deleted = 0")
    int countDepartment(@Param("id") Long id);

    @Select("SELECT COUNT(1) FROM position WHERE id = #{id} AND deleted = 0")
    int countPosition(@Param("id") Long id);

    @Select("SELECT code FROM department WHERE id = #{id} AND deleted = 0 LIMIT 1")
    String selectDepartmentCode(@Param("id") Long id);

    @Select("SELECT name FROM department WHERE id = #{id} AND deleted = 0 LIMIT 1")
    String selectDepartmentName(@Param("id") Long id);

    @Select("SELECT name FROM position WHERE id = #{id} AND deleted = 0 LIMIT 1")
    String selectPositionName(@Param("id") Long id);

    @Select("SELECT head_employee_id FROM department WHERE id = #{id} AND deleted = 0 LIMIT 1")
    Long selectDepartmentHeadEmployeeId(@Param("id") Long id);

    @Select("SELECT is_standard FROM position WHERE id = #{id} AND deleted = 0 LIMIT 1")
    Integer selectPositionIsStandard(@Param("id") Long id);

    @Select("SELECT default_probation_months FROM position WHERE id = #{id} AND deleted = 0 LIMIT 1")
    Integer selectPositionDefaultProbationMonths(@Param("id") Long id);

    @Select("SELECT rank_max FROM position WHERE id = #{id} AND deleted = 0 LIMIT 1")
    String selectPositionRankMax(@Param("id") Long id);

    /** 当前用户作为负责人的部门 ID 列表 */
    @Select("""
            SELECT d.id FROM department d
            INNER JOIN employee e ON e.id = d.head_employee_id AND (e.deleted = 0 OR e.deleted IS NULL)
            WHERE e.user_id = #{userId} AND d.deleted = 0
            """)
    List<Long> selectDepartmentIdsByHeadUserId(@Param("userId") Long userId);

    @Select("""
            SELECT old_mobile AS oldMobile, new_mobile AS newMobile, reason, status,
                   employee_id AS employeeId
            FROM employee_mobile_change_application
            WHERE id = #{id}
            LIMIT 1
            """)
    java.util.Map<String, Object> selectMobileChangeBrief(@Param("id") Long id);

    /**
     * 审批交接人选人：姓名/工号模糊，仅在职/试用/待离职；不含敏感字段。
     * 不走花名册 DataScope，供财务经理等部门负责人在离职审批中选交接人。
     */
    @Select("""
            SELECT e.id AS employeeId,
                   e.name AS name,
                   e.employee_no AS empNo,
                   d.name AS department
            FROM employee e
            LEFT JOIN department d ON d.id = e.department_id AND d.deleted = 0
            WHERE (e.deleted = 0 OR e.deleted IS NULL)
              AND e.employment_status IN (10, 20, 30)
              AND (
                    e.name LIKE CONCAT('%', #{keyword}, '%')
                 OR e.employee_no LIKE CONCAT('%', #{keyword}, '%')
              )
            ORDER BY e.id DESC
            LIMIT 20
            """)
    List<java.util.Map<String, Object>> searchHandoverCandidates(@Param("keyword") String keyword);

    /** 按 userId 查员工姓名与职位名（审批时间线展示） */
    @Select("""
            SELECT e.name AS name, p.name AS positionName
            FROM employee e
            LEFT JOIN position p ON p.id = e.position_id AND (p.deleted = 0 OR p.deleted IS NULL)
            WHERE e.user_id = #{userId}
              AND (e.deleted = 0 OR e.deleted IS NULL)
            LIMIT 1
            """)
    java.util.Map<String, Object> selectEmployeeNameAndPositionByUserId(@Param("userId") Long userId);

    @Select("SELECT username FROM sys_user WHERE id = #{userId} LIMIT 1")
    String selectUsernameByUserId(@Param("userId") Long userId);

    @Select("""
            SELECT COUNT(1) FROM sys_user_role ur
            INNER JOIN sys_role r ON r.id = ur.role_id
            WHERE ur.user_id = #{userId} AND r.code = 'SYS_ADMIN'
            """)
    int countSysAdminRole(@Param("userId") Long userId);

    /** 按 employeeId 查员工姓名与职位名（交接人展示） */
    @Select("""
            SELECT e.name AS name, p.name AS positionName
            FROM employee e
            LEFT JOIN position p ON p.id = e.position_id AND (p.deleted = 0 OR p.deleted IS NULL)
            WHERE e.id = #{employeeId}
              AND (e.deleted = 0 OR e.deleted IS NULL)
            LIMIT 1
            """)
    java.util.Map<String, Object> selectEmployeeNameAndPositionByEmployeeId(@Param("employeeId") Long employeeId);
}
