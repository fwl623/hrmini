package com.company.hrms.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.hrms.common.exception.BusinessException;
import com.company.hrms.common.exception.ErrorCode;
import com.company.hrms.common.web.PageResult;
import com.company.hrms.employee.entity.Employee;
import com.company.hrms.employee.service.EmployeeLifecycleService;
import com.company.hrms.employee.vo.PendingRegularizationVO;
import com.company.hrms.workflow.dto.LifecycleDtos;
import com.company.hrms.workflow.entity.RegularizationApplication;
import com.company.hrms.workflow.mapper.RegularizationApplicationMapper;
import com.company.hrms.workflow.model.ProcessNodeDef;
import com.company.hrms.workflow.notify.ApprovalNotifyPublisher;
import com.company.hrms.workflow.support.CurrentUserProvider;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 【转正业务 Service】管理转正申请单与审批联动；
 * 审批通过后按 PASS/EXTEND/FAIL 分支调用 {@link EmployeeLifecycleService} 改员工状态。
 */
@Service
public class RegularizationService {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final RegularizationApplicationMapper mapper;
    private final EmployeeLifecycleService employeeLifecycleService;
    private final DbApprovalService dbApprovalService;
    private final CurrentUserProvider currentUserProvider;
    private final ApprovalNotifyPublisher notifyPublisher;

    public RegularizationService(RegularizationApplicationMapper mapper,
                                 EmployeeLifecycleService employeeLifecycleService,
                                 @Lazy DbApprovalService dbApprovalService,
                                 CurrentUserProvider currentUserProvider,
                                 ApprovalNotifyPublisher notifyPublisher) {
        this.mapper = mapper;
        this.employeeLifecycleService = employeeLifecycleService;
        this.dbApprovalService = dbApprovalService;
        this.currentUserProvider = currentUserProvider;
        this.notifyPublisher = notifyPublisher;
    }

    /**
     * 查询待转正员工列表：试用结束日 ≤ 今天+7（含已逾期），排除已有 APPROVING 转正单的员工。
     * 【调用】{@code RegularizationController.listPending}；{@link #scanAndRemindHr} 内部调用
     * 【实现】
     * <ol>
     *   <li>{@code EmployeeLifecycleService.listPendingRegularization} 查 B 模块试用到期员工</li>
     *   <li>{@code RegularizationApplicationMapper} 查 status=APPROVING 的 employeeId 集合并排除</li>
     * </ol>
     */
    public List<PendingRegularizationVO> listPending() {
        LocalDate today = LocalDate.now();
        List<PendingRegularizationVO> pending =
                employeeLifecycleService.listPendingRegularization(null, today.plusDays(7));
        Set<Long> approvingIds = mapper.selectList(new LambdaQueryWrapper<RegularizationApplication>()
                        .eq(RegularizationApplication::getStatus, "APPROVING"))
                .stream()
                .map(RegularizationApplication::getEmployeeId)
                .collect(Collectors.toCollection(HashSet::new));
        return pending.stream()
                .filter(p -> p.getEmployeeId() != null && !approvingIds.contains(p.getEmployeeId()))
                .collect(Collectors.toList());
    }

    /**
     *  定时扫描待转正员工并 MQ/日志知会 HR（逾期人数一并统计）。
     * 【调用】{@code RegularizationPendingScanJob}
     * 【实现】
     *   <li>{@link #listPending} 获取待转正列表</li>
     *   <li>{@code EmployeeLifecycleService.resolveHrApproverUserId} 解析 HR 用户</li>
     *   <li>{@code ApprovalNotifyPublisher.publishRegularizationPendingRemind} 发送提醒</li>
     */
    public int scanAndRemindHr() {
        List<PendingRegularizationVO> pending = listPending();
        if (pending.isEmpty()) {
            return 0;
        }
        long overdue = pending.stream().filter(p -> Boolean.TRUE.equals(p.getOverdue())).count();
        Long hrUserId = null;
        try {
            hrUserId = employeeLifecycleService.resolveHrApproverUserId(null);
        } catch (Exception ignored) {
            // 通知降级
        }
        notifyPublisher.publishRegularizationPendingRemind(pending.size(), overdue, hrUserId);
        return pending.size();
    }

