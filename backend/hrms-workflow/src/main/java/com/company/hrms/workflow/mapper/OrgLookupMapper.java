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
}
