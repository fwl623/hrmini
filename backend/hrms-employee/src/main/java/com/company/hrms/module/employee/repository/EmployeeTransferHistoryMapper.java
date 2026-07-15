package com.company.hrms.module.employee.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.hrms.module.employee.entity.EmployeeTransferHistoryEntity;
import com.company.hrms.module.employee.vo.EmployeeTransferHistoryVO;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 调岗历史 Mapper
 */
@Repository
public interface EmployeeTransferHistoryMapper extends BaseMapper<EmployeeTransferHistoryEntity> {

    /**
     * 查询员工调岗历史
     */
    List<EmployeeTransferHistoryVO> selectByEmployeeId(@Param("employeeId") Long employeeId);
}
