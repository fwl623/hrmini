package com.company.hrms.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.hrms.common.exception.BusinessException;
import com.company.hrms.common.exception.ErrorCode;
import com.company.hrms.common.security.LoginUser;
import com.company.hrms.common.security.SecurityUtils;
import com.company.hrms.employee.dto.OnboardingArchiveCommand;
import com.company.hrms.employee.entity.Employee;
import com.company.hrms.employee.mapper.EmployeeMapper;
import com.company.hrms.employee.service.EmployeeLifecycleService;
import com.company.hrms.workflow.client.AuthAccountClient;
import com.company.hrms.workflow.client.EmployeeArchiveClient;
import com.company.hrms.workflow.core.ApprovalStateMachine;
import com.company.hrms.workflow.dto.OnboardingDtos;
import com.company.hrms.workflow.entity.ApprovalLog;
import com.company.hrms.workflow.entity.OnboardingApplication;
import com.company.hrms.workflow.enums.ApprovalAction;
import com.company.hrms.workflow.enums.ApprovalStatus;
import com.company.hrms.workflow.mapper.ApprovalLogMapper;
import com.company.hrms.workflow.mapper.OnboardingApplicationMapper;
import com.company.hrms.workflow.mapper.OrgLookupMapper;
import com.company.hrms.workflow.model.ProcessNodeDef;
import com.company.hrms.workflow.notify.ApprovalNotifyPublisher;
import com.company.hrms.workflow.support.CurrentUserProvider;
import com.company.hrms.workflow.support.GradeSalaryCap;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service("workflowOnboardingService")
public class OnboardingService {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final OnboardingApplicationMapper mapper;
    private final DbApprovalService dbApprovalService;
    private final CurrentUserProvider currentUserProvider;
    private final EmployeeArchiveClient employeeArchiveClient;
    private final AuthAccountClient authAccountClient;
    private final EmployeeMapper employeeMapper;
    private final OrgLookupMapper orgLookupMapper;
    private final EmployeeLifecycleService employeeLifecycleService;
    private final ApprovalLogMapper approvalLogMapper;
    private final ApprovalNotifyPublisher notifyPublisher;

    public OnboardingService(OnboardingApplicationMapper mapper,
                             @Lazy DbApprovalService dbApprovalService,
                             CurrentUserProvider currentUserProvider,
                             EmployeeArchiveClient employeeArchiveClient,
                             AuthAccountClient authAccountClient,
                             EmployeeMapper employeeMapper,
                             OrgLookupMapper orgLookupMapper,
                             EmployeeLifecycleService employeeLifecycleService,
                             ApprovalLogMapper approvalLogMapper,
                             ApprovalNotifyPublisher notifyPublisher) {
        this.mapper = mapper;
        this.dbApprovalService = dbApprovalService;
        this.currentUserProvider = currentUserProvider;
        this.employeeArchiveClient = employeeArchiveClient;
        this.authAccountClient = authAccountClient;
        this.employeeMapper = employeeMapper;
        this.orgLookupMapper = orgLookupMapper;
        this.employeeLifecycleService = employeeLifecycleService;
        this.approvalLogMapper = approvalLogMapper;
        this.notifyPublisher = notifyPublisher;
    }

