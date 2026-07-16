package com.company.hrms.employee.mapper;

import com.company.hrms.employee.entity.Employee;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 员工 Mapper
 */
@Mapper
public interface EmployeeMapper {

    List<Employee> search(@Param("keyword") String keyword,
                          @Param("deptIds") List<Long> deptIds,
                          @Param("positionIds") List<Long> positionIds,
                          @Param("statusList") List<Integer> statusList,
                          @Param("gradeList") List<String> gradeList,
                          @Param("hireDateFrom") LocalDate hireDateFrom,
                          @Param("hireDateTo") LocalDate hireDateTo,
                          @Param("dataScope") String dataScope);

    Long countSearch(@Param("keyword") String keyword,
                     @Param("deptIds") List<Long> deptIds,
                     @Param("positionIds") List<Long> positionIds,
                     @Param("statusList") List<Integer> statusList,
                     @Param("gradeList") List<String> gradeList,
                     @Param("hireDateFrom") LocalDate hireDateFrom,
                     @Param("hireDateTo") LocalDate hireDateTo,
                     @Param("dataScope") String dataScope);

    Employee selectById(@Param("id") Long id);
    Employee selectByEmployeeNo(@Param("employeeNo") String employeeNo);
    Employee selectByMobile(@Param("mobile") String mobile);
    int insert(Employee employee);
    int updateById(Employee employee);

    /** 试用期结束日在 [from, to] 内的试用员工 */
    List<Employee> listPendingRegularization(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
