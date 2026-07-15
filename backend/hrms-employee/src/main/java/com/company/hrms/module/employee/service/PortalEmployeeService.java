package com.company.hrms.module.employee.service;

import com.company.hrms.module.employee.dto.*;
import com.company.hrms.module.employee.vo.*;

import java.util.List;

/**
 * 员工门户服务接口
 * 全部接口强制 @DataScope(SELF)
 */
public interface PortalEmployeeService {

    // ===== 个人档案 =====
    ProfileVO getMyProfile(Long employeeId);
    void updateMyProfile(Long employeeId, ProfileUpdateDTO dto);

    // ===== 手机号变更 =====
    void applyMobileChange(Long employeeId, Long userId, MobileChangeApplyDTO dto);
    List<MobileChangeAppVO> listMyMobileChanges(Long employeeId);
    void cancelMobileChange(Long employeeId, Long appId);

    // ===== 离职申请 =====
    void applyResignation(Long employeeId, ResignationRequestDTO dto);
    List<?> listMyResignations(Long employeeId);
    void cancelResignation(Long employeeId, Long requestId);

    // ===== 账号安全 =====
    void changePassword(Long userId, PasswordChangeDTO dto);
    void bindMobile(Long userId, MobileBindDTO dto);
    void unbindMobile(Long userId, MobileBindDTO dto);
    List<LoginLogVO> listMyLoginLogs(Long userId);

    // ===== 工资条 =====
    List<PayslipListVO> listMyPayslips(Long employeeId);
    List<PayslipTrendVO> getPayslipTrend(Long employeeId);
    PayslipDetailVO getPayslipDetail(Long employeeId, String period);
    byte[] getPayslipPdf(Long employeeId, String period);
    void verifyPayslip(Long userId, String verifyType, String verifyCode);

    // ===== 考勤代理 =====
    Object punch(Long employeeId, Object dto);
    Object applyPunchFix(Long employeeId, Object dto);

    // ===== 请假代理 =====
    List<?> listMyLeaves(Long employeeId);
    Object applyLeave(Long employeeId, Object dto);
    void cancelLeave(Long employeeId, Long appId);

    // ===== 加班代理 =====
    List<?> listMyOvertimes(Long employeeId);
    Object applyOvertime(Long employeeId, Object dto);
}
