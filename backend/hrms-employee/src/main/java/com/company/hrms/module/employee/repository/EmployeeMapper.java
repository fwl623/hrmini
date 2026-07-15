package com.company.hrms.module.employee.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.hrms.module.employee.entity.EmployeeEntity;
import com.company.hrms.module.employee.vo.EmployeeListVO;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * 员工 Mapper
 */
@Repository
public interface EmployeeMapper extends BaseMapper<EmployeeEntity> {

    /**
     * 高级搜索：花名册分页查询
     */
    List<EmployeeListVO> search(@Param("keyword") String keyword,
                                @Param("deptIds") List<Long> deptIds,
                                @Param("positionIds") List<Long> positionIds,
                                @Param("statusList") List<String> statusList,
                                @Param("gradeList") List<String> gradeList,
                                @Param("hireDateFrom") LocalDate hireDateFrom,
                                @Param("hireDateTo") LocalDate hireDateTo,
                                @Param("dataScope") String dataScope);

    Long countSearch(@Param("keyword") String keyword,
                     @Param("deptIds") List<Long> deptIds,
                     @Param("positionIds") List<Long> positionIds,
                     @Param("statusList") List<String> statusList,
                     @Param("gradeList") List<String> gradeList,
                     @Param("hireDateFrom") LocalDate hireDateFrom,
                     @Param("hireDateTo") LocalDate hireDateTo,
                     @Param("dataScope") String dataScope);

    /**
     * 根据员工ID查询详情（关联表）
     */
    EmployeeEntity selectByEmployeeId(@Param("employeeId") Long employeeId);

    /**
     * 根据工号查询
     */
    EmployeeEntity selectByEmployeeNo(@Param("employeeNo") String employeeNo);

    /**
     * 根据手机号查询
     */
    EmployeeEntity selectByMobile(@Param("mobile") String mobile);
}
