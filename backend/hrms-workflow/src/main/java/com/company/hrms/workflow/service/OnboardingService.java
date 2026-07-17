package com.company.hrms.workflow.service;

import com.company.hrms.common.exception.BusinessException;
import com.company.hrms.common.exception.ErrorCode;
import com.company.hrms.employee.dto.OnboardingArchiveCommand;
import com.company.hrms.employee.entity.Employee;
import com.company.hrms.employee.mapper.EmployeeMapper;
import com.company.hrms.workflow.client.AuthAccountClient;
import com.company.hrms.workflow.client.EmployeeArchiveClient;
import com.company.hrms.workflow.core.ApprovalStateMachine;
import com.company.hrms.workflow.dto.OnboardingDtos;
import com.company.hrms.workflow.entity.OnboardingApplication;
import com.company.hrms.workflow.enums.ApprovalAction;
import com.company.hrms.workflow.enums.ApprovalStatus;
import com.company.hrms.workflow.mapper.OrgLookupMapper;
import com.company.hrms.workflow.store.WorkflowMemoryStore;
import com.company.hrms.workflow.support.CurrentUserProvider;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service("workflowOnboardingService")
public class OnboardingService {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final BigDecimal DEFAULT_GRADE_MAX = new BigDecimal("20000");

    private final WorkflowMemoryStore store;
    private final ApprovalEngine approvalEngine;
    private final CurrentUserProvider currentUserProvider;
    private final EmployeeArchiveClient employeeArchiveClient;
    private final AuthAccountClient authAccountClient;
    private final EmployeeMapper employeeMapper;
    private final OrgLookupMapper orgLookupMapper;

    public OnboardingService(WorkflowMemoryStore store,
                             ApprovalEngine approvalEngine,
                             CurrentUserProvider currentUserProvider,
                             EmployeeArchiveClient employeeArchiveClient,
                             AuthAccountClient authAccountClient,
                             EmployeeMapper employeeMapper,
                             OrgLookupMapper orgLookupMapper) {
        this.store = store;
        this.approvalEngine = approvalEngine;
        this.currentUserProvider = currentUserProvider;
        this.employeeArchiveClient = employeeArchiveClient;
        this.authAccountClient = authAccountClient;
        this.employeeMapper = employeeMapper;
        this.orgLookupMapper = orgLookupMapper;
    }

    public OnboardingDtos.OnboardingListResponse list(int page, int pageSize, String status) {
        List<OnboardingApplication> filtered = store.listOnboardings().stream()
                .filter(a -> status == null || status.isBlank() || status.equalsIgnoreCase(a.getStatus()))
                .collect(Collectors.toList());
        int p = page <= 0 ? 1 : page;
        int size = pageSize <= 0 ? 20 : pageSize;
        int from = Math.min((p - 1) * size, filtered.size());
        int to = Math.min(from + size, filtered.size());

        OnboardingDtos.OnboardingListResponse resp = new OnboardingDtos.OnboardingListResponse();
        resp.setList(filtered.subList(from, to).stream().map(this::toVo).collect(Collectors.toList()));
        resp.setTotal(filtered.size());
        resp.setPage(p);
        resp.setPageSize(size);
        resp.setStats(stats());
        return resp;
    }

    public OnboardingDtos.OnboardingStatsVO stats() {
        OnboardingDtos.OnboardingStatsVO s = new OnboardingDtos.OnboardingStatsVO();
        for (OnboardingApplication app : store.listOnboardings()) {
            String st = app.getStatus() == null ? "" : app.getStatus().toLowerCase(Locale.ROOT);
            switch (st) {
                case "draft" -> s.setDraft(s.getDraft() + 1);
                case "pending" -> s.setPending(s.getPending() + 1);
                case "approved_pending" -> s.setApprovedPending(s.getApprovedPending() + 1);
                case "onboarded" -> s.setOnboarded(s.getOnboarded() + 1);
                case "rejected" -> s.setRejected(s.getRejected() + 1);
                case "abandoned" -> s.setAbandoned(s.getAbandoned() + 1);
                default -> {
                }
            }
        }
        return s;
    }

