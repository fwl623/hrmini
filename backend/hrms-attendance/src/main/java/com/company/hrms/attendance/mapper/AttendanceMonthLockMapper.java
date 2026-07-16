package com.company.hrms.attendance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.hrms.attendance.entity.AttendanceMonthLock;
import org.apache.ibatis.annotations.Mapper;

/**
 * MyBatis-Plus Mapper 接口，操作 {@link AttendanceMonthLock} 实体。
 * <p>
 * {@link AttendanceMonthLock} 对应月考勤锁定记录表，用于标记某一年某月的考勤数据是否已锁定。
 * 锁定后该月考勤数据不允许修改、删除，以此保证数据的一致性和审计追溯。
 * 本接口继承 {@link BaseMapper}，自动获得 CRUD、分页、批量操作等能力，无需额外编写 SQL。
 * 如需扩展统计、批量锁定等查询，可在本接口中添加自定义方法并配合注解或 XML 实现。
 * </p>
 *
 * @see AttendanceMonthLock
 * @see BaseMapper
 */
@Mapper
public interface AttendanceMonthLockMapper extends BaseMapper<AttendanceMonthLock> {
}