    public OnboardingDtos.OnboardingListResponse list(int page, int pageSize, String status) {
        requireHrOrAdmin();
        LambdaQueryWrapper<OnboardingApplication> q = new LambdaQueryWrapper<OnboardingApplication>()
                .orderByDesc(OnboardingApplication::getId);
        if (status != null && !status.isBlank()) {
            q.eq(OnboardingApplication::getStatus, status);
        }
        applyVisibilityFilter(q);
        List<OnboardingApplication> filtered = mapper.selectList(q);
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

    public OnboardingDtos.OnboardingVO detail(long id) {
        OnboardingApplication app = require(id);
        assertCanView(app);
        return toVo(app);
    }

    public OnboardingDtos.OnboardingStatsVO stats() {
        requireHrOrAdmin();
        LambdaQueryWrapper<OnboardingApplication> q = new LambdaQueryWrapper<>();
        applyVisibilityFilter(q);
        OnboardingDtos.OnboardingStatsVO s = new OnboardingDtos.OnboardingStatsVO();
        for (OnboardingApplication app : mapper.selectList(q)) {
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

    @Transactional
    public OnboardingDtos.OnboardingVO create(OnboardingDtos.OnboardingFormRequest req, long userId) {
        requireHrOrAdmin();
        validateForm(req, true);
        ensureMobileUnique(req.getMobile(), null);
        fillDefaults(req);

        LocalDateTime now = LocalDateTime.now();
        OnboardingApplication app = new OnboardingApplication();
        app.setStatus(ApprovalStatus.Onboarding.DRAFT.code());
        applyForm(app, req);
        app.setCreatedBy(userId);
        app.setCreatedAt(now);
        app.setUpdatedAt(now);
        mapper.insert(app);
        return toVo(app);
    }

    @Transactional
    public OnboardingDtos.OnboardingVO update(long id, OnboardingDtos.OnboardingFormRequest req) {
        OnboardingApplication app = require(id);
        String status = app.getStatus() == null ? "" : app.getStatus().toLowerCase(Locale.ROOT);

        if (ApprovalStatus.Onboarding.APPROVED_PENDING.code().equalsIgnoreCase(status)) {
            assertCanManageApprovedPending(app);
            if (req == null || req.getExpectedOnboardDate() == null) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "待入职仅可修改预计入职日");
            }
            if (req.getExpectedOnboardDate().isBefore(LocalDate.now())) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "预计入职日不能早于今天");
            }
            app.setExpectedOnboardDate(req.getExpectedOnboardDate());
            app.setUpdatedAt(LocalDateTime.now());
            mapper.updateById(app);
            return toVo(app);
        }

