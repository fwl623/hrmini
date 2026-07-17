package com.company.hrms.employee.mapper;

import com.company.hrms.employee.entity.EmployeeMobileChangeApplication;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 手机号变更申请 Mapper
 * <p>
 * 对应表 employee_mobile_change_application，管理员工手机号变更申请的 CRUD。
 * 审批通过后同步更新 employee.mobile 和 sys_user.username。
 * </p>
 */
@Mapper
public interface EmployeeMobileChangeApplicationMapper {

    /** 按主键查询 */
    EmployeeMobileChangeApplication selectById(@Param("id") Long id);

    /** 查询员工本人的申请记录 */
    List<EmployeeMobileChangeApplication> selectByEmployeeId(@Param("employeeId") Long employeeId);

    /** 查询全部待审批申请（HR 端） */
    List<EmployeeMobileChangeApplication> selectPending();

    /** 提交新申请 */
    int insert(EmployeeMobileChangeApplication app);

    /** 更新申请状态 */
    int updateById(EmployeeMobileChangeApplication app);
}