    /**
     * 分页查询转正申请记录，可选按 status 筛选。
     * 【调用】{@code RegularizationController.list}
     * 【实现】
     *   <li>{@code RegularizationApplicationMapper.selectList} 按 id 倒序，可选 status 条件</li>
     *   <li>内存分页，{@code toVo} 转 VO（含 nextAction=START_RESIGNATION 当 FAIL 已完成）</li>
     */
    public PageResult<LifecycleDtos.RegularizationVO> list(int page, int pageSize, String status) {
        LambdaQueryWrapper<RegularizationApplication> q = new LambdaQueryWrapper<RegularizationApplication>()
                .orderByDesc(RegularizationApplication::getId);
        if (status != null && !status.isBlank()) {
            q.eq(RegularizationApplication::getStatus, status.toUpperCase(Locale.ROOT));
        }
        List<RegularizationApplication> all = mapper.selectList(q);
        int p = page <= 0 ? 1 : page;
        int size = pageSize <= 0 ? 20 : Math.min(pageSize, 100);
        int from = Math.min((p - 1) * size, all.size());
        int to = Math.min(from + size, all.size());
        List<LifecycleDtos.RegularizationVO> list = all.subList(from, to).stream()
                .map(this::toVo)
                .collect(Collectors.toList());
        return PageResult.of(list, all.size(), p, size);
    }

