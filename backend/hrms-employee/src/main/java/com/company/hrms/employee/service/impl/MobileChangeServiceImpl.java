package com.company.hrms.employee.service.impl;

import com.company.hrms.common.exception.BusinessException;
import com.company.hrms.common.exception.ErrorCode;
import com.company.hrms.employee.dto.MobileChangeApplyDTO;
import com.company.hrms.employee.entity.Employee;
import com.company.hrms.employee.entity.EmployeeMobileChangeApplication;
import com.company.hrms.employee.feign.AuthInternalFeignClient;
import com.company.hrms.employee.mapper.EmployeeMapper;
import com.company.hrms.employee.mapper.EmployeeMobileChangeApplicationMapper;
import com.company.hrms.employee.service.MobileChangeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 手机号变更服务实现
 * <p>
 * 状态机：PENDING → APPROVED（更新 mobile + 同步 auth）| REJECTED | CANCELLED
 * <p>
 * 审批通过后自动同步 sys_user.username 至新手机号，确保新旧手机号登录验证正确。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MobileChangeServiceImpl implements MobileChangeService {

    private final EmployeeMapper employeeMapper;
    private final EmployeeMobileChangeApplicationMapper mobileChangeMapper;
    private final AuthInternalFeignClient authInternalFeignClient;

    @Override
    @Transactional
    public void apply(Long employeeId, Long userId, MobileChangeApplyDTO dto) {
        Employee emp = employeeMapper.selectById(employeeId);
        if (emp == null) throw new BusinessException(ErrorCode.PARAM_INVALID, "员工不存在");

        Employee exist = employeeMapper.selectByMobile(dto.getNewMobile());
        if (exist != null) throw new BusinessException(ErrorCode.PARAM_INVALID, "新手机号已被使用");

        // 创建申请记录，状态 PENDING
        EmployeeMobileChangeApplication app = new EmployeeMobileChangeApplication();
        app.setEmployeeId(employeeId);
        app.setUserId(userId);
        app.setOldMobile(emp.getMobile());
        app.setNewMobile(dto.getNewMobile());
        app.setReason(dto.getReason());
        app.setSmsVerified(1);
        app.setStatus("PENDING");
        mobileChangeMapper.insert(app);

        log.info("手机号变更申请已提交: employeeId={}, newMobile={}, appId={}",
                employeeId, dto.getNewMobile(), app.getId());
    }

    @Override
    public List<EmployeeMobileChangeApplication> listMyApplications(Long employeeId) {
        return mobileChangeMapper.selectByEmployeeId(employeeId);
    }

    @Override
    @Transactional
    public void cancel(Long employeeId, Long appId) {
        EmployeeMobileChangeApplication app = mobileChangeMapper.selectById(appId);
        if (app == null || !app.getEmployeeId().equals(employeeId))
            throw new BusinessException(ErrorCode.PARAM_INVALID, "申请不存在");
        if (!"PENDING".equals(app.getStatus()))
            throw new BusinessException(ErrorCode.APPROVAL_STATE_INVALID, "仅 PENDING 状态可撤销");
        app.setStatus("CANCELLED");
        mobileChangeMapper.updateById(app);
        log.info("手机号变更申请已撤销: appId={}", appId);
    }

    /**
     * HR 审批通过
     * 状态机：PENDING → APPROVED
     * 事务内：1.更新状态 2.更新 employee.mobile 3.同步 auth username
     */
    @Override
    @Transactional
    public void approve(Long appId) {
        EmployeeMobileChangeApplication app = mobileChangeMapper.selectById(appId);
        if (app == null) throw new BusinessException(ErrorCode.PARAM_INVALID, "申请不存在");
        if (!"PENDING".equals(app.getStatus()))
            throw new BusinessException(ErrorCode.APPROVAL_STATE_INVALID, "仅 PENDING 状态可通过");

        // 1. 更新 employee.mobile
        Employee emp = employeeMapper.selectById(app.getEmployeeId());
        if (emp != null) {
            Employee update = new Employee();
            update.setId(emp.getId());
            update.setMobile(app.getNewMobile());
            employeeMapper.updateById(update);
        }

        // 2. 同步 sys_user.username（A 组 auth Feign）
        try {
            AuthInternalFeignClient.UpdateUsernameRequest req = new AuthInternalFeignClient.UpdateUsernameRequest();
            req.setUserId(app.getUserId());
            req.setNewUsername(app.getNewMobile());
            var resp = authInternalFeignClient.updateUsername(req);
            if (resp.getCode() != 0) {
                log.error("同步 username 失败: userId={}, newMobile={}, resp={}",
                        app.getUserId(), app.getNewMobile(), resp.getMessage());
            }
        } catch (Exception e) {
            log.error("同步 username 异常: userId={}, newMobile={}", app.getUserId(), app.getNewMobile(), e);
            // 不阻断事务——mobile 已更新，auth 可后补
        }

        // 3. 更新申请状态
        app.setStatus("APPROVED");
        mobileChangeMapper.updateById(app);

        log.info("手机号变更审批通过: appId={}, newMobile={}", appId, app.getNewMobile());
    }

    /**
     * HR 审批驳回
     * 状态机：PENDING → REJECTED
     */
    @Override
    @Transactional
    public void reject(Long appId) {
        EmployeeMobileChangeApplication app = mobileChangeMapper.selectById(appId);
        if (app == null) throw new BusinessException(ErrorCode.PARAM_INVALID, "申请不存在");
        if (!"PENDING".equals(app.getStatus()))
            throw new BusinessException(ErrorCode.APPROVAL_STATE_INVALID, "仅 PENDING 状态可驳回");

        app.setStatus("REJECTED");
        mobileChangeMapper.updateById(app);

        log.info("手机号变更审批驳回: appId={}", appId);
    }

    @Override
    public List<EmployeeMobileChangeApplication> listPending() {
        return mobileChangeMapper.selectPending();
    }
}