    public OnboardingDtos.OnboardingVO create(OnboardingDtos.OnboardingFormRequest req, long userId) {
        validateForm(req, true);
        ensureMobileUnique(req.getMobile(), null);

        LocalDateTime now = LocalDateTime.now();
        OnboardingApplication app = new OnboardingApplication();
        app.setId(store.nextOnboardingId());
        app.setStatus(ApprovalStatus.Onboarding.DRAFT.code());
        applyForm(app, req);
        app.setCreatedBy(userId);
        app.setCreatedAt(now);
        app.setUpdatedAt(now);
        store.saveOnboarding(app);
        return toVo(app);
    }

    public OnboardingDtos.OnboardingVO update(long id, OnboardingDtos.OnboardingFormRequest req) {
        OnboardingApplication app = require(id);
        if (!ApprovalStatus.Onboarding.DRAFT.code().equalsIgnoreCase(app.getStatus())) {
            throw new BusinessException(ErrorCode.APPROVAL_STATE_INVALID, "仅草稿可编辑");
        }
        validateForm(req, false);
        if (req.getMobile() != null) {
            ensureMobileUnique(req.getMobile(), id);
        }
        applyForm(app, req);
        app.setUpdatedAt(LocalDateTime.now());
        store.saveOnboarding(app);
        return toVo(app);
    }

    public void delete(long id) {
        OnboardingApplication app = require(id);
        if (!ApprovalStatus.Onboarding.DRAFT.code().equalsIgnoreCase(app.getStatus())) {
            throw new BusinessException(ErrorCode.APPROVAL_STATE_INVALID, "仅草稿可删除");
        }
        store.removeOnboarding(id);
    }

    public OnboardingDtos.OnboardingVO submit(long id, long userId) {
        OnboardingApplication app = require(id);
        String next;
        try {
            next = ApprovalStateMachine.transit(
                    ApprovalStateMachine.ProcessType.ONBOARDING, app.getStatus(), ApprovalAction.SUBMIT);
        } catch (IllegalStateException | IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.APPROVAL_STATE_INVALID, ex.getMessage());
        }
        app.setStatus(next);

        boolean second = needSecondApproval(app);
        Map<String, Object> variables = new HashMap<>();
        variables.put("needSecondApproval", second);
        variables.put("positionStandard", !second || isPositionStandard(app));
        variables.put("baseSalary", app.getBaseSalary());
        variables.put("gradeMax", resolveGradeMax(app));

        WorkflowMemoryStore.InstanceMeta meta = new WorkflowMemoryStore.InstanceMeta();
        meta.title = app.getName() + "入职审批";
        meta.businessNo = "OA-2026-" + String.format("%03d", app.getId());
        meta.applicantName = currentUserProvider.displayName(userId);
        meta.applicantDept = "人力资源部";
        meta.businessSummary = "部门" + app.getDepartmentId() + "-职位" + app.getPositionId();

