package com.company.hrms.employee.mapper;

import com.company.hrms.employee.entity.Employee;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 员工 Mapper 接口
 * <p>
 * 提供员工主表的 CRUD 操作。
 * 高级搜索支持多维度筛选（关键词、部门、职位、状态、职级、日期范围），
 * 通过 ${dataScope} 注入行级数据权限。
 * </p>
 */
@Mapper
public interface EmployeeMapper {

    /** 高级搜索列表（含 DataScope 行级权限过滤） */
    List<Employee> search(@Param("keyword") String keyword,
                          @Param("deptIds") List<Long> deptIds,
                          @Param("positionIds") List<Long> positionIds,
                          @Param("statusList") List<Integer> statusList,
                          @Param("gradeList") List<String> gradeList,
                          @Param("hireDateFrom") LocalDate hireDateFrom,
                          @Param("hireDateTo") LocalDate hireDateTo,
                          @Param("dataScope") String dataScope);

    /** 高级搜索计数 */
    Long countSearch(@Param("keyword") String keyword,
                     @Param("deptIds") List<Long> deptIds,
                     @Param("positionIds") List<Long> positionIds,
                     @Param("statusList") List<Integer> statusList,
                     @Param("gradeList") List<String> gradeList,
                     @Param("hireDateFrom") LocalDate hireDateFrom,
                     @Param("hireDateTo") LocalDate hireDateTo,
                     @Param("dataScope") String dataScope);

    /** 按主键查询 */
    Employee selectById(@Param("id") Long id);

    /** 按工号查询 */
    Employee selectByEmployeeNo(@Param("employeeNo") String employeeNo);

    /** 按手机号查询（唯一索引） */
    Employee selectByMobile(@Param("mobile") String mobile);

    /** 新增员工 */
    int insert(Employee employee);

    /** 按主键更新（仅更新非 null 字段） */
    int updateById(Employee employee);
}
