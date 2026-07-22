package com.company.hrms.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.hrms.common.exception.BusinessException;
import com.company.hrms.common.exception.ErrorCode;
import com.company.hrms.common.security.LoginUser;
import com.company.hrms.common.security.SecurityUtils;
import com.company.hrms.common.web.PageResult;
import com.company.hrms.employee.dto.TransferEffectDTO;
import com.company.hrms.employee.entity.Employee;
import com.company.hrms.employee.service.EmployeeLifecycleService;
import com.company.hrms.workflow.dto.LifecycleDtos;
import com.company.hrms.workflow.entity.ApprovalInstance;
import com.company.hrms.workflow.entity.TransferApplication;
import com.company.hrms.workflow.mapper.ApprovalInstanceMapper;
import com.company.hrms.workflow.mapper.OrgLookupMapper;
import com.company.hrms.workflow.mapper.TransferApplicationMapper;
import com.company.hrms.workflow.model.ProcessNodeDef;
import com.company.hrms.workflow.support.CurrentUserProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

/**
 * 【调岗业务 Service】管理调岗申请、三/四节点审批链及生效日延迟执行；
 * 审批通过后调用 {@link EmployeeLifecycleService#applyTransfer} 变更员工组织与薪资。
 */
@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final TransferApplicationMapper mapper;
    private final ApprovalInstanceMapper instanceMapper;
    private final EmployeeLifecycleService employeeLifecycleService;
    private final DbApprovalService dbApprovalService;
    private final CurrentUserProvider currentUserProvider;
    private final OrgLookupMapper orgLookupMapper;

    public TransferService(TransferApplicationMapper mapper,
                           ApprovalInstanceMapper instanceMapper,
                           EmployeeLifecycleService employeeLifecycleService,
                           @Lazy DbApprovalService dbApprovalService,
                           CurrentUserProvider currentUserProvider,
                           OrgLookupMapper orgLookupMapper) {
        this.mapper = mapper;
        this.instanceMapper = instanceMapper;
        this.employeeLifecycleService = employeeLifecycleService;
        this.dbApprovalService = dbApprovalService;
        this.currentUserProvider = currentUserProvider;
        this.orgLookupMapper = orgLookupMapper;
    }

    /**
     *  组装审批中心展示用的调岗业务详情 Map（原/新部门、职位、调薪、生效日等）。
     * 【调用】{@code DbApprovalService.buildBusinessDetail}（processType=TRANSFER）
     * 【实现】
     *   <li>{@code TransferApplicationMapper.selectById} 加载申请</li>
     *   <li>{@code EmployeeLifecycleService} / {@code OrgLookupMapper} 解析员工与部门职位名称</li>
     *   <li>填充 from/new 部门、职位、直属上级、调薪、reason、status</li>
     */
    public Map<String, Object> businessDetail(Long applicationId) {
        Map<String, Object> biz = new HashMap<>();
        if (applicationId == null) {
            return biz;
        }
        TransferApplication app = mapper.selectById(applicationId);
        if (app == null) {
            return biz;
        }
        biz.put("transferId", app.getId());
        biz.put("employeeId", app.getEmployeeId());
        try {
            Employee emp = employeeLifecycleService.requireEmployee(app.getEmployeeId());
            biz.put("employeeName", emp.getName());
            biz.put("employeeNo", emp.getEmployeeNo());
        } catch (Exception ignored) {
            // ignore
        }
        biz.put("fromDepartmentId", app.getFromDepartmentId());
        biz.put("newDepartmentId", app.getNewDepartmentId());
        biz.put("newPositionId", app.getNewPositionId());
        if (app.getFromDepartmentId() != null) {
            biz.put("fromDepartmentName", orgLookupMapper.selectDepartmentName(app.getFromDepartmentId()));
        }
        if (app.getNewDepartmentId() != null) {
            biz.put("newDepartmentName", orgLookupMapper.selectDepartmentName(app.getNewDepartmentId()));
        }
        if (app.getNewPositionId() != null) {
            biz.put("newPositionName", orgLookupMapper.selectPositionName(app.getNewPositionId()));
        }
        biz.put("newJobLevel", app.getNewJobLevel());
        biz.put("newManagerId", app.getNewManagerId());
        if (app.getNewManagerId() != null) {
            try {
                biz.put("newManagerName", employeeLifecycleService.requireEmployee(app.getNewManagerId()).getName());
            } catch (Exception ignored) {
                // ignore
            }
        }
        biz.put("salaryAdjustment", app.getSalaryAdjustment());
        biz.put("effectiveDate", app.getEffectiveDate() == null ? null : app.getEffectiveDate().toString());
        biz.put("reason", app.getReason());
        biz.put("status", app.getStatus());
        return biz;
    }

    /**
     *  HR 分页查询调岗申请列表，可选按 status 筛选。
     * 【调用】{@code TransferController.list}
     */
    public PageResult<LifecycleDtos.TransferVO> list(int page, int pageSize, String status) {
        requireHrOrAdmin();
        LambdaQueryWrapper<TransferApplication> q = new LambdaQueryWrapper<TransferApplication>()
                .orderByDesc(TransferApplication::getId);
        if (status != null && !status.isBlank()) {
            q.eq(TransferApplication::getStatus, status.toUpperCase(Locale.ROOT));
        }
        List<TransferApplication> all = mapper.selectList(q);
        int p = page <= 0 ? 1 : page;
        int size = pageSize <= 0 ? 20 : Math.min(pageSize, 100);
        int from = Math.min((p - 1) * size, all.size());
        int to = Math.min(from + size, all.size());
        List<LifecycleDtos.TransferVO> list = all.subList(from, to).stream()
                .map(a -> toVo(a, false))
                .collect(Collectors.toList());
        return PageResult.of(list, all.size(), p, size);
    }

    /**
     *  按 id 返回调岗申请详情（含审批节点进度）。
     * 【调用】{@code TransferController.detail}
     */
    public LifecycleDtos.TransferVO detail(Long id) {
        requireHrOrAdmin();
        TransferApplication app = mapper.selectById(id);
        if (app == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "调岗申请不存在");
        }
        return toVo(app, true);
    }

    /**
     * HR 发起调岗：校验部门变更约束(30004)，落调岗单并创建审批实例。
     * 含调薪时审批链增加财务节点（原部门→新部门→财务→HR 备案）。
     * 【调用】{@code TransferController.create}
     * 【实现】
     *   <li>{@code requireHrOrAdmin}；校验 newDepartmentId ≠ 原部门、生效日、员工状态(10/20)</li>
     *   <li>{@code TransferApplicationMapper.insert} status=APPROVING，记录 from/new 部门职位等</li>
     *   <li>{@code buildTransferNodes} 组装节点链 → {@code DbApprovalService.createInstance}（TRANSFER）</li>
     *   <li>回写 instanceId，{@code toVo} 返回详情</li>
     */
    @Transactional
    public LifecycleDtos.TransferVO create(LifecycleDtos.TransferCreateRequest req) {
        requireHrOrAdmin();
        if (req == null || req.getEmployeeId() == null || req.getNewDepartmentId() == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "employeeId / newDepartmentId 必填");
        }
        if (req.getEffectiveDate() == null || req.getEffectiveDate().isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "effectiveDate 必填");
        }
        LocalDate effectiveDate;
        try {
            effectiveDate = LocalDate.parse(req.getEffectiveDate());
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "effectiveDate 格式须为 YYYY-MM-DD");
        }
        if (effectiveDate.isBefore(LocalDate.now())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "生效日期不能早于今天");
        }
        if (req.getReason() == null || req.getReason().isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "reason 必填");
        }
        if (req.getSalaryAdjustment() != null && req.getSalaryAdjustment().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "调岗后基本工资须大于 0");
        }

        Employee emp = employeeLifecycleService.requireEmployee(req.getEmployeeId());
        int st = emp.getEmploymentStatus() == null ? -1 : emp.getEmploymentStatus();
        if (st != 10 && st != 20) {
            throw new BusinessException(ErrorCode.EMPLOYEE_STATUS_INVALID, "仅试用/正式员工可调岗");
        }
        if (req.getNewDepartmentId().equals(emp.getDepartmentId())) {
            throw new BusinessException(ErrorCode.TRANSFER_DEPT_UNCHANGED);
        }
        if (req.getNewDepartmentId() <= 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "newDepartmentId 无效");
        }
        if (req.getNewPositionId() != null && req.getNewPositionId() <= 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "newPositionId 无效");
        }

        long userId = currentUserProvider.requireUserId();
        boolean withSalary = req.getSalaryAdjustment() != null;

        TransferApplication app = new TransferApplication();
        app.setEmployeeId(req.getEmployeeId());
        app.setStatus("APPROVING");
        app.setFromDepartmentId(emp.getDepartmentId());
        app.setNewDepartmentId(req.getNewDepartmentId());
        app.setNewPositionId(req.getNewPositionId());
        app.setNewJobLevel(req.getNewJobLevel());
        app.setNewManagerId(req.getNewManagerId());
        app.setSalaryAdjustment(req.getSalaryAdjustment());
        app.setEffectiveDate(effectiveDate);
        app.setReason(req.getReason());
        app.setCreatedBy(userId);
        app.setCreatedAt(LocalDateTime.now());
        mapper.insert(app);

        List<ProcessNodeDef> nodes = buildTransferNodes(emp, req.getNewDepartmentId(), userId, withSalary);
        Long instanceId = dbApprovalService.createInstance(
                "TRANSFER",
                String.valueOf(app.getId()),
                userId,
                nodes,
                DbApprovalService.InstanceDisplay.of(
                        emp.getName() + "调岗申请" + (withSalary ? "(含调薪)" : ""),
                        emp.getName(),
                        null,
                        "TR-" + app.getId()),
                null);
        app.setInstanceId(instanceId);
        mapper.updateById(app);
        return toVo(app, true);
    }

    /**
     *  调岗审批全部通过回调：生效日未到则 PENDING_EFFECT；否则立即 {@code doEffect} 变更员工档案。
     * 【调用】{@code LifecycleApprovalHandlerImpl.onApproved}（processType=TRANSFER）
     */
    @Transactional
    public void onApproved(Long appId) {
        TransferApplication app = mapper.selectById(appId);
        if (app == null) {
            return;
        }
        requireStatus(app.getStatus(), "APPROVING", "PENDING");
        LocalDate effectiveDate = app.getEffectiveDate();
        // 生效日未到：待生效，由 Job 到期执行
        if (effectiveDate != null && effectiveDate.isAfter(LocalDate.now())) {
            app.setStatus("PENDING_EFFECT");
            mapper.updateById(app);
            log.info("调岗审批通过，待生效日执行 appId={} effectiveDate={}", appId, effectiveDate);
            return;
        }
        doEffect(app);
    }

    /**
     *  定时 Job / 联调手动触发：将到期待生效调岗单执行 {@code doEffect}。
     * 【调用】{@code TransferEffectJob}；{@code TransferController.effectDue}（联调）
     */
    @Transactional
    public int effectDueTransfers(LocalDate today) {
        List<TransferApplication> due = mapper.selectList(new LambdaQueryWrapper<TransferApplication>()
                .eq(TransferApplication::getStatus, "PENDING_EFFECT")
                .le(TransferApplication::getEffectiveDate, today));
        int count = 0;
        for (TransferApplication app : due) {
            try {
                doEffect(app);
                count++;
            } catch (Exception e) {
                log.warn("调岗生效失败 appId={} employeeId={}: {}",
                        app.getId(), app.getEmployeeId(), e.getMessage());
            }
        }
        return count;
    }

    private void doEffect(TransferApplication app) {
        TransferEffectDTO dto = new TransferEffectDTO();
        dto.setTransferAppId(app.getId());
        dto.setNewDepartmentId(app.getNewDepartmentId());
        dto.setNewPositionId(app.getNewPositionId());
        dto.setNewJobLevel(app.getNewJobLevel());
        dto.setNewManagerId(app.getNewManagerId());
        dto.setNewBaseSalary(app.getSalaryAdjustment());
        dto.setEffectiveDate(app.getEffectiveDate());
        dto.setReason(app.getReason());
        employeeLifecycleService.applyTransfer(app.getEmployeeId(), dto);
        app.setStatus("APPROVED");
        mapper.updateById(app);
        log.info("调岗已生效 appId={} employeeId={}", app.getId(), app.getEmployeeId());
    }

    /**
     *  调岗审批被驳回或撤回：调岗单 → REJECTED 或 CANCELLED。
     * 【调用】{@code LifecycleApprovalHandlerImpl.onRejected} / {@code LifecycleApprovalHandlerImpl.onWithdrawn}
     */
    @Transactional
    public void onRejectedOrWithdrawn(Long appId, boolean withdrawn) {
        TransferApplication app = mapper.selectById(appId);
        if (app == null) {
            return;
        }
        requireStatus(app.getStatus(), "APPROVING", "PENDING");
        app.setStatus(withdrawn ? "CANCELLED" : "REJECTED");
        mapper.updateById(app);
    }

    /**
     * PRD §5.3.3：原部门负责人 → 新部门负责人 →（有调薪时）财务 → HR 备案。
     * 写入真实 assigneeUserId。
     */
    private List<ProcessNodeDef> buildTransferNodes(Employee emp, Long newDeptId,
                                                    long initiatorUserId, boolean withSalary) {
        Long fromMgr = employeeLifecycleService.resolveDeptManagerUserId(emp.getId());
        Long toMgr = employeeLifecycleService.resolveDeptHeadUserIdByDeptId(newDeptId);
        Long hrUserId = employeeLifecycleService.resolveHrApproverUserId(initiatorUserId);

        List<ProcessNodeDef> nodes = new ArrayList<>();
        nodes.add(node(1, "原部门确认", "DEPT_MANAGER", fromMgr));
        nodes.add(node(2, "新部门接收", "NEW_DEPT_MANAGER", toMgr));
        int order = 3;
        if (withSalary) {
            Long financeId = employeeLifecycleService.resolveFinanceApproverUserId(initiatorUserId);
            nodes.add(node(order++, "财务调薪确认", "FINANCE_MANAGER", financeId));
        }
        nodes.add(node(order, "HR 备案", "HR_STAFF", hrUserId));
        return nodes;
    }

    private static ProcessNodeDef node(int order, String label, String type, Long assigneeUserId) {
        ProcessNodeDef n = new ProcessNodeDef();
        n.setOrder(order);
        n.setLabel(label);
        n.setAssigneeType(type);
        n.setAssigneeUserId(assigneeUserId);
        return n;
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

    private LifecycleDtos.TransferVO toVo(TransferApplication app, boolean withNodes) {
        LifecycleDtos.TransferVO vo = new LifecycleDtos.TransferVO();
        vo.setId(app.getId());
        vo.setEmployeeId(app.getEmployeeId());
        try {
            vo.setEmployeeName(employeeLifecycleService.requireEmployee(app.getEmployeeId()).getName());
        } catch (Exception ignored) {
        }
        vo.setStatus(app.getStatus());
        vo.setFromDepartmentId(app.getFromDepartmentId());
        vo.setNewDepartmentId(app.getNewDepartmentId());
        vo.setNewPositionId(app.getNewPositionId());
        vo.setNewJobLevel(app.getNewJobLevel());
        vo.setNewManagerId(app.getNewManagerId());
        vo.setSalaryAdjustment(app.getSalaryAdjustment());
        vo.setEffectiveDate(app.getEffectiveDate() == null ? null : app.getEffectiveDate().toString());
        vo.setReason(app.getReason());
        vo.setInstanceId(app.getInstanceId());
        vo.setCreatedAt(app.getCreatedAt() == null ? null : DT.format(app.getCreatedAt()));

        if (withNodes) {
            List<ProcessNodeDef> defs = resolveProgressNodes(app);
            int current = 1;
            String instStatus = "PENDING";
            if (app.getInstanceId() != null) {
                ApprovalInstance inst = instanceMapper.selectById(app.getInstanceId());
                if (inst != null) {
                    current = inst.getCurrentNode() == null ? 1 : inst.getCurrentNode();
                    instStatus = inst.getStatus();
                }
            }
            List<LifecycleDtos.NodeProgressVO> nodes = new ArrayList<>();
            for (ProcessNodeDef d : defs) {
                LifecycleDtos.NodeProgressVO n = new LifecycleDtos.NodeProgressVO();
                n.setOrder(d.getOrder());
                n.setLabel(d.getLabel());
                if ("APPROVED".equalsIgnoreCase(instStatus)
                        || "APPROVED".equalsIgnoreCase(app.getStatus())
                        || "PENDING_EFFECT".equalsIgnoreCase(app.getStatus())) {
                    n.setStatus("finish");
                } else if ("REJECTED".equalsIgnoreCase(instStatus) || "REJECTED".equalsIgnoreCase(app.getStatus())) {
                    n.setStatus(d.getOrder() < current ? "finish" : (d.getOrder() == current ? "error" : "wait"));
                } else if (d.getOrder() < current) {
                    n.setStatus("finish");
                } else if (d.getOrder() == current) {
                    n.setStatus("process");
                } else {
                    n.setStatus("wait");
                }
                nodes.add(n);
            }
            vo.setNodes(nodes);
            final int currentNode = current;
            vo.setCurrentNodeLabel(defs.stream()
                    .filter(d -> d.getOrder() == currentNode)
                    .map(ProcessNodeDef::getLabel)
                    .findFirst()
                    .orElse(""));
        }
        return vo;
    }

    private List<ProcessNodeDef> resolveProgressNodes(TransferApplication app) {
        boolean withSalary = app.getSalaryAdjustment() != null;
        List<ProcessNodeDef> defs = new ArrayList<>();
        defs.add(node(1, "原部门确认", "DEPT_MANAGER", null));
        defs.add(node(2, "新部门接收", "NEW_DEPT_MANAGER", null));
        int order = 3;
        if (withSalary) {
            defs.add(node(order++, "财务调薪确认", "FINANCE_MANAGER", null));
        }
        defs.add(node(order, "HR 备案", "HR_STAFF", null));
        return defs;
    }

    private void requireHrOrAdmin() {
        LoginUser login = SecurityUtils.getLoginUser();
        if (login == null
                || (!login.hasRole("HR_STAFF") && !login.hasRole("SYS_ADMIN"))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅 HR/管理员可发起调岗");
        }
    }
}