        assertCanMutate(app);
        if (!ApprovalStatus.Onboarding.DRAFT.code().equalsIgnoreCase(status)
                && !ApprovalStatus.Onboarding.REJECTED.code().equalsIgnoreCase(status)) {
            throw new BusinessException(ErrorCode.APPROVAL_STATE_INVALID, "仅草稿或已驳回可编辑");
        }
        validateForm(req, false);
        if (req.getMobile() != null) {
            ensureMobileUnique(req.getMobile(), id);
        }
        fillDefaults(req);
        applyForm(app, req);
        app.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(app);
        return toVo(app);
    }

    @Transactional
    public void delete(long id) {
        OnboardingApplication app = require(id);
        assertCanMutate(app);
        String status = app.getStatus() == null ? "" : app.getStatus().toLowerCase(Locale.ROOT);
        if (!ApprovalStatus.Onboarding.DRAFT.code().equalsIgnoreCase(status)
                && !ApprovalStatus.Onboarding.REJECTED.code().equalsIgnoreCase(status)) {
            throw new BusinessException(ErrorCode.APPROVAL_STATE_INVALID, "仅草稿或已驳回可删除");
        }
        mapper.deleteById(id);
    }

    @Transactional
    public OnboardingDtos.OnboardingVO submit(long id, long userId) {
        OnboardingApplication app = require(id);
        assertCanMutate(app);
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
        variables.put("departmentId", app.getDepartmentId());

        Long instanceId = dbApprovalService.createInstance(
                "ONBOARDING",
                String.valueOf(app.getId()),
                userId,
                buildOnboardingNodes(app, userId, second),
                DbApprovalService.InstanceDisplay.of(
                        app.getName() + "入职审批",
                        currentUserProvider.displayName(userId),
                        "人力资源部",
                        "OA-" + app.getId()),
                variables);
        app.setInstanceId(instanceId);
        app.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(app);
        return toVo(app);
    }

    @Transactional
    public OnboardingDtos.OnboardingVO withdraw(long id, long userId) {
        OnboardingApplication app = require(id);
        assertCanMutate(app);
        if (app.getInstanceId() == null) {
            throw new BusinessException(ErrorCode.APPROVAL_STATE_INVALID, "无可撤回实例");
        }
        dbApprovalService.withdrawInstance(app.getInstanceId(), userId);
        if (!ApprovalStatus.Onboarding.DRAFT.code().equalsIgnoreCase(app.getStatus())) {
            String next = ApprovalStateMachine.transit(
                    ApprovalStateMachine.ProcessType.ONBOARDING, app.getStatus(), ApprovalAction.WITHDRAW);
            app.setStatus(next);
            app.setUpdatedAt(LocalDateTime.now());
            mapper.updateById(app);
        }
        return toVo(require(id));
    }

    @Transactional
    public OnboardingDtos.OnboardingVO confirm(long id) {
        requireHrOrAdmin();
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
        cmd.setPositionId(normalizePositionId(app.getPositionId()));
        cmd.setEmploymentType(app.getEmploymentType());
        cmd.setProbationMonths(app.getProbationMonths());
        cmd.setProbationSalaryRatio(app.getProbationSalaryRatio());
        cmd.setBaseSalary(app.getBaseSalary());
        cmd.setManagerId(app.getManagerId() != null
                ? app.getManagerId()
                : employeeLifecycleService.resolveDeptHeadEmployeeId(app.getDepartmentId()));
        cmd.setExpectedOnboardDate(app.getExpectedOnboardDate());

        Long employeeId = employeeArchiveClient.archive(cmd);
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
        mapper.updateById(app);

        Long deptMgrUserId = null;
        try {
            deptMgrUserId = employeeLifecycleService.resolveDeptManagerUserIdByDepartment(app.getDepartmentId());
        } catch (Exception ignored) {
            // 通知降级
        }
        Long hrUserId = null;
        try {
            hrUserId = employeeLifecycleService.resolveHrApproverUserId(currentUserProvider.requireUserId());
        } catch (Exception ignored) {
            // 通知降级
        }
        String empNo = null;
        Employee archived = employeeMapper.selectById(employeeId);
        if (archived != null) {
            empNo = archived.getEmployeeNo();
        }
        notifyPublisher.publishOnboardingWelcome(
                app.getEmail(), app.getName(), app.getMobile(), empNo, employeeId, deptMgrUserId, hrUserId);

        return toVo(app);
    }

    @Transactional
    public OnboardingDtos.OnboardingVO abandon(long id) {
        OnboardingApplication app = require(id);
        if (!ApprovalStatus.Onboarding.APPROVED_PENDING.code().equalsIgnoreCase(app.getStatus())) {
            throw new BusinessException(ErrorCode.APPROVAL_STATE_INVALID, "仅「审批通过待入职」可放弃");
        }
        assertCanManageApprovedPending(app);
        String next;
        try {
            next = ApprovalStateMachine.transit(
                    ApprovalStateMachine.ProcessType.ONBOARDING, app.getStatus(), ApprovalAction.ABANDON);
        } catch (IllegalStateException | IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.APPROVAL_STATE_INVALID, "当前状态不可放弃: " + app.getStatus());
        }
        app.setStatus(next);
        app.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(app);
        return toVo(app);
    }

    public void onApprovalFinished(String businessKey, boolean approved, String comment) {
        Long id = parseBusinessId(businessKey);
        if (id == null) {
            return;
        }
        OnboardingApplication app = mapper.selectById(id);
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
        mapper.updateById(app);
    }

    public void onApprovalWithdrawn(String businessKey) {
        Long id = parseBusinessId(businessKey);
        if (id == null) {
            return;
        }
        OnboardingApplication app = mapper.selectById(id);
        if (app == null) {
            return;
        }
        if (ApprovalStatus.Onboarding.PENDING.code().equalsIgnoreCase(app.getStatus())) {
            String next = ApprovalStateMachine.transit(
                    ApprovalStateMachine.ProcessType.ONBOARDING, app.getStatus(), ApprovalAction.WITHDRAW);
            app.setStatus(next);
            app.setUpdatedAt(LocalDateTime.now());
            mapper.updateById(app);
        }
    }

    /** 审批中心业务详情 */
    public Map<String, Object> businessDetail(Long applicationId) {
        Map<String, Object> biz = new HashMap<>();
        if (applicationId == null) {
            return biz;
        }
        OnboardingApplication app = mapper.selectById(applicationId);
        if (app == null) {
            return biz;
        }
        biz.put("applicationId", app.getId());
        biz.put("name", app.getName());
        biz.put("gender", app.getGender());
        biz.put("mobile", app.getMobile());
        biz.put("email", app.getEmail());
        biz.put("expectedOnboardDate",
                app.getExpectedOnboardDate() != null ? app.getExpectedOnboardDate().toString() : null);
        biz.put("departmentId", app.getDepartmentId());
        Long positionId = normalizePositionId(app.getPositionId());
        biz.put("positionId", positionId);
        if (app.getDepartmentId() != null) {
            biz.put("departmentName", orgLookupMapper.selectDepartmentName(app.getDepartmentId()));
        }
        if (positionId != null) {
            biz.put("positionName", orgLookupMapper.selectPositionName(positionId));
        }
        biz.put("employmentType", app.getEmploymentType());
        biz.put("probationMonths", app.getProbationMonths());
        biz.put("baseSalary", app.getBaseSalary());
        biz.put("managerId", app.getManagerId());
        if (app.getManagerId() != null) {
            try {
                Employee mgr = employeeLifecycleService.requireEmployee(app.getManagerId());
                biz.put("managerName", mgr.getName());
            } catch (Exception ignored) {
                // ignore
            }
        }
        biz.put("status", app.getStatus());
        biz.put("positionStandard", isPositionStandard(app));
        biz.put("gradeMax", resolveGradeMax(app));
        biz.put("needSecondApproval", needSecondApproval(app));
        if (ApprovalStatus.Onboarding.REJECTED.code().equalsIgnoreCase(app.getStatus())) {
            biz.put("rejectReason", loadRejectReason(app.getInstanceId()));
        }
        return biz;
    }

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

    private List<ProcessNodeDef> buildOnboardingNodes(OnboardingApplication app, long initiatorUserId, boolean second) {
        Long deptMgrUserId = employeeLifecycleService.resolveDeptManagerUserIdByDepartment(app.getDepartmentId());
        List<ProcessNodeDef> nodes = new ArrayList<>();
        ProcessNodeDef n1 = new ProcessNodeDef();
        n1.setOrder(1);
        n1.setLabel("部门负责人审批");
        n1.setAssigneeType("DEPT_MANAGER");
        n1.setAssigneeUserId(deptMgrUserId);
        nodes.add(n1);
        if (second) {
            Long hrUserId = employeeLifecycleService.resolveHrApproverUserId(initiatorUserId);
            ProcessNodeDef n2 = new ProcessNodeDef();
            n2.setOrder(2);
            n2.setLabel("HR 二审");
            n2.setAssigneeType("HR_STAFF");
            n2.setAssigneeUserId(hrUserId);
            nodes.add(n2);
        }
        return nodes;
    }

    private boolean isPositionStandard(OnboardingApplication app) {
        Long pid = normalizePositionId(app.getPositionId());
        if (pid == null) {
            return true;
        }
        // 兼容历史：非标职位曾用 positionId = 9000 + 真实 ID
        if (app.getPositionId() != null && app.getPositionId() >= 9000) {
            return false;
        }
        Integer flag = orgLookupMapper.selectPositionIsStandard(pid);
        return flag == null || flag != 0;
    }

    private BigDecimal resolveGradeMax(OnboardingApplication app) {
        Long pid = normalizePositionId(app.getPositionId());
        if (pid != null) {
            String rankMax = orgLookupMapper.selectPositionRankMax(pid);
            return GradeSalaryCap.ofRankMax(rankMax);
        }
        return GradeSalaryCap.ofRankMax(null);
    }

    private Long normalizePositionId(Long positionId) {
        if (positionId == null) {
            return null;
        }
        return positionId >= 9000 ? positionId - 9000 : positionId;
    }

    private void fillDefaults(OnboardingDtos.OnboardingFormRequest req) {
        if (req == null) {
            return;
        }
        Long positionId = req.getPositionId();
        if (positionId != null) {
            Long realPid = normalizePositionId(positionId);
            if (req.getProbationMonths() == null) {
                Integer months = orgLookupMapper.selectPositionDefaultProbationMonths(realPid);
                if (months != null && months > 0) {
                    req.setProbationMonths(months);
                }
            }
            if (req.getPositionStandard() == null) {
                Integer flag = orgLookupMapper.selectPositionIsStandard(realPid);
                req.setPositionStandard(flag == null || flag != 0);
            }
            if (req.getGradeMaxSalary() == null) {
                req.setGradeMaxSalary(GradeSalaryCap.ofRankMax(orgLookupMapper.selectPositionRankMax(realPid)));
            }
        }
        if (req.getManagerId() == null && req.getDepartmentId() != null) {
            req.setManagerId(employeeLifecycleService.resolveDeptHeadEmployeeId(req.getDepartmentId()));
        }
        if (req.getProbationMonths() == null) {
            req.setProbationMonths(3);
        }
        if (req.getProbationSalaryRatio() == null) {
            req.setProbationSalaryRatio(new BigDecimal("0.80"));
        }
    }

    private OnboardingApplication require(long id) {
        OnboardingApplication app = mapper.selectById(id);
        if (app == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "入职申请不存在");
        }
        return app;
    }

    private void ensureMobileUnique(String mobile, Long excludeId) {
        LambdaQueryWrapper<OnboardingApplication> q = new LambdaQueryWrapper<OnboardingApplication>()
                .eq(OnboardingApplication::getMobile, mobile)
                .notIn(OnboardingApplication::getStatus, List.of("abandoned", "rejected", "onboarded"));
        if (excludeId != null) {
            q.ne(OnboardingApplication::getId, excludeId);
        }
        if (mapper.selectCount(q) > 0) {
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
        Long lookupPositionId = normalizePositionId(positionId);
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
            // 始终存真实职位 ID；非标以 position.is_standard / 表单 positionStandard 判定二审
            app.setPositionId(normalizePositionId(req.getPositionId()));
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
        // 显式标记非标：用 positionId 偏移兼容历史 needSecond（仅当 DB 仍标标准时）
        if (Boolean.FALSE.equals(req.getPositionStandard())
                && app.getPositionId() != null
                && app.getPositionId() < 9000
                && isPositionStandard(app)) {
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
        vo.setPositionId(normalizePositionId(app.getPositionId()));
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
        vo.setPositionStandard(isPositionStandard(app));
        vo.setGradeMaxSalary(resolveGradeMax(app));
        if (ApprovalStatus.Onboarding.REJECTED.code().equalsIgnoreCase(app.getStatus())) {
            vo.setRejectReason(loadRejectReason(app.getInstanceId()));
        }
        return vo;
    }

    private String loadRejectReason(Long instanceId) {
        if (instanceId == null) {
            return null;
        }
        ApprovalLog log = approvalLogMapper.selectOne(new LambdaQueryWrapper<ApprovalLog>()
                .eq(ApprovalLog::getInstanceId, instanceId)
                .eq(ApprovalLog::getAction, "REJECT")
                .orderByDesc(ApprovalLog::getId)
                .last("LIMIT 1"));
        return log == null ? null : log.getComment();
    }

    private void applyVisibilityFilter(LambdaQueryWrapper<OnboardingApplication> q) {
        LoginUser login = SecurityUtils.getLoginUser();
        if (login == null || login.getUserId() == null) {
            return;
        }
        if (login.hasRole("HR_STAFF") || login.hasRole("SYS_ADMIN")) {
            return;
        }
        // 部门主管不进「入转调离」管理台；列表仅创建人可见（兜底）
        q.eq(OnboardingApplication::getCreatedBy, login.getUserId());
    }

    private void assertCanView(OnboardingApplication app) {
        LoginUser login = SecurityUtils.getLoginUser();
        if (login == null || login.getUserId() == null) {
            return;
        }
        if (login.hasRole("HR_STAFF") || login.hasRole("SYS_ADMIN")) {
            return;
        }
        long userId = login.getUserId();
        if (app.getCreatedBy() != null && app.getCreatedBy() == userId) {
            return;
        }
        // 审批中心打开业务详情：目标部门负责人仍可查看
        if (login.hasRole("DEPT_MANAGER")) {
            List<Long> deptIds = orgLookupMapper.selectDepartmentIdsByHeadUserId(userId);
            if (deptIds != null && app.getDepartmentId() != null && deptIds.contains(app.getDepartmentId())) {
                return;
            }
        }
        throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看该入职申请");
    }

    private void assertCanMutate(OnboardingApplication app) {
        LoginUser login = SecurityUtils.getLoginUser();
        if (login == null || login.getUserId() == null) {
            return;
        }
        if (login.hasRole("HR_STAFF") || login.hasRole("SYS_ADMIN")) {
            return;
        }
        long userId = login.getUserId();
        if (app.getCreatedBy() != null && app.getCreatedBy() == userId) {
            return;
        }
        throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作该入职申请");
    }

    private void requireHrOrAdmin() {
        LoginUser login = SecurityUtils.getLoginUser();
        if (login == null) {
            return;
        }
        if (login.hasRole("HR_STAFF") || login.hasRole("SYS_ADMIN")) {
            return;
        }
        throw new BusinessException(ErrorCode.FORBIDDEN, "仅 HR/管理员可操作入职管理");
    }

    /** 待入职：仅 HR/管理员或创建人可改入职日 / 放弃 */
    private void assertCanManageApprovedPending(OnboardingApplication app) {
        LoginUser login = SecurityUtils.getLoginUser();
        if (login == null || login.getUserId() == null) {
            return;
        }
        if (login.hasRole("HR_STAFF") || login.hasRole("SYS_ADMIN")) {
            return;
        }
        long userId = login.getUserId();
        if (app.getCreatedBy() != null && app.getCreatedBy() == userId) {
            return;
        }
        throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作该待入职申请");
    }

    private Long parseBusinessId(String businessKey) {
        if (businessKey == null || businessKey.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(businessKey.trim());
        } catch (NumberFormatException ex) {
            if (businessKey.startsWith("ONBOARDING:")) {
                try {
                    return Long.parseLong(businessKey.substring("ONBOARDING:".length()));
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            return null;
        }
    }
}
