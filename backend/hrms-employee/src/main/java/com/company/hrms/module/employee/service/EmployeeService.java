package com.company.hrms.module.employee.service;

import com.company.hrms.module.employee.dto.EmployeePageQuery;
import com.company.hrms.module.employee.dto.EmployeeUpdateDTO;
import com.company.hrms.module.employee.dto.ProfileUpdateDTO;
import com.company.hrms.module.employee.dto.SalaryProfileUpdateDTO;
import com.company.hrms.module.employee.vo.*;
import com.company.hrms.common.dto.PageResult;

import java.util.List;

/**
 * 员工档案服务接口
 */
public interface EmployeeService {

    /**
     * 花名册分页搜索
     */
    PageResult<EmployeeListVO> pageSearch(EmployeePageQuery query);

    /**
     * 查询员工详情
     */
    EmployeeDetailVO getDetail(Long employeeId);

    /**
     * 编辑员工白名单字段（管理端）
     */
    void update(Long employeeId, EmployeeUpdateDTO dto);

    /**
     * 查询薪资档案
     */
    SalaryProfileVO getSalaryProfile(Long employeeId);

    /**
     * 更新薪资档案
     */
    void updateSalaryProfile(Long employeeId, SalaryProfileUpdateDTO dto);

    /**
     * 查看敏感字段（二次验证后）
     */
    EmployeeSensitiveFieldVO getSensitiveField(Long employeeId, String field);

    /**
     * HR端查询手机号变更待办列表
     */
    List<MobileChangeAppVO> listMobileChangeApplications();

    /**
     * 查询员工调岗历史
     */
    List<EmployeeTransferHistoryVO> getTransferHistory(Long employeeId);

    // ===== 门户接口 =====

    /**
     * 查询本人档案（脱敏）
     */
    ProfileVO getMyProfile(Long employeeId);

    /**
     * 编辑本人档案（白名单）
     */
    void updateMyProfile(Long employeeId, ProfileUpdateDTO dto);

    /**
     * 查询本人工资条列表
     */
    List<PayslipListVO> listMyPayslips(Long employeeId);

    /**
     * 近6月实发趋势
     */
    List<PayslipTrendVO> getPayslipTrend(Long employeeId);

    /**
     * 工资条详情
     */
    PayslipDetailVO getPayslipDetail(Long employeeId, String period);

    /**
     * 工资条 PDF 下载
     */
    byte[] getPayslipPdf(Long employeeId, String period);

    /**
     * 工资条二次验证
     */
    void verifyPayslip(Long userId, String verifyType, String verifyCode);
}