        Long instanceId = approvalEngine.createInstance(
                "ONBOARDING",
                "ONBOARDING:" + app.getId(),
                userId,
                variables,
                meta);
        app.setInstanceId(instanceId);
        app.setUpdatedAt(LocalDateTime.now());
        store.saveOnboarding(app);
        return toVo(app);
    }

    public OnboardingDtos.OnboardingVO withdraw(long id, long userId) {
        OnboardingApplication app = require(id);
        if (app.getInstanceId() == null) {
            throw new BusinessException(ErrorCode.APPROVAL_STATE_INVALID, "无可撤回实例");
        }
        approvalEngine.withdrawInstance(app.getInstanceId(), userId);
        // onApprovalWithdrawn 回调会改状态；若未触发则本地兜底
        if (!ApprovalStatus.Onboarding.DRAFT.code().equalsIgnoreCase(app.getStatus())) {
            String next = ApprovalStateMachine.transit(
                    ApprovalStateMachine.ProcessType.ONBOARDING, app.getStatus(), ApprovalAction.WITHDRAW);
            app.setStatus(next);
            app.setUpdatedAt(LocalDateTime.now());
            store.saveOnboarding(app);
        }
        return toVo(require(id));
    }

    public OnboardingDtos.OnboardingVO confirm(long id) {
        OnboardingApplication app = require(id);
        if (!ApprovalStatus.Onboarding.APPROVED_PENDING.code().equalsIgnoreCase(app.getStatus())) {
            throw new BusinessException(ErrorCode.APPROVAL_STATE_INVALID, "仅「审批通过待入职」可确认入职");
        }
        LocalDate actual = LocalDate.now();
        OnboardingArchiveCommand cmd = new OnboardingArchiveCommand();
        cmd.setApplicationId(app.getId());
        cmd.setActualOnboardDate(actual);
        cmd.setName(app.getName());
        cmd.setGender(app.getGender());
        cmd.setMobile(app.getMobile());
        cmd.setEmail(app.getEmail());
        cmd.setIdNumber(app.getIdNumberEnc());
        cmd.setDepartmentId(app.getDepartmentId());
        cmd.setPositionId(app.getPositionId());
        cmd.setEmploymentType(app.getEmploymentType());
        cmd.setProbationMonths(app.getProbationMonths());
        cmd.setProbationSalaryRatio(app.getProbationSalaryRatio());
        cmd.setBaseSalary(app.getBaseSalary());
        cmd.setManagerId(app.getManagerId());
        cmd.setExpectedOnboardDate(app.getExpectedOnboardDate());

        Long employeeId = employeeArchiveClient.archive(cmd);
        // A 组建号 Feign：Mock 返回假 userId；真实模式由建档内 InternalUserService 完成，此处对齐调用点
        authAccountClient.createAccount(new AuthAccountClient.CreateAccountRequest(
                app.getMobile(), employeeId, app.getName()));

        try {
            app.setStatus(ApprovalStateMachine.transit(
                    ApprovalStateMachine.ProcessType.ONBOARDING, app.getStatus(), ApprovalAction.APPROVE));
        } catch (IllegalStateException | IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.APPROVAL_STATE_INVALID, ex.getMessage());
        }
        app.setActualOnboardDate(actual);
        app.setEmployeeId(employeeId);
        app.setUpdatedAt(LocalDateTime.now());
        store.saveOnboarding(app);
        return toVo(app);
    }

    public OnboardingDtos.OnboardingVO abandon(long id) {
        OnboardingApplication app = require(id);
        String next;
        try {
            next = ApprovalStateMachine.transit(
                    ApprovalStateMachine.ProcessType.ONBOARDING, app.getStatus(), ApprovalAction.ABANDON);
        } catch (IllegalStateException | IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.APPROVAL_STATE_INVALID, "当前状态不可放弃: " + app.getStatus());
        }
        app.setStatus(next);
        app.setUpdatedAt(LocalDateTime.now());
        store.saveOnboarding(app);
        return toVo(app);
    }

    public void onApprovalFinished(String businessKey, boolean approved, String comment) {
        Long id = parseBusinessId(businessKey);
        if (id == null) {
            return;
        }
        OnboardingApplication app = store.findOnboarding(id).orElse(null);
        if (app == null || !ApprovalStatus.Onboarding.PENDING.code().equalsIgnoreCase(app.getStatus())) {
            return;
        }
        if (approved) {
            try {
                app.setStatus(ApprovalStateMachine.transit(
                        ApprovalStateMachine.ProcessType.ONBOARDING, app.getStatus(), ApprovalAction.APPROVE));
            } catch (IllegalStateException | IllegalArgumentException ex) {
                throw new BusinessException(ErrorCode.APPROVAL_STATE_INVALID, ex.getMessage());
            }
        } else {
            try {
                app.setStatus(ApprovalStateMachine.transit(
                        ApprovalStateMachine.ProcessType.ONBOARDING, app.getStatus(), ApprovalAction.REJECT));
            } catch (IllegalStateException | IllegalArgumentException ex) {
                throw new BusinessException(ErrorCode.APPROVAL_STATE_INVALID, ex.getMessage());
            }
        }
        app.setUpdatedAt(LocalDateTime.now());
        store.saveOnboarding(app);
    }

    public void onApprovalWithdrawn(String businessKey) {
        Long id = parseBusinessId(businessKey);
        if (id == null) {
            return;
        }
        OnboardingApplication app = store.findOnboarding(id).orElse(null);
        if (app == null) {
            return;
        }
        if (ApprovalStatus.Onboarding.PENDING.code().equalsIgnoreCase(app.getStatus())) {
            String next = ApprovalStateMachine.transit(
                    ApprovalStateMachine.ProcessType.ONBOARDING, app.getStatus(), ApprovalAction.WITHDRAW);
            app.setStatus(next);
            app.setUpdatedAt(LocalDateTime.now());
            store.saveOnboarding(app);
        }
    }

    /**
     * 非标准职位或薪资超职级 → 需要二审。
     */
    public boolean needSecondApproval(OnboardingApplication app) {
        if (app == null) {
            return false;
        }
        if (!isPositionStandard(app)) {
            return true;
        }
        BigDecimal salary = app.getBaseSalary() == null ? BigDecimal.ZERO : app.getBaseSalary();
        return salary.compareTo(resolveGradeMax(app)) > 0;
    }

    private boolean isPositionStandard(OnboardingApplication app) {
        // 内存桩：职位 ID 以 9 开头视为非标准；其余标准
        return app.getPositionId() == null || app.getPositionId() < 9000;
    }

    private BigDecimal resolveGradeMax(OnboardingApplication app) {
        // 可从请求扩展字段写入；Day2 固定默认上限
        return DEFAULT_GRADE_MAX;
    }

    private OnboardingApplication require(long id) {
        return store.findOnboarding(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "入职申请不存在"));
    }

    private void ensureMobileUnique(String mobile, Long excludeId) {
        if (store.existsMobile(mobile, excludeId)) {
            throw new BusinessException(ErrorCode.MOBILE_DUPLICATE);
        }
        Employee existing = employeeMapper.selectByMobile(mobile);
        if (existing != null && (existing.getDeleted() == null || existing.getDeleted() == 0)) {
            throw new BusinessException(ErrorCode.MOBILE_DUPLICATE);
        }
    }

    private void ensureOrgExists(Long departmentId, Long positionId) {
        if (departmentId != null && orgLookupMapper.countDepartment(departmentId) <= 0) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "部门不存在");
        }
        Long lookupPositionId = positionId;
        if (lookupPositionId != null && lookupPositionId >= 9000) {
            lookupPositionId = lookupPositionId - 9000;
        }
        if (lookupPositionId != null && orgLookupMapper.countPosition(lookupPositionId) <= 0) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "职位不存在");
        }
    }

    private void validateForm(OnboardingDtos.OnboardingFormRequest req, boolean creating) {
        if (req == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "请求体不能为空");
        }
        if (creating) {
            requireText(req.getName(), "name");
            requireText(req.getMobile(), "mobile");
            requireText(req.getGender(), "gender");
            requireText(req.getEmail(), "email");
            requireText(req.getIdNumber(), "idNumber");
            if (req.getExpectedOnboardDate() == null) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "expectedOnboardDate 必填");
            }
            if (req.getDepartmentId() == null || req.getPositionId() == null) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "departmentId/positionId 必填");
            }
            if (req.getBaseSalary() == null) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "baseSalary 必填");
            }
            ensureOrgExists(req.getDepartmentId(), req.getPositionId());
        } else if (req.getDepartmentId() != null || req.getPositionId() != null) {
            ensureOrgExists(req.getDepartmentId(), req.getPositionId());
        }
        if (req.getMobile() != null && !req.getMobile().matches("^1\\d{10}$")) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "手机号格式不正确");
        }
        if (req.getExpectedOnboardDate() != null && req.getExpectedOnboardDate().isBefore(LocalDate.now())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "预计入职日不能早于今天");
        }
    }

    private void requireText(String v, String field) {
        if (v == null || v.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, field + " 必填");
        }
    }

    private void applyForm(OnboardingApplication app, OnboardingDtos.OnboardingFormRequest req) {
        if (req.getName() != null) {
            app.setName(req.getName());
        }
        if (req.getGender() != null) {
            app.setGender(req.getGender());
        }
        if (req.getMobile() != null) {
            app.setMobile(req.getMobile());
        }
        if (req.getEmail() != null) {
            app.setEmail(req.getEmail());
        }
        if (req.getIdNumber() != null) {
            app.setIdNumberEnc(req.getIdNumber());
            app.setIdNumberHash(Integer.toHexString(req.getIdNumber().hashCode()));
        }
        if (req.getExpectedOnboardDate() != null) {
            app.setExpectedOnboardDate(req.getExpectedOnboardDate());
        }
        if (req.getDepartmentId() != null) {
            app.setDepartmentId(req.getDepartmentId());
        }
        if (req.getPositionId() != null) {
            app.setPositionId(req.getPositionId());
        }
        if (req.getEmploymentType() != null) {
            app.setEmploymentType(req.getEmploymentType());
        }
        if (req.getProbationMonths() != null) {
            app.setProbationMonths(req.getProbationMonths());
        }
        if (req.getProbationSalaryRatio() != null) {
            app.setProbationSalaryRatio(req.getProbationSalaryRatio());
        }
        if (req.getManagerId() != null) {
            app.setManagerId(req.getManagerId());
        }
        if (req.getBaseSalary() != null) {
            app.setBaseSalary(req.getBaseSalary());
        }
        // positionStandard=false → 用非标准职位 ID 标记
        if (Boolean.FALSE.equals(req.getPositionStandard()) && app.getPositionId() != null && app.getPositionId() < 9000) {
            app.setPositionId(9000L + app.getPositionId());
        }
    }

    private OnboardingDtos.OnboardingVO toVo(OnboardingApplication app) {
        OnboardingDtos.OnboardingVO vo = new OnboardingDtos.OnboardingVO();
        vo.setId(app.getId());
        vo.setInstanceId(app.getInstanceId());
        vo.setStatus(app.getStatus());
        vo.setName(app.getName());
        vo.setGender(app.getGender());
        vo.setMobile(app.getMobile());
        vo.setEmail(app.getEmail());
        vo.setIdNumber(app.getIdNumberEnc());
        vo.setExpectedOnboardDate(app.getExpectedOnboardDate());
        vo.setDepartmentId(app.getDepartmentId());
        vo.setPositionId(app.getPositionId());
        vo.setEmploymentType(app.getEmploymentType());
        vo.setProbationMonths(app.getProbationMonths());
        vo.setProbationSalaryRatio(app.getProbationSalaryRatio());
        vo.setManagerId(app.getManagerId());
        vo.setBaseSalary(app.getBaseSalary());
        vo.setActualOnboardDate(app.getActualOnboardDate());
        vo.setEmployeeId(app.getEmployeeId());
        vo.setCreatedBy(app.getCreatedBy());
        vo.setCreatedAt(app.getCreatedAt() == null ? null : DT.format(app.getCreatedAt()));
        vo.setUpdatedAt(app.getUpdatedAt() == null ? null : DT.format(app.getUpdatedAt()));
        return vo;
    }

    private Long parseBusinessId(String businessKey) {
        if (businessKey == null || !businessKey.startsWith("ONBOARDING:")) {
            return null;
        }
        try {
            return Long.parseLong(businessKey.substring("ONBOARDING:".length()));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
