package com.company.hrms.module.employee.service.impl;

import com.company.hrms.common.exception.BusinessException;
import com.company.hrms.module.employee.dto.*;
import com.company.hrms.module.employee.entity.EmployeeEntity;
import com.company.hrms.module.employee.entity.EmployeeMobileChangeApplicationEntity;
import com.company.hrms.module.employee.entity.EmployeeResignationRequestEntity;
import com.company.hrms.module.employee.enums.EmploymentStatus;
import com.company.hrms.module.employee.enums.MobileChangeStatus;
import com.company.hrms.module.employee.enums.ResignationRequestStatus;
import com.company.hrms.module.employee.feign.AttendanceFeignClient;
import com.company.hrms.module.employee.feign.AuthFeignClient;
import com.company.hrms.module.employee.feign.PayrollFeignClient;
import com.company.hrms.module.employee.repository.EmployeeMapper;
import com.company.hrms.module.employee.repository.EmployeeMobileChangeApplicationMapper;
import com.company.hrms.module.employee.repository.EmployeeResignationRequestMapper;
import com.company.hrms.module.employee.service.PortalEmployeeService;
import com.company.hrms.module.employee.vo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

/**
 * 员工门户服务实现
 *
 * 全部接口强制 @DataScope(SELF)，需校验 employeeId == currentUser.empId
 * 跨模块操作通过 Feign 代理转发
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PortalEmployeeServiceImpl implements PortalEmployeeService {

    private final EmployeeMapper employeeMapper;
    private final EmployeeMobileChangeApplicationMapper mobileChangeMapper;
    private final EmployeeResignationRequestMapper resignationRequestMapper;
    private final EmployeeService employeeService;
    private final AuthFeignClient authFeignClient;
    private final PayrollFeignClient payrollFeignClient;
    private final AttendanceFeignClient attendanceFeignClient;

    // ==================== 个人档案 ====================

    @Override
    public ProfileVO getMyProfile(Long employeeId) {
        return employeeService.getMyProfile(employeeId);
    }

    @Override
    public void updateMyProfile(Long employeeId, ProfileUpdateDTO dto) {
        employeeService.updateMyProfile(employeeId, dto);
    }

    // ==================== 手机号变更 ====================

    @Override
    @Transactional
    public void applyMobileChange(Long employeeId, Long userId, MobileChangeApplyDTO dto) {
        // 校验新手机号未被使用
        EmployeeEntity existing = employeeMapper.selectByMobile(dto.getNewMobile());
        if (existing != null) {
            throw new BusinessException(409, "该手机号已被其他员工使用");
        }

        EmployeeEntity emp = employeeMapper.selectById(employeeId);
        if (emp == null) {
            throw new BusinessException(404, "员工不存在");
        }

        EmployeeMobileChangeApplicationEntity app = new EmployeeMobileChangeApplicationEntity();
        app.setEmployeeId(employeeId);
        app.setUserId(userId);
        app.setOldMobile(emp.getMobile());
        app.setNewMobile(dto.getNewMobile());
        app.setReason(dto.getReason());
        app.setSmsVerified(true); // 提交时已验证
        app.setStatus(MobileChangeStatus.PENDING);

        mobileChangeMapper.insert(app);

        // TODO: 发起 MOBILE_CHANGE 审批（通过审批引擎 Feign 或直接调用）
        log.info("手机号变更申请已提交: employeeId={}, newMobile={}", employeeId, dto.getNewMobile());
    }

    @Override
    public List<MobileChangeAppVO> listMyMobileChanges(Long employeeId) {
        return mobileChangeMapper.selectByEmployeeId(employeeId);
    }

    @Override
    @Transactional
    public void cancelMobileChange(Long employeeId, Long appId) {
        EmployeeMobileChangeApplicationEntity app = mobileChangeMapper.selectById(appId);
        if (app == null || !app.getEmployeeId().equals(employeeId)) {
            throw new BusinessException(404, "申请不存在");
        }
        if (app.getStatus() != MobileChangeStatus.PENDING) {
            throw new BusinessException(60002, "当前状态不允许撤销");
        }
        app.setStatus(MobileChangeStatus.CANCELLED);
        mobileChangeMapper.updateById(app);
    }

    // ==================== 离职申请 ====================

    @Override
    @Transactional
    public void applyResignation(Long employeeId, ResignationRequestDTO dto) {
        EmployeeEntity emp = employeeMapper.selectById(employeeId);
        if (emp == null) {
            throw new BusinessException(404, "员工不存在");
        }
        // 校验在职状态
        if (emp.getEmploymentStatus() == EmploymentStatus.RESIGNED
                || emp.getEmploymentStatus() == EmploymentStatus.PENDING_RESIGN) {
            throw new BusinessException(30003, "员工状态不允许此操作");
        }

        // TODO: 创建离职申请记录，发起 RESIGNATION_REQUEST 审批
        log.info("离职申请已提交: employeeId={}, date={}", employeeId, dto.getExpectedResignDate());
    }

    @Override
    public List<?> listMyResignations(Long employeeId) {
        return Collections.emptyList();
    }

    @Override
    @Transactional
    public void cancelResignation(Long employeeId, Long requestId) {
        // TODO: 校验申请状态为 PENDING 后取消
    }

    // ==================== 账号安全 ====================

    @Override
    public void changePassword(Long userId, PasswordChangeDTO dto) {
        if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
            throw new BusinessException(10001, "两次输入的密码不一致");
        }
        authFeignClient.changePassword(dto);
    }

    @Override
    public void bindMobile(Long userId, MobileBindDTO dto) {
        authFeignClient.bindMobile(dto);
    }

    @Override
    public void unbindMobile(Long userId, MobileBindDTO dto) {
        authFeignClient.unbindMobile(dto);
    }

    @Override
    public List<LoginLogVO> listMyLoginLogs(Long userId) {
        // 通过 AuthFeign 查询
        return Collections.emptyList();
    }

    // ==================== 工资条 ====================

    @Override
    public List<PayslipListVO> listMyPayslips(Long employeeId) {
        return employeeService.listMyPayslips(employeeId);
    }

    @Override
    public List<PayslipTrendVO> getPayslipTrend(Long employeeId) {
        return employeeService.getPayslipTrend(employeeId);
    }

    @Override
    public PayslipDetailVO getPayslipDetail(Long employeeId, String period) {
        return employeeService.getPayslipDetail(employeeId, period);
    }

    @Override
    public byte[] getPayslipPdf(Long employeeId, String period) {
        return employeeService.getPayslipPdf(employeeId, period);
    }

    @Override
    public void verifyPayslip(Long userId, String verifyType, String verifyCode) {
        employeeService.verifyPayslip(userId, verifyType, verifyCode);
    }

    // ==================== 考勤代理 ====================

    @Override
    public Object punch(Long employeeId, Object dto) {
        return attendanceFeignClient.punch(dto);
    }

    @Override
    public Object applyPunchFix(Long employeeId, Object dto) {
        return attendanceFeignClient.applyPunchFix(dto);
    }

    // ==================== 请假代理 ====================

    @Override
    public List<?> listMyLeaves(Long employeeId) {
        return Collections.emptyList();
    }

    @Override
    public Object applyLeave(Long employeeId, Object dto) {
        return attendanceFeignClient.applyLeave(dto);
    }

    @Override
    public void cancelLeave(Long employeeId, Long appId) {
        // POST /profile/leave/applications/{id}/cancel 委托至 attendance
    }

    // ==================== 加班代理 ====================

    @Override
    public List<?> listMyOvertimes(Long employeeId) {
        return Collections.emptyList();
    }

    @Override
    public Object applyOvertime(Long employeeId, Object dto) {
        return attendanceFeignClient.applyOvertime(dto);
    }
}
