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
 * 状态机：PENDING → APPROVED（更新mobile+同步auth）| REJECTED | CANCELLED
 * <p>
 * 当前本地逻辑已完成状态流转+DB操作。
 * ⚠️ 跨模块调用（审批实例创建 + auth同步）以注释桩形式存在，联调时启用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MobileChangeServiceImpl implements MobileChangeService {

    private final EmployeeMapper employeeMapper;
    private final EmployeeMobileChangeApplicationMapper mobileChangeMapper;

    // ⚠️ 联调时启用：下面两个 Feign 依赖 C 组/A 组接口
    // private final ApprovalFeignClient approvalFeignClient;
    // private final AuthInternalFeignClient authInternalFeignClient;

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

        // ⚠️ C 组审批引擎就绪后启用：发起 MOBILE_CHANGE 审批实例
        //   ApprovalFeignClient.StartInstanceRequest req = new ...();
        //   req.setProcessType("MOBILE_CHANGE");
        //   req.setBusinessKey(String.valueOf(app.getId()));
        //   req.setTitle("手机号变更审批");
        //   Result<StartInstanceResponse> resp = approvalFeignClient.startInstance(req);
        //   if (resp.success) { app.setInstanceId(resp.data.getInstanceId()); mobileChangeMapper.updateById(app); }

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

        // 2. ⚠️ 联调时启用：同步 sys_user.username（依赖 A 组 auth 内部 Feign）
        //   AuthInternalFeignClient.UpdateUsernameRequest req = new AuthInternalFeignClient.UpdateUsernameRequest();
        //   req.setUserId(app.getUserId());
        //   req.setNewUsername(app.getNewMobile());
        //   authInternalFeignClient.updateUsername(req);

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
