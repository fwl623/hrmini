package com.company.hrms.attendance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.hrms.attendance.entity.AttendanceGroup;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * MyBatis-Plus Mapper for the {@link AttendanceGroup} entity.
 * <p>
 * Provides CRUD operations inherited from {@link BaseMapper<AttendanceGroup>}
 * (insert, update, deleteById, selectById, selectList, etc.) without additional
 * implementation code. Custom query methods defined in this interface target
 * specific business needs such as duplicate-name validation and member-count
 * statistics.
 * </p>
 */
@Mapper
public interface AttendanceGroupMapper extends BaseMapper<AttendanceGroup> {

    /**
     * Counts the number of employee members currently associated with the
     * specified attendance group.
     * <p>
     * The SQL queries the {@code attendance_group_member} join table, which
     * links attendance groups to their employee members. A return value of
     * zero indicates the group has no members (and may be safe to delete if
     * business rules allow it).
     * </p>
     *
     * @param groupId the primary key (ID) of the attendance group whose
     *                member count is to be calculated; must not be {@code null}
     * @return the total number of member records for the given group, or
     *         {@code 0} if the group has no members or does not exist
     */
    @Select("SELECT COUNT(*) FROM attendance_group_member WHERE group_id = #{groupId}")
    int countMemberByGroupId(@Param("groupId") Long groupId);

    /**
     * Counts attendance groups that match the specified name and have not been
     * soft-deleted.
     * <p>
     * This method is typically used for uniqueness validation before creating or
     * renaming an attendance group. A non-zero return value indicates that the
     * name is already taken by an active (non-deleted) group. The filter
     * {@code deleted = 0} excludes logically deleted records so that duplicate
     * checks are only enforced against currently visible groups.
     * </p>
     *
     * @param name the attendance group name to check for duplicates; must not
     *             be {@code null} or empty
     * @return the number of active attendance groups with the given name
     *         (expected to be {@code 0} for a unique name, or {@code 1} if a
     *         duplicate exists)
     */
    @Select("SELECT COUNT(*) FROM attendance_group WHERE name = #{name} AND deleted = 0")
    int countByName(@Param("name") String name);
}