    /**
     * HR 发起转正审批：落转正单并创建审批实例（部门负责人→HR 两节点）。
     * approvalResult 为 PASS/EXTEND/FAIL，决定审批通过后的分支逻辑。
     * 【调用】{@code RegularizationController.create}
     * 【实现】
     *   <li>校验员工为试用期(10)、performanceEvaluation、PASS/EXTEND/FAIL 及 extendMonths</li>
     *   <li>防重复：同员工无 APPROVING 单</li>
     *   <li>{@code RegularizationApplicationMapper.insert} status=APPROVING</li>
     *   <li>{@code buildRegularizationNodes} → {@code DbApprovalService.createInstance}（REGULARIZATION）</li>
     *   <li>回写 instanceId</li>
     */
    @Transactional
    public LifecycleDtos.RegularizationVO create(LifecycleDtos.RegularizationCreateRequest req) {
        if (req == null || req.getEmployeeId() == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "employeeId 必填");
        }
        if (req.getPerformanceEvaluation() == null || req.getPerformanceEvaluation().isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "performanceEvaluation 必填");
        }
        String result = req.getApprovalResult() == null ? "" : req.getApprovalResult().trim().toUpperCase(Locale.ROOT);
        if (!List.of("PASS", "EXTEND", "FAIL").contains(result)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "approvalResult 须为 PASS/EXTEND/FAIL");
        }
        if ("EXTEND".equals(result) && (req.getExtendMonths() == null || req.getExtendMonths() <= 0)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "EXTEND 须填写 extendMonths");
        }

        Employee emp = employeeLifecycleService.requireEmployee(req.getEmployeeId());
        if (emp.getEmploymentStatus() == null || emp.getEmploymentStatus() != 10) {
            throw new BusinessException(ErrorCode.EMPLOYEE_STATUS_INVALID, "仅试用期员工可发起转正");
        }
        long pending = mapper.selectCount(new LambdaQueryWrapper<RegularizationApplication>()
                .eq(RegularizationApplication::getEmployeeId, req.getEmployeeId())
                .eq(RegularizationApplication::getStatus, "APPROVING"));
        if (pending > 0) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "该员工已有进行中的转正申请");
        }

        long userId = currentUserProvider.requireUserId();
        LocalDateTime now = LocalDateTime.now();
        RegularizationApplication app = new RegularizationApplication();
        app.setEmployeeId(req.getEmployeeId());
        app.setStatus("APPROVING");
        app.setProbationStartDate(emp.getHireDate() != null ? emp.getHireDate() : LocalDate.now());
        app.setProbationEndDate(emp.getProbationEndDate() != null ? emp.getProbationEndDate() : LocalDate.now().plusMonths(3));
        app.setPerformanceEvaluation(req.getPerformanceEvaluation());
        app.setSalaryAdjustment(req.getSalaryAdjustment());
        app.setApprovalResult(result);
        app.setExtendMonths(req.getExtendMonths());
        app.setCreatedBy(userId);
        app.setCreatedAt(now);
        mapper.insert(app);

        Long instanceId = dbApprovalService.createInstance(
                "REGULARIZATION",
                String.valueOf(app.getId()),
                userId,
                buildRegularizationNodes(emp, userId),
                DbApprovalService.InstanceDisplay.of(
                        emp.getName() + "转正申请(" + result + ")",
                        emp.getName(),
                        null,
                        "REG-" + app.getId()),
                null);
        app.setInstanceId(instanceId);
        mapper.updateById(app);
        return toVo(app);
    }

    /**
     *  转正审批全部通过回调：按 approvalResult 执行 PASS（转正+可选调薪）/ EXTEND（延长试用期）/ FAIL（知会 HR 走离职）。
     * 【调用】{@code LifecycleApprovalHandlerImpl.onApproved}（processType=REGULARIZATION）
     * 【实现】
     *   <li>加载转正单，{@code requireStatus} 校验 APPROVING/PENDING</li>
     *   <li>PASS：{@code EmployeeLifecycleService.regularizePass} + 可选 {@code applyRegularizationSalary}</li>
     *   <li>EXTEND：{@code EmployeeLifecycleService.regularizeExtend}</li>
     *   <li>FAIL：{@code ApprovalNotifyPublisher.publishRegularizationFailNeedResign}（MQ）</li>
     *   <li>单 status → COMPLETED</li>
     */
    @Transactional
    public void onApproved(Long appId) {
        RegularizationApplication app = mapper.selectById(appId);
        if (app == null) {
            return;
        }
        requireStatus(app.getStatus(), "APPROVING", "PENDING");
        String result = app.getApprovalResult();
        if ("PASS".equalsIgnoreCase(result)) {
            employeeLifecycleService.regularizePass(app.getEmployeeId());
            if (app.getSalaryAdjustment() != null) {
                employeeLifecycleService.applyRegularizationSalary(
                        app.getEmployeeId(), app.getSalaryAdjustment(), app.getCreatedBy());
            }
        } else if ("EXTEND".equalsIgnoreCase(result)) {
            int months = app.getExtendMonths() == null ? 1 : app.getExtendMonths();
            employeeLifecycleService.regularizeExtend(app.getEmployeeId(), months);
        } else if ("FAIL".equalsIgnoreCase(result)) {
            // 试用不合格：不改员工状态，知会 HR 走正式离职（双阶段）
            Long hrUserId = null;
            try {
                hrUserId = employeeLifecycleService.resolveHrApproverUserId(null);
            } catch (Exception ignored) {
                // ignore
            }
            notifyPublisher.publishRegularizationFailNeedResign(
                    app.getId(), app.getEmployeeId(), hrUserId);
        }
        app.setStatus("COMPLETED");
        mapper.updateById(app);
    }

    /**
     *  转正审批被驳回或撤回：转正单 status → REJECTED。
     * 【调用】{@code LifecycleApprovalHandlerImpl.onRejected} / {@code LifecycleApprovalHandlerImpl.onWithdrawn}
     * 【实现】
     *   <li>加载转正单，{@code requireStatus} 校验 APPROVING/PENDING</li>
     *   <li>{@code RegularizationApplicationMapper.updateById} status=REJECTED</li>
     */
    @Transactional
    public void onRejectedOrWithdrawn(Long appId) {
        RegularizationApplication app = mapper.selectById(appId);
        if (app == null) {
            return;
        }
        requireStatus(app.getStatus(), "APPROVING", "PENDING");
        app.setStatus("REJECTED");
        mapper.updateById(app);
    }

    /**
     *  组装审批中心展示用的转正业务详情 Map。
     * 【调用】{@code DbApprovalService.buildBusinessDetail}（processType=REGULARIZATION）
     * 【实现】
     *   <li>{@code RegularizationApplicationMapper.selectById} 加载申请</li>
     *   <li>{@code EmployeeLifecycleService.requireEmployee} 补员工姓名/工号/部门</li>
     *   <li>填充 approvalResult、试用期、绩效评价、调薪等字段</li>
     *   <li>FAIL 且 COMPLETED 时附加 nextAction=START_RESIGNATION</li>
     */
    public Map<String, Object> businessDetail(Long applicationId) {
        Map<String, Object> biz = new HashMap<>();
        if (applicationId == null) {
            return biz;
        }
        RegularizationApplication app = mapper.selectById(applicationId);
        if (app == null) {
            return biz;
        }
        biz.put("applicationId", app.getId());
        biz.put("employeeId", app.getEmployeeId());
        try {
            Employee emp = employeeLifecycleService.requireEmployee(app.getEmployeeId());
            biz.put("employeeName", emp.getName());
            biz.put("employeeNo", emp.getEmployeeNo());
            biz.put("departmentId", emp.getDepartmentId());
            biz.put("positionId", emp.getPositionId());
        } catch (Exception ignored) {
            // ignore
        }
        biz.put("status", app.getStatus());
        biz.put("approvalResult", app.getApprovalResult());
        biz.put("extendMonths", app.getExtendMonths());
        biz.put("performanceEvaluation", app.getPerformanceEvaluation());
        biz.put("salaryAdjustment", app.getSalaryAdjustment());
        biz.put("probationStartDate",
                app.getProbationStartDate() != null ? app.getProbationStartDate().toString() : null);
        biz.put("probationEndDate",
                app.getProbationEndDate() != null ? app.getProbationEndDate().toString() : null);
        if ("FAIL".equalsIgnoreCase(app.getApprovalResult())
                && "COMPLETED".equalsIgnoreCase(app.getStatus())) {
            biz.put("nextAction", "START_RESIGNATION");
        }
        return biz;
    }

    private List<ProcessNodeDef> buildRegularizationNodes(Employee emp, long initiatorUserId) {
        Long deptMgrUserId = employeeLifecycleService.resolveDeptManagerUserId(emp.getId());
        Long hrUserId = employeeLifecycleService.resolveHrApproverUserId(initiatorUserId);
        List<ProcessNodeDef> nodes = new ArrayList<>();
        ProcessNodeDef n1 = new ProcessNodeDef();
        n1.setOrder(1);
        n1.setLabel("部门负责人审批");
        n1.setAssigneeType("DEPT_MANAGER");
        n1.setAssigneeUserId(deptMgrUserId);
        nodes.add(n1);
        ProcessNodeDef n2 = new ProcessNodeDef();
        n2.setOrder(2);
        n2.setLabel("HR 审批");
        n2.setAssigneeType("HR_STAFF");
        n2.setAssigneeUserId(hrUserId);
        nodes.add(n2);
        return nodes;
    }

    private static void requireStatus(String current, String... allowed) {
        if (current == null || current.isBlank()) {
            throw new BusinessException(ErrorCode.APPROVAL_STATE_INVALID, "申请状态为空，无法流转");
        }
        for (String a : allowed) {
            if (a.equalsIgnoreCase(current)) {
                return;
            }
        }
        throw new BusinessException(ErrorCode.APPROVAL_STATE_INVALID,
                "非法状态转换: current=" + current + ", allowed=" + String.join("/", allowed));
    }

    private LifecycleDtos.RegularizationVO toVo(RegularizationApplication app) {
        LifecycleDtos.RegularizationVO vo = new LifecycleDtos.RegularizationVO();
        vo.setId(app.getId());
        vo.setEmployeeId(app.getEmployeeId());
        try {
            Employee emp = employeeLifecycleService.requireEmployee(app.getEmployeeId());
            vo.setEmployeeName(emp.getName());
            vo.setEmpNo(emp.getEmployeeNo());
        } catch (Exception ignored) {
            // ignore
        }
        vo.setStatus(app.getStatus());
        vo.setApprovalResult(app.getApprovalResult());
        vo.setExtendMonths(app.getExtendMonths());
        vo.setPerformanceEvaluation(app.getPerformanceEvaluation());
        vo.setSalaryAdjustment(app.getSalaryAdjustment());
        vo.setProbationStartDate(app.getProbationStartDate() == null ? null : app.getProbationStartDate().toString());
        vo.setProbationEndDate(app.getProbationEndDate() == null ? null : app.getProbationEndDate().toString());
        vo.setInstanceId(app.getInstanceId());
        vo.setCreatedAt(app.getCreatedAt() == null ? null : DT.format(app.getCreatedAt()));
        if ("FAIL".equalsIgnoreCase(app.getApprovalResult())
                && "COMPLETED".equalsIgnoreCase(app.getStatus())) {
            vo.setNextAction("START_RESIGNATION");
        }
        return vo;
    }
}
