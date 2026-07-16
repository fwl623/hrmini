package com.company.hrms.employee.service.impl;

import com.company.hrms.common.exception.BusinessException;
import com.company.hrms.common.exception.ErrorCode;
import com.company.hrms.employee.dto.MobileChangeApplyDTO;
import com.company.hrms.employee.entity.Employee;
import com.company.hrms.employee.entity.EmployeeMobileChangeApplication;
import com.company.hrms.employee.feign.ApprovalFeignClient;
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
 *
 * ⚠️ 审批发起 → 依赖 C 组 (郭策) 的 ApprovalFeignClient（联调时对接）
 * ⚠️ 审批通过同步 → 依赖 A 组 (李俊毅) 的 AuthInternalFeignClient（联调时对接）
 * 当前为桩实现，审批实例 ID 暂为 null，联调时替换真实 Feign 调用
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MobileChangeServiceImpl implements MobileChangeService {

    private final EmployeeMapper employeeMapper;
    private final EmployeeMobileChangeApplicationMapper mobileChangeMapper;

    // ⚠️ 以下 Feign 依赖 C 组/A 组接口，当前暂未注入，联调时启用
    // private final ApprovalFeignClient approvalFeignClient;
    // private final AuthInternalFeignClient authInternalFeignClient;

    @Override
    @Transactional
    public void apply(Long employeeId, Long userId, MobileChangeApplyDTO dto) {
        // 校验员工存在
        Employee emp = employeeMapper.selectById(employeeId);
        if (emp == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "员工不存在");
        }

        // 校验新手机号唯一
        Employee exist = employeeMapper.selectByMobile(dto.getNewMobile());
        if (exist != null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "新手机号已被其他员工使用");
        }

        // 创建申请记录
        EmployeeMobileChangeApplication app = new EmployeeMobileChangeApplication();
        app.setEmployeeId(employeeId);
        app.setUserId(userId);
        app.setOldMobile(emp.getMobile());
        app.setNewMobile(dto.getNewMobile());
        app.setReason(dto.getReason());
        app.setSmsVerified(1);
        app.setStatus("PENDING");
        mobileChangeMapper.insert(app);

        // 发起 MOBILE_CHANGE 审批
        // ⚠️ 依赖 C 组审批引擎 — 联调时启用以下代码
        // try {
        //     ApprovalFeignClient.StartInstanceRequest req = new ApprovalFeignClient.StartInstanceRequest();
        //     req.setProcessType("MOBILE_CHANGE");
        //     req.setBusinessKey(String.valueOf(app.getId()));
        //     req.setInitiatorId(userId);
        //     req.setTitle("手机号变更审批");
        //     req.setBusinessSummary(emp.getName() + "(" + emp.getEmployeeNo() + ") 申请变更手机号");
        //     Result<ApprovalFeignClient.StartInstanceResponse> resp = approvalFeignClient.startInstance(req);
        //     if (resp.getCode() == 0 && resp.getData() != null) {
        //         app.setInstanceId(resp.getData().getInstanceId());
        //         mobileChangeMapper.updateById(app);
        //     }
        // } catch (Exception e) {
        //     log.error("发起MOBILE_CHANGE审批失败: appId={}", app.getId(), e);
        //     // 审批发起失败不影响申请记录创建，审批实例ID后补
        // }

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
        if (app == null || !app.getEmployeeId().equals(employeeId)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "申请不存在");
        }
        if (!"PENDING".equals(app.getStatus())) {
            throw new BusinessException(ErrorCode.APPROVAL_STATE_INVALID, "当前状态不允许撤销");
        }
        app.setStatus("CANCELLED");
        mobileChangeMapper.updateById(app);
        log.info("手机号变更申请已撤销: appId={}", appId);
    }

    @Override
    @Transactional
    public void onApproved(Long appId) {
        // 审批通过回调
        // ⚠️ 由 C 组审批引擎在审批通过后调用，或通过 MQ 消费
        // 当前为手动触发桩实现

        EmployeeMobileChangeApplication app = mobileChangeMapper.selectById(appId);
        if (app == null) {
            log.warn("手机号变更申请不存在: appId={}", appId);
            return;
        }

        // 1. 更新 employee.mobile
        Employee emp = employeeMapper.selectById(app.getEmployeeId());
        if (emp != null) {
            Employee update = new Employee();
            update.setId(emp.getId());
            update.setMobile(app.getNewMobile());
            employeeMapper.updateById(update);
        }

        // 2. 同步 sys_user.username
        // ⚠️ 依赖 A 组 auth 内部 Feign — 联调时启用以下代码
        // try {
        //     AuthInternalFeignClient.UpdateUsernameRequest req = new AuthInternalFeignClient.UpdateUsernameRequest();
        //     req.setUserId(app.getUserId());
        //     req.setNewUsername(app.getNewMobile());
        //     authInternalFeignClient.updateUsername(req);
        // } catch (Exception e) {
        //     log.error("同步username失败: userId={}, newMobile={}", app.getUserId(), app.getNewMobile(), e);
        // }

        // 3. 更新申请状态
        app.setStatus("APPROVED");
        mobileChangeMapper.updateById(app);

        log.info("手机号变更审批通过: appId={}, newMobile={}", appId, app.getNewMobile());
    }
}
