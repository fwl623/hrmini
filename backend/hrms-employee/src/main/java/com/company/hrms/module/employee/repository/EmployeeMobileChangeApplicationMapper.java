package com.company.hrms.module.employee.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.hrms.module.employee.entity.EmployeeMobileChangeApplicationEntity;
import com.company.hrms.module.employee.vo.MobileChangeAppVO;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 手机号变更申请 Mapper
 */
@Repository
public interface EmployeeMobileChangeApplicationMapper extends BaseMapper<EmployeeMobileChangeApplicationEntity> {

    /**
     * HR端查询手机号变更待办列表
     */
    List<MobileChangeAppVO> selectPendingList();

    /**
     * 查询员工本人的手机号变更申请记录
     */
    List<MobileChangeAppVO> selectByEmployeeId(@Param("employeeId") Long employeeId);
}
