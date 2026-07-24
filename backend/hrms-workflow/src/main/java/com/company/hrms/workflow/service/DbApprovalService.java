package com.company.hrms.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.hrms.common.approval.ApprovalEngineService;
import com.company.hrms.common.approval.ApprovalStatusDTO;
import com.company.hrms.common.approval.CreateApprovalRequest;
import com.company.hrms.common.approval.CreateApprovalResult;
import com.company.hrms.common.approval.PendingApprovalTaskDTO;
import com.company.hrms.common.event.ApprovalCompletedEvent;
import com.company.hrms.common.enums.RoleCode;
import com.company.hrms.common.exception.BusinessException;
import com.company.hrms.common.exception.ErrorCode;
import com.company.hrms.common.exception.ForbiddenException;
import com.company.hrms.common.security.LoginUser;
import com.company.hrms.common.security.SecurityUtils;
import com.company.hrms.common.web.PageResult;
import com.company.hrms.employee.service.EmployeeLifecycleService;
import com.company.hrms.workflow.dto.ApprovalDtos;
import com.company.hrms.workflow.entity.ApprovalInstance;
import com.company.hrms.workflow.entity.ApprovalLog;
import com.company.hrms.workflow.entity.ApprovalTask;
import com.company.hrms.workflow.mapper.ApprovalInstanceMapper;
import com.company.hrms.workflow.mapper.ApprovalLogMapper;
import com.company.hrms.workflow.mapper.ApprovalTaskMapper;
import com.company.hrms.workflow.mapper.OrgLookupMapper;
import com.company.hrms.workflow.model.ProcessNodeDef;
import com.company.hrms.workflow.notify.ApprovalNotifyPublisher;
import com.company.hrms.workflow.support.AssigneeResolver;
import com.company.hrms.workflow.support.CurrentUserProvider;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 【DB 审批引擎】现网主路径：待办持久化在 MySQL，重启不丢。
 *   业务 Service 组装 ProcessNodeDef 链 → {@link #createInstance}
 *   写 approval_instance + 首节点 approval_task + 日志
 *   {@link #action}：校验归属/委托 → 写日志 → 通过推进下一节点或回调业务终态；驳回回调业务驳回
 *   撤回：实例结束 + 业务单回退（如入职回 draft）
 * 状态机只算「下一状态」；本类负责「实例/任务/日志」落库。单据状态不以 Redis 做主存储。
 */
@Service
public class DbApprovalService implements ApprovalEngineService {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final TypeReference<List<ProcessNodeDef>> NODES_TYPE = new TypeReference<>() {};
    private static final Pattern HANDOVER_ID_PATTERN = Pattern.compile("交接人#(\\d+)");

    private final ApprovalInstanceMapper instanceMapper;
    private final ApprovalTaskMapper taskMapper;
    private final ApprovalLogMapper logMapper;
    private final CurrentUserProvider currentUserProvider;
    private final AssigneeResolver assigneeResolver;
    private final LifecycleApprovalHandler lifecycleApprovalHandler;
    private final ApprovalNotifyPublisher notifyPublisher;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final DelegationService delegationService;
    private final ResignationService resignationService;
    private final OnboardingService onboardingService;
    private final RegularizationService regularizationService;
    private final TransferService transferService;
    private final OrgLookupMapper orgLookupMapper;
    private final EmployeeLifecycleService employeeLifecycleService;

    public DbApprovalService(ApprovalInstanceMapper instanceMapper,
                             ApprovalTaskMapper taskMapper,
                             ApprovalLogMapper logMapper,
                             CurrentUserProvider currentUserProvider,
                             @Lazy LifecycleApprovalHandler lifecycleApprovalHandler,
                             ApprovalNotifyPublisher notifyPublisher,
                             ApplicationEventPublisher eventPublisher,
                             ObjectMapper objectMapper,
                             DelegationService delegationService,
                             @Lazy ResignationService resignationService,
                             @Lazy OnboardingService onboardingService,
                             @Lazy RegularizationService regularizationService,
                             @Lazy TransferService transferService,
                             OrgLookupMapper orgLookupMapper,
                             EmployeeLifecycleService employeeLifecycleService) {
        this.instanceMapper = instanceMapper;
        this.taskMapper = taskMapper;
        this.logMapper = logMapper;
        this.currentUserProvider = currentUserProvider;
        this.assigneeResolver = new AssigneeResolver.DevAssigneeResolver();
        this.lifecycleApprovalHandler = lifecycleApprovalHandler;
        this.notifyPublisher = notifyPublisher;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
        this.delegationService = delegationService;
        this.resignationService = resignationService;
        this.onboardingService = onboardingService;
        this.regularizationService = regularizationService;
        this.transferService = transferService;
        this.orgLookupMapper = orgLookupMapper;
        this.employeeLifecycleService = employeeLifecycleService;
    }

    /**
     * 跨模块统一入口：根据 {@link CreateApprovalRequest} 创建审批实例并返回 instanceId。
     * 考勤/薪资等模块通过 {@link ApprovalEngineService} 接口调用；入转调离业务更常走下方 String 重载。
     * <p>
     * 【谁调用】{@code ApprovalController.startInstance}；
     * {@code LeaveService} / {@code OvertimeService} / {@code PunchService}（注入 ApprovalEngineService）
     * <p>
     * 【怎么实现】
     * <ol>
     *   <li>校验 processType、businessId；{@code AssigneeResolver.resolveNodes} 按流程类型解析默认节点链</li>
     *   <li>{@code enrichNodeAssignees}：把 SUPERVISOR/DEPT_MANAGER/HR 等占位解析为真实 userId</li>
     *   <li>组装 {@code InstanceDisplay}（标题、申请人、摘要）后委托 {@link #createInstance(String, String, Long, List, InstanceDisplay, Map)}</li>
     *   <li>返回 {@code CreateApprovalResult(instanceId, "PENDING")}</li>
     * </ol>
     */
    @Override
    @Transactional
    public CreateApprovalResult createInstance(CreateApprovalRequest request) {
        if (request == null || request.getProcessType() == null || request.getBusinessId() == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "processType/businessId 必填");
        }
        String processType = request.getProcessType().trim().toUpperCase(Locale.ROOT);
        List<ProcessNodeDef> nodes = AssigneeResolver.resolveNodes(processType, request.getFormData());
        enrichNodeAssignees(nodes, request);
        InstanceDisplay display = InstanceDisplay.of(
                request.getTitle() != null ? request.getTitle() : processType + "#" + request.getBusinessId(),
                request.getApplicantName() != null
                        ? request.getApplicantName()
                        : currentUserProvider.displayName(request.getApplicantId()),
                request.getApplicantDept(),
                request.getBusinessNo() != null
                        ? request.getBusinessNo()
                        : processType + "-" + request.getBusinessId());
        display.businessSummary = request.getBusinessSummary();

        Long instanceId = createInstance(
                processType,
                String.valueOf(request.getBusinessId()),
                request.getApplicantId(),
                nodes,
                display,
                request.getFormData());
        return new CreateApprovalResult(instanceId, "PENDING");
    }

    @Override
    public ApprovalStatusDTO getInstanceStatus(Long instanceId) {
        ApprovalInstance instance = instanceMapper.selectById(instanceId);
        if (instance == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "实例不存在");
        }
        ApprovalStatusDTO dto = new ApprovalStatusDTO();
        dto.setInstanceId(instanceId);
        dto.setStatus(instance.getStatus());
        dto.setCurrentNodeLabel(labelOf(nodesOf(instance), instance.getCurrentNode()));
        return dto;
    }

    @Override
    @Transactional
    public boolean withdrawInstance(Long instanceId, Long operatorId) {
        Long employeeId = null;
        try {
            var login = com.company.hrms.common.security.SecurityUtils.getLoginUser();
            if (login != null) {
                employeeId = login.getEmployeeId();
            }
        } catch (Exception ignored) {
            // ignore
        }
        doWithdrawInstance(instanceId.longValue(), operatorId.longValue(), employeeId);
        return true;
    }

    /**
     * 入转调离等业务侧创建审批实例的核心重载：写入 instance、首节点 task、SUBMIT 日志。
     * 节点链由业务 Service 预先组装（含 assigneeUserId），variables 供 AssigneeResolver 兜底解析。
     * <p>
     * 【谁调用】{@code OnboardingService.submit}、{@code RegularizationService.create}、
     * {@code TransferService.create}、{@code ResignationService.createResignation}；
     * 本类 {@link #createInstance(CreateApprovalRequest)} 内部委托
     * <p>
     * 【怎么实现】
     * <ol>
     *   <li>校验 nodes 非空；{@code ApprovalInstanceMapper.insert} 写 instance（status=PENDING，nodesJson 序列化）</li>
     *   <li>{@code createTask}：{@code AssigneeResolver} + {@code DelegationService.resolveAssignee} 确定 actualAssignee</li>
     *   <li>{@code ApprovalTaskMapper.insert} 首节点 PENDING 任务，slaDeadline=now+48h</li>
     *   <li>{@code ApprovalNotifyPublisher.scheduleRemind} 投递催办 MQ；{@code writeLog} 记录 SUBMIT</li>
     * </ol>
     */
    @Transactional
    public Long createInstance(String processType,
                               String businessKey,
                               Long initiatorId,
                               List<ProcessNodeDef> nodes,
                               InstanceDisplay display,
                               Map<String, Object> variables) {
        if (nodes == null || nodes.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "审批链为空");
        }
        LocalDateTime now = LocalDateTime.now();
        ApprovalInstance instance = new ApprovalInstance();
        instance.setProcessType(processType);
        instance.setBusinessKey(businessKey);
        instance.setStatus("PENDING");
        instance.setInitiatorId(initiatorId);
        instance.setCurrentNode(nodes.get(0).getOrder());
        instance.setCreatedAt(now);
        instance.setUpdatedAt(now);
        if (display == null) {
            display = new InstanceDisplay();
            display.title = processType + "#" + businessKey;
            display.applicantName = currentUserProvider.displayName(initiatorId);
        }
        instance.setTitle(display.title);
        instance.setApplicantName(display.applicantName);
        instance.setApplicantDept(display.applicantDept);
        instance.setBusinessNo(display.businessNo);
        instance.setBusinessSummary(display.businessSummary);
        try {
            instance.setNodesJson(objectMapper.writeValueAsString(nodes));
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "序列化审批链失败");
        }
        instanceMapper.insert(instance);

        ProcessNodeDef first = nodes.get(0);
        createTask(instance, first, variables, now);
        writeLog(instance.getId(), null, initiatorId, "SUBMIT", null, null, "PENDING",
                "提交 " + processTypeLabel(processType) + " 审批");
        return instance.getId();
    }

    public boolean ownsTask(long taskId) {
        return taskMapper.selectById(taskId) != null;
    }

    public boolean ownsInstance(long instanceId) {
        return instanceMapper.selectById(instanceId) != null;
    }

    /**
     * 【干什么】统计当前用户可见待办的 pending 数、今日已办数、超期数，供审批中心 KPI 卡片。
     * <p>
     * 【谁调用】{@code ApprovalController.taskStats}
     * <p>
     * 【怎么实现】
     * <ol>
     *   <li>{@code tasksVisibleToUser} 查本人 assignee/actualAssignee 及委托待办</li>
     *   <li>pending：status=PENDING 且 {@code isEffectiveAssignee} 为 true</li>
     *   <li>approvedToday：completedAt 为今天且 status=APPROVED/DONE</li>
     *   <li>overdueCount：PENDING 且 slaDeadline 已过期</li>
     * </ol>
     */
    public ApprovalDtos.TaskStatsVO taskStats(long userId) {
        List<ApprovalTask> mine = taskMapper.selectList(tasksVisibleToUser(userId));
        ApprovalDtos.TaskStatsVO vo = new ApprovalDtos.TaskStatsVO();
        vo.setPending(mine.stream()
                .filter(t -> "PENDING".equalsIgnoreCase(t.getStatus()))
                .filter(t -> isEffectiveAssignee(t, userId))
                .count());
        LocalDate today = LocalDate.now();
        vo.setApprovedToday(mine.stream()
                .filter(t -> t.getCompletedAt() != null && t.getCompletedAt().toLocalDate().equals(today))
                .filter(t -> "APPROVED".equalsIgnoreCase(t.getStatus()) || "DONE".equalsIgnoreCase(t.getStatus()))
                .count());
        vo.setOverdueCount(mine.stream()
                .filter(t -> "PENDING".equalsIgnoreCase(t.getStatus()))
                .filter(t -> isEffectiveAssignee(t, userId))
                .filter(t -> t.getSlaDeadline() != null && t.getSlaDeadline().isBefore(LocalDateTime.now()))
                .count());
        return vo;
    }

    /**
     * 与 {@link #taskStats} 的 pending 同口径，供工作台「待审批」KPI 复用，避免全库串数。
     */
    @Override
    public long countPendingTasksForAssignee(long userId) {
        return taskStats(userId).getPending();
    }

    /**
     * 供 AI 办事卡片等跨模块只读拉取：有效审批人的 PENDING 待办摘要。
     */
    @Override
    public List<PendingApprovalTaskDTO> listPendingTasksForAssignee(long userId, int limit) {
        int cap = Math.max(1, Math.min(limit <= 0 ? 10 : limit, 50));
        PageResult<ApprovalDtos.TaskListItemVO> page =
                listTasks(userId, "pending", null, null, 1, cap);
        List<PendingApprovalTaskDTO> out = new ArrayList<>();
        if (page == null || page.getList() == null) {
            return out;
        }
        for (ApprovalDtos.TaskListItemVO item : page.getList()) {
            PendingApprovalTaskDTO dto = new PendingApprovalTaskDTO();
            dto.setTaskId(item.getTaskId());
            dto.setInstanceId(item.getInstanceId());
            dto.setProcessType(item.getProcessType());
            dto.setTitle(item.getTitle());
            dto.setApplicantName(item.getApplicantName());
            dto.setCurrentNodeLabel(item.getCurrentNodeLabel());
            dto.setCreateTime(item.getCreateTime());
            dto.setBusinessSummary(item.getBusinessSummary());
            out.add(dto);
        }
        return out;
    }

    /**
     * 正式离职交接人选人：审批场景专用轻量搜索（非花名册）。
     * 允许 HR / 部门主管 / 财务经理 / 系统管理员。
     */
    public List<ApprovalDtos.HandoverCandidateVO> searchHandoverCandidates(String keyword) {
        LoginUser user = SecurityUtils.requireLoginUser();
        if (!(user.hasRole(RoleCode.HR_STAFF.name())
                || user.hasRole(RoleCode.DEPT_MANAGER.name())
                || user.hasRole(RoleCode.FINANCE_MANAGER.name())
                || user.hasRole(RoleCode.SYS_ADMIN.name()))) {
            throw new ForbiddenException();
        }
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        String kw = keyword.trim();
        if (kw.length() > 64) {
            kw = kw.substring(0, 64);
        }
        List<Map<String, Object>> rows = orgLookupMapper.searchHandoverCandidates(kw);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<ApprovalDtos.HandoverCandidateVO> list = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            ApprovalDtos.HandoverCandidateVO vo = new ApprovalDtos.HandoverCandidateVO();
            Object id = row.get("employeeId");
            if (id instanceof Number n) {
                vo.setEmployeeId(n.longValue());
            } else if (id != null) {
                try {
                    vo.setEmployeeId(Long.parseLong(String.valueOf(id)));
                } catch (NumberFormatException ignored) {
                    continue;
                }
            } else {
                continue;
            }
            vo.setName(row.get("name") == null ? null : String.valueOf(row.get("name")));
            vo.setEmpNo(row.get("empNo") == null ? null : String.valueOf(row.get("empNo")));
            vo.setDepartment(row.get("department") == null ? null : String.valueOf(row.get("department")));
            list.add(vo);
        }
        return list;
    }

    /**
     * 【干什么】分页查询当前用户的审批待办/已办列表，支持按 status、processType、keyword 筛选。
     * <p>
     * 【谁调用】{@code ApprovalController.listTasks}
     * <p>
     * 【怎么实现】
     * <ol>
     *   <li>{@code tasksVisibleToUser} + {@code ApprovalTaskMapper.selectList} 按 id 倒序</li>
     *   <li>status=pending 时过滤 {@code isEffectiveAssignee}（含委托解析）</li>
     *   <li>{@code toTaskItem} 关联 instance 组装标题/申请人/节点标签</li>
     *   <li>内存过滤 processType、keyword 后 {@code pageOf} 分页</li>
     * </ol>
     */
    public PageResult<ApprovalDtos.TaskListItemVO> listTasks(long userId, String status, String processType,
                                                             String keyword, int page, int pageSize) {
        LambdaQueryWrapper<ApprovalTask> q = tasksVisibleToUser(userId).orderByDesc(ApprovalTask::getId);
        if (status != null && !status.isBlank()) {
            if ("pending".equalsIgnoreCase(status)) {
                q.eq(ApprovalTask::getStatus, "PENDING");
            } else if ("done".equalsIgnoreCase(status)) {
                q.ne(ApprovalTask::getStatus, "PENDING");
            }
        }
        List<ApprovalDtos.TaskListItemVO> all = taskMapper.selectList(q).stream()
                .filter(t -> {
                    if (status != null && "pending".equalsIgnoreCase(status)) {
                        return isEffectiveAssignee(t, userId);
                    }
                    return true;
                })
                .map(t -> toTaskItem(t, userId))
                .filter(item -> processType == null || processType.isBlank()
                        || processType.equalsIgnoreCase(item.getProcessType()))
                .filter(item -> keyword == null || keyword.isBlank()
                        || (item.getTitle() != null && item.getTitle().contains(keyword)))
                .collect(Collectors.toList());
        return pageOf(all, page, pageSize);
    }

    /**
     * 【干什么】返回单条待办的完整详情：任务摘要、实例信息、业务 Detail、节点进度、时间线、可操作按钮。
     * <p>
     * 【谁调用】{@code ApprovalController.getTaskDetail}
     * <p>
     * 【怎么实现】
     * <ol>
     *   <li>{@code ApprovalTaskMapper.selectById} + {@code ApprovalInstanceMapper.selectById} 加载任务与实例</li>
     *   <li>{@code buildBusinessDetail} 按 processType 分发到各业务 Service（如 OnboardingService.businessDetail）</li>
     *   <li>{@code buildNodeProgress} / {@code buildTimeline} 组装进度与时间线</li>
     *   <li>PENDING 且 {@code isEffectiveAssignee} 时 actions 含 APPROVE/REJECT/FORWARD</li>
     * </ol>
     */
    public ApprovalDtos.TaskDetailVO getTaskDetail(long taskId, long userId) {
        ApprovalTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "任务不存在");
        }
        ApprovalInstance instance = instanceMapper.selectById(task.getInstanceId());
        if (instance == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "实例不存在");
        }
        InstanceDisplay display = displayOf(instance);
        List<ProcessNodeDef> nodes = nodesOf(instance);

        ApprovalDtos.TaskDetailVO detail = new ApprovalDtos.TaskDetailVO();
        ApprovalDtos.TaskBriefVO brief = new ApprovalDtos.TaskBriefVO();
        brief.setId(task.getId());
        brief.setStatus(apiStatus(task.getStatus()));
        brief.setCurrentNodeLabel(labelOf(nodes, task.getNodeOrder()));
        brief.setDueAt(fmt(task.getSlaDeadline()));
        detail.setTask(brief);

        ApprovalDtos.InstanceBriefVO ib = new ApprovalDtos.InstanceBriefVO();
        ib.setProcessType(instance.getProcessType());
        ib.setBusinessNo(display.businessNo != null ? display.businessNo : instance.getBusinessKey());
        ib.setInitiator(display.applicantName);
        ib.setCreatedAt(fmt(instance.getCreatedAt()));
        ib.setStatus(apiStatus(instance.getStatus()));
        detail.setInstance(ib);

        detail.setBusinessDetail(buildBusinessDetail(instance, display, task));

        int current = instance.getCurrentNode() == null ? 1 : instance.getCurrentNode();
        String instStatus = instance.getStatus() == null ? "" : instance.getStatus().toUpperCase(Locale.ROOT);
        detail.setNodes(buildNodeProgress(instance, nodes, current, instStatus));
        detail.setTimeline(buildTimeline(instance.getId()));
        List<String> actions = new ArrayList<>();
        if ("PENDING".equalsIgnoreCase(task.getStatus()) && isEffectiveAssignee(task, userId)) {
            actions.add("APPROVE");
            actions.add("REJECT");
            actions.add("FORWARD");
        }
        detail.setActions(actions);
        return detail;
    }

    /**
     * 【干什么】对指定待办任务发起催办，立即通知当前有效审批人并写 REMIND 日志。
     * <p>
     * 【谁调用】{@code ApprovalController.remindTask}
     * <p>
     * 【怎么实现】
     * <ol>
     *   <li>校验任务存在且 status=PENDING，否则抛 {@code APPROVAL_ALREADY_HANDLED}</li>
     *   <li>{@code effectiveAssigneeId} 解析实际审批人（含转交/委托）</li>
     *   <li>{@code ApprovalNotifyPublisher.publishImmediateRemind} 发即时催办（MQ 或日志降级）</li>
     *   <li>{@code writeLog} 记录 REMIND 动作</li>
     * </ol>
     */
    @Transactional
    public void remind(long taskId, long operatorId) {
        ApprovalTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "任务不存在");
        }
        if (!"PENDING".equalsIgnoreCase(task.getStatus())) {
            throw new BusinessException(ErrorCode.APPROVAL_ALREADY_HANDLED, "仅待办可催办");
        }
        long assignee = effectiveAssigneeId(task);
        notifyPublisher.publishImmediateRemind(taskId, assignee);
        writeLog(task.getInstanceId(), taskId, operatorId, "REMIND", "催办通知已记录",
                "PENDING", "PENDING", "催办");
    }

    /**
     * 【干什么】审批操作主链路：处理待办的 APPROVE / REJECT / FORWARD，推进节点或触发业务终态回调。
     * <p>
     * 【谁调用】{@code ApprovalController.action}；前端 {@code pages/admin/approval} → {@code postTaskAction}
     * <p>
     * 【怎么实现】
     * <ol>
     *   <li>校验 task 为 PENDING；{@code effectiveAssigneeId} 须等于 operator，否则 403</li>
     *   <li>REJECT 须 comment；FORWARD 须 targetUserId，更新 actualAssigneeId 并重排催办</li>
     *   <li>正式离职 RESIGNATION 第 1 岗 APPROVE 时可带 handoverEmployeeId → {@code ResignationService.confirmHandover}</li>
     *   <li>更新 task 状态 → {@code writeLog}；REJECT：instance=REJECTED → {@code LifecycleApprovalHandler.onRejected} + MQ 事件</li>
     *   <li>APPROVE：有下一节点则 {@code createTask} 推进 currentNode；否则 instance=APPROVED → {@code LifecycleApprovalHandler.onApproved} + MQ</li>
     * </ol>
     */
    @Transactional
    public void action(long taskId, long userId, ApprovalDtos.ActionRequest body) {
        if (body == null || body.getAction() == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "action 不能为空");
        }
        String action = body.getAction().trim().toUpperCase(Locale.ROOT);
        ApprovalTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "任务不存在");
        }
        if (!"PENDING".equalsIgnoreCase(task.getStatus())) {
            throw new BusinessException(ErrorCode.APPROVAL_ALREADY_HANDLED);
        }
        long effective = effectiveAssigneeId(task);
        if (userId != effective) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "非当前审批人");
        }
        if ("REJECT".equals(action) && (body.getComment() == null || body.getComment().isBlank())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "驳回须填写意见");
        }
        if ("FORWARD".equals(action) && body.getTargetUserId() == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "转交须指定目标用户");
        }

        ApprovalInstance instance = instanceMapper.selectById(task.getInstanceId());
        List<ProcessNodeDef> nodes = nodesOf(instance);
        LocalDateTime now = LocalDateTime.now();

        if ("FORWARD".equals(action)) {
            task.setActualAssigneeId(body.getTargetUserId());
            task.setComment(body.getComment());
            taskMapper.updateById(task);
            String targetName = currentUserProvider.displayName(body.getTargetUserId());
            writeLog(instance.getId(), taskId, userId, "FORWARD", body.getComment(),
                    "PENDING", "PENDING", "转交给" + targetName);
            notifyPublisher.scheduleRemind(taskId, body.getTargetUserId(), instance.getProcessType());
            return;
        }

        // 正式离职第一岗：部门负责人可选确认工作交接人
        if ("APPROVE".equals(action)
                && "RESIGNATION".equalsIgnoreCase(instance.getProcessType())
                && task.getNodeOrder() != null
                && task.getNodeOrder() == 1
                && body.getHandoverEmployeeId() != null) {
            try {
                Long appId = Long.parseLong(instance.getBusinessKey());
                resignationService.confirmHandover(appId, body.getHandoverEmployeeId());
            } catch (NumberFormatException ex) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "离职单关联异常");
            }
        }

        task.setStatus("APPROVE".equals(action) ? "APPROVED" : "REJECTED");
        task.setComment(body.getComment());
        task.setCompletedAt(now);
        taskMapper.updateById(task);
        writeLog(instance.getId(), taskId, userId, action, body.getComment(),
                "PENDING", task.getStatus(),
                actionDisplayText(action, body.getHandoverEmployeeId()));

        if ("REJECT".equals(action)) {
            instance.setStatus("REJECTED");
            instance.setUpdatedAt(now);
            instanceMapper.updateById(instance);
            lifecycleApprovalHandler.onRejected(instance.getProcessType(), instance.getBusinessKey());
            publishCompleted(instance, "REJECTED", body.getComment());
            return;
        }

        Optional<ProcessNodeDef> next = nodes.stream()
                .filter(n -> n.getOrder() > task.getNodeOrder())
                .min(Comparator.comparingInt(ProcessNodeDef::getOrder));
        if (next.isPresent()) {
            instance.setCurrentNode(next.get().getOrder());
            instance.setUpdatedAt(now);
            instanceMapper.updateById(instance);
            createTask(instance, next.get(), Map.of(), now);
        } else {
            instance.setStatus("APPROVED");
            instance.setUpdatedAt(now);
            instanceMapper.updateById(instance);
            lifecycleApprovalHandler.onApproved(instance.getProcessType(), instance.getBusinessKey());
            publishCompleted(instance, "APPROVED", body.getComment());
        }
    }

    /**
     * 【干什么】撤回审批实例（两参数重载）：取消 instance 及全部 PENDING 任务，并回调业务撤回逻辑。
     * <p>
     * 【谁调用】{@code DbApprovalService.withdrawInstance}（{@link ApprovalEngineService} 接口实现）；
     * {@code OnboardingService.withdraw} / {@code ResignationService.cancelMyRequest} 等经 withdrawInstance 间接调用
     * <p>
     * 【怎么实现】委托 {@link #doWithdrawInstance(long, long, Long)}，employeeId 传 null
     */
    @Transactional
    public void doWithdrawInstance(long instanceId, long userId) {
        doWithdrawInstance(instanceId, userId, null);
    }

    /**
     * 【干什么】撤回审批实例（三参数重载）：兼容 initiatorId 存 userId 或 employeeId 的场景。
     * 仅 PENDING 且无任何节点已审批时可撤；HR/管理员可代撤。
     * <p>
     * 【谁调用】{@code ApprovalController.withdrawInstance}；{@link #doWithdrawInstance(long, long)} 内部委托
     * <p>
     * 【怎么实现】
     * <ol>
     *   <li>校验 initiator 匹配 userId/employeeId 或 {@code isHrOrAdmin}</li>
     *   <li>instance.status 须 PENDING；任一 task 非 PENDING 则不可撤</li>
     *   <li>instance 与全部 PENDING task 置 CANCELLED；{@code writeLog} 记录 WITHDRAW</li>
     *   <li>{@code LifecycleApprovalHandler.onWithdrawn} 回调各业务 Service 回退单据状态</li>
     * </ol>
     */
    @Transactional
    public void doWithdrawInstance(long instanceId, long userId, Long employeeId) {
        ApprovalInstance instance = instanceMapper.selectById(instanceId);
        if (instance == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "实例不存在");
        }
        Long initiator = instance.getInitiatorId();
        boolean owner = initiator != null
                && (initiator.equals(userId) || (employeeId != null && initiator.equals(employeeId)));
        if (!owner && !isHrOrAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅发起人或 HR 可撤回");
        }
        if (!"PENDING".equalsIgnoreCase(instance.getStatus())) {
            throw new BusinessException(ErrorCode.APPROVAL_STATE_INVALID);
        }
        List<ApprovalTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<ApprovalTask>()
                .eq(ApprovalTask::getInstanceId, instanceId));
        boolean anyDone = tasks.stream().anyMatch(t -> !"PENDING".equalsIgnoreCase(t.getStatus()));
        if (anyDone) {
            throw new BusinessException(ErrorCode.APPROVAL_STATE_INVALID, "已有节点审批，不可撤回");
        }
        LocalDateTime now = LocalDateTime.now();
        instance.setStatus("CANCELLED");
        instance.setUpdatedAt(now);
        instanceMapper.updateById(instance);
        for (ApprovalTask t : tasks) {
            if ("PENDING".equalsIgnoreCase(t.getStatus())) {
                t.setStatus("CANCELLED");
                t.setCompletedAt(now);
                taskMapper.updateById(t);
            }
        }
        writeLog(instanceId, null, userId, "WITHDRAW", null, "PENDING", "CANCELLED", "撤回申请");
        lifecycleApprovalHandler.onWithdrawn(instance.getProcessType(), instance.getBusinessKey());
        publishCompleted(instance, "CANCELLED", "撤回申请");
    }

    /**
     * 【干什么】分页查询当前用户发起的审批实例列表（我发起的 Tab）。
     * <p>
     * 【谁调用】{@code ApprovalController.listMyInstances}
     * <p>
     * 【怎么实现】
     * <ol>
     *   <li>{@code ApprovalInstanceMapper.selectList} 按 initiatorId=userId 倒序</li>
     *   <li>{@code displayOf} 实时解析申请人姓名；{@code labelOf} 取当前节点标签</li>
     *   <li>内存 {@code pageOf} 分页返回</li>
     * </ol>
     */
    public PageResult<ApprovalDtos.InstanceListItemVO> listMyInstances(long userId, int page, int pageSize) {
        List<ApprovalInstance> list = instanceMapper.selectList(new LambdaQueryWrapper<ApprovalInstance>()
                .eq(ApprovalInstance::getInitiatorId, userId)
                .orderByDesc(ApprovalInstance::getId));
        List<ApprovalDtos.InstanceListItemVO> items = list.stream().map(inst -> {
            InstanceDisplay d = displayOf(inst);
            ApprovalDtos.InstanceListItemVO vo = new ApprovalDtos.InstanceListItemVO();
            vo.setInstanceId(inst.getId());
            vo.setTaskId(0L);
            vo.setProcessType(inst.getProcessType());
            vo.setTitle(d.title);
            vo.setApplicantName(d.applicantName);
            vo.setApplicantDept(d.applicantDept);
            vo.setCurrentNodeLabel(labelOf(nodesOf(inst), inst.getCurrentNode()));
            vo.setCreateTime(fmt(inst.getCreatedAt()));
            vo.setStatus(apiStatus(inst.getStatus()));
            return vo;
        }).collect(Collectors.toList());
        return pageOf(items, page, pageSize);
    }

    /**
     * 【干什么】发起人（或 HR）查看审批实例进度：节点状态、时间线、业务 Detail，不含待办操作按钮。
     * <p>
     * 【谁调用】{@code ApprovalController.getInstanceDetail}；入职/请假等业务页查看审批进度
     * <p>
     * 【怎么实现】
     * <ol>
     *   <li>校验 initiator 匹配 userId/employeeId 或 {@code isHrOrAdmin}</li>
     *   <li>{@code buildNodeProgress} + {@code buildTimeline} 组装进度与时间线</li>
     *   <li>{@code buildBusinessDetail}（task=null）按 processType 拉取业务详情</li>
     * </ol>
     */
    public ApprovalDtos.InstanceDetailVO getInstanceDetail(long instanceId, long userId, Long employeeId) {
        ApprovalInstance instance = instanceMapper.selectById(instanceId);
        if (instance == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "审批实例不存在");
        }
        Long initiator = instance.getInitiatorId();
        boolean owner = initiator != null
                && (initiator.equals(userId) || (employeeId != null && initiator.equals(employeeId)));
        if (!owner && !isHrOrAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看该审批进度");
        }

        InstanceDisplay display = displayOf(instance);
        List<ProcessNodeDef> nodeDefs = nodesOf(instance);
        int current = instance.getCurrentNode() == null ? 1 : instance.getCurrentNode();
        String instStatus = instance.getStatus() == null ? "" : instance.getStatus().toUpperCase(Locale.ROOT);

        List<ApprovalDtos.NodeProgressVO> nodes = buildNodeProgress(instance, nodeDefs, current, instStatus);

        ApprovalDtos.InstanceDetailVO vo = new ApprovalDtos.InstanceDetailVO();
        vo.setInstanceId(instance.getId());
        vo.setProcessType(instance.getProcessType());
        vo.setTitle(display.title);
        vo.setStatus(apiStatus(instance.getStatus()));
        vo.setCurrentNodeLabel(labelOf(nodeDefs, current));
        vo.setCreatedAt(fmt(instance.getCreatedAt()));
        vo.setNodes(nodes);
        vo.setTimeline(buildTimeline(instance.getId()));
        vo.setBusinessDetail(buildBusinessDetail(instance, display, null));
        return vo;
    }

    /**
     * 组装审批业务详情（离职/入职/转正/考勤等）。task 为空时不要求交接确认（发起人只读视角）。
     */
    private Map<String, Object> buildBusinessDetail(ApprovalInstance instance, InstanceDisplay display,
                                                    ApprovalTask task) {
        Map<String, Object> biz = new HashMap<>();
        biz.put("businessKey", instance.getBusinessKey());
        biz.put("title", display.title);
        biz.put("businessSummary", display.businessSummary);
        if ("RESIGNATION".equalsIgnoreCase(instance.getProcessType())) {
            try {
                Long appId = parseBusinessId(instance.getBusinessKey());
                biz.putAll(resignationService.resignationBusinessDetail(appId));
                boolean needHandover = task != null
                        && task.getNodeOrder() != null
                        && task.getNodeOrder() == 1
                        && "PENDING".equalsIgnoreCase(task.getStatus());
                biz.put("needHandoverConfirm", needHandover);
            } catch (Exception ignored) {
                biz.put("needHandoverConfirm", false);
            }
        } else if ("RESIGNATION_REQUEST".equalsIgnoreCase(instance.getProcessType())) {
            try {
                Long requestId = parseBusinessId(instance.getBusinessKey());
                biz.putAll(resignationService.requestBusinessDetail(requestId));
            } catch (Exception ignored) {
                // ignore
            }
        } else if ("ONBOARDING".equalsIgnoreCase(instance.getProcessType())) {
            try {
                Long appId = parseBusinessId(instance.getBusinessKey());
                biz.putAll(onboardingService.businessDetail(appId));
            } catch (Exception ignored) {
                // ignore
            }
        } else if ("REGULARIZATION".equalsIgnoreCase(instance.getProcessType())) {
            try {
                Long appId = parseBusinessId(instance.getBusinessKey());
                biz.putAll(regularizationService.businessDetail(appId));
            } catch (Exception ignored) {
                // ignore
            }
        } else if ("TRANSFER".equalsIgnoreCase(instance.getProcessType())) {
            try {
                Long appId = parseBusinessId(instance.getBusinessKey());
                biz.putAll(transferService.businessDetail(appId));
            } catch (Exception ignored) {
                // ignore
            }
        } else if ("MOBILE_CHANGE".equalsIgnoreCase(instance.getProcessType())) {
            try {
                Long appId = parseBusinessId(instance.getBusinessKey());
                Map<String, Object> mobile = orgLookupMapper.selectMobileChangeBrief(appId);
                if (mobile != null) {
                    biz.putAll(mobile);
                }
            } catch (Exception ignored) {
                // ignore
            }
        } else if ("LEAVE".equalsIgnoreCase(instance.getProcessType())
                || "PAYROLL_BATCH".equalsIgnoreCase(instance.getProcessType())
                || "PAYROLL".equalsIgnoreCase(instance.getProcessType())) {
            if ("LEAVE".equalsIgnoreCase(instance.getProcessType())) {
                biz.put("type", "leave");
                try {
                    Long appId = parseBusinessId(instance.getBusinessKey());
                    Map<String, Object> leave = orgLookupMapper.selectLeaveBrief(appId);
                    if (leave != null) {
                        biz.putAll(leave);
                    }
                } catch (Exception ignored) {
                    // ignore
                }
                // 库表无数据时回退解析摘要
                if (!biz.containsKey("leaveType")) {
                    String summary = display.businessSummary;
                    if (summary != null) {
                        String[] parts = summary.split(" ");
                        if (parts.length >= 2) {
                            biz.put("leaveType", parts[0]);
                            biz.put("days", parts[1].replace("天", ""));
                        }
                    }
                }
            } else {
                biz.put("type", "payroll");
            }
        } else if ("OVERTIME".equalsIgnoreCase(instance.getProcessType())) {
            biz.put("type", "overtime");
            String summary = display.businessSummary;
            if (summary != null) {
                String[] parts = summary.split(" ");
                if (parts.length >= 2) {
                    biz.put("overtimeDate", parts[0]);
                    biz.put("hours", parts[1].replace("h", ""));
                }
            }
        } else if ("MAKEUP".equalsIgnoreCase(instance.getProcessType())
                || "PUNCH_FIX".equalsIgnoreCase(instance.getProcessType())) {
            biz.put("type", "makeup");
            String summary = display.businessSummary;
            if (summary != null) {
                String[] parts = summary.split(" ");
                if (parts.length >= 2) {
                    biz.put("makeupDate", parts[0]);
                    biz.put("punchType", parts[1]);
                }
            }
        }
        return biz;
    }

    private void publishCompleted(ApprovalInstance instance, String result, String comment) {
        Long businessId = parseBusinessId(instance.getBusinessKey());
        eventPublisher.publishEvent(new ApprovalCompletedEvent(
                this,
                instance.getProcessType(),
                instance.getId(),
                businessId,
                result,
                instance.getInitiatorId(),
                comment));
        notifyPublisher.publishApprovalCompleted(
                instance.getProcessType(),
                instance.getId(),
                businessId,
                result,
                instance.getInitiatorId(),
                comment);
    }

    /**
     * 考勤类等通过 {@link ApprovalEngineService#createInstance} 发起的流程：
     * 把 SUPERVISOR/DEPT_MANAGER/HR 等占位类型解析为真实 userId，避免落成 DevAssignees 桩账号。
     * 入转调离业务侧已自行写入 assigneeUserId 的节点不会被覆盖。
     */
    private void enrichNodeAssignees(List<ProcessNodeDef> nodes, CreateApprovalRequest request) {
        if (nodes == null || nodes.isEmpty() || request == null) {
            return;
        }
        Long employeeId = extractLong(request.getFormData(), "employeeId");
        Long applicantId = request.getApplicantId();
        for (ProcessNodeDef node : nodes) {
            if (node == null || node.getAssigneeUserId() != null) {
                continue;
            }
            String type = node.getAssigneeType() == null ? "" : node.getAssigneeType().trim().toUpperCase(Locale.ROOT);
            switch (type) {
                case "SUPERVISOR", "DEPT_MANAGER" -> {
                    if (employeeId != null) {
                        node.setAssigneeUserId(employeeLifecycleService.resolveDeptManagerUserId(employeeId));
                    }
                }
                case "NEW_DEPT_MANAGER" -> {
                    Long newDeptId = extractLong(request.getFormData(), "newDepartmentId", "toDepartmentId");
                    if (newDeptId != null) {
                        node.setAssigneeUserId(employeeLifecycleService.resolveDeptHeadUserIdByDeptId(newDeptId));
                    }
                }
                case "HR_STAFF", "ROLE" ->
                        node.setAssigneeUserId(employeeLifecycleService.resolveHrApproverUserId(applicantId));
                case "FINANCE", "FINANCE_MANAGER" ->
                        node.setAssigneeUserId(employeeLifecycleService.resolveFinanceApproverUserId(applicantId));
                default -> {
                    // 保留 DevAssigneeResolver 兜底
                }
            }
        }
    }

    private static Long extractLong(Map<String, Object> form, String... keys) {
        if (form == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            Object v = form.get(key);
            if (v instanceof Number n) {
                return n.longValue();
            }
            if (v != null && !v.toString().isBlank()) {
                try {
                    return Long.parseLong(v.toString().trim());
                } catch (NumberFormatException ignored) {
                    // next key
                }
            }
        }
        return null;
    }

    private void createTask(ApprovalInstance instance, ProcessNodeDef node,
                            Map<String, Object> variables, LocalDateTime now) {
        long configuredAssignee = assigneeResolver.resolve(node, variables == null ? Map.of() : variables);
        long actualAssignee = delegationService.resolveAssignee(configuredAssignee, now.toLocalDate());
        ApprovalTask task = new ApprovalTask();
        task.setInstanceId(instance.getId());
        task.setNodeOrder(node.getOrder());
        task.setAssigneeId(configuredAssignee);
        if (actualAssignee != configuredAssignee) {
            task.setActualAssigneeId(actualAssignee);
        }
        task.setStatus("PENDING");
        task.setOverdue(0);
        task.setSlaDeadline(now.plusHours(48));
        taskMapper.insert(task);
        notifyPublisher.scheduleRemind(task.getId(), actualAssignee, instance.getProcessType());
    }

    /** 可见：本人作为原审批人/实际审批人，或委托人已把待办委托给本人 */
    private LambdaQueryWrapper<ApprovalTask> tasksVisibleToUser(long userId) {
        List<Long> delegatorIds = delegationService.findActiveDelegatorIdsFor(userId);
        return new LambdaQueryWrapper<ApprovalTask>()
                .and(w -> {
                    w.eq(ApprovalTask::getAssigneeId, userId)
                            .or()
                            .eq(ApprovalTask::getActualAssigneeId, userId);
                    if (!delegatorIds.isEmpty()) {
                        w.or(sub -> sub.in(ApprovalTask::getAssigneeId, delegatorIds)
                                .eq(ApprovalTask::getStatus, "PENDING")
                                .and(a -> a.isNull(ApprovalTask::getActualAssigneeId)
                                        .or()
                                        .eq(ApprovalTask::getActualAssigneeId, userId)));
                    }
                });
    }

    /** 待办可操作人：有 actual 时仅 actual；否则按委托规则动态解析 */
    private boolean isEffectiveAssignee(ApprovalTask task, long userId) {
        return effectiveAssigneeId(task) == userId;
    }

    private long effectiveAssigneeId(ApprovalTask task) {
        if (task.getActualAssigneeId() != null) {
            return task.getActualAssigneeId();
        }
        if (task.getAssigneeId() == null) {
            return -1L;
        }
        return delegationService.resolveAssignee(task.getAssigneeId());
    }

    private void writeLog(Long instanceId, Long taskId, Long operatorId, String action, String comment,
                          String from, String to, String displayText) {
        ApprovalLog log = new ApprovalLog();
        log.setInstanceId(instanceId);
        log.setTaskId(taskId);
        log.setOperatorId(operatorId);
        log.setAction(action);
        log.setComment(comment);
        log.setFromStatus(from);
        log.setToStatus(to);
        log.setDisplayText(displayText);
        log.setCreatedAt(LocalDateTime.now());
        logMapper.insert(log);
    }

    private List<ApprovalDtos.TimelineItemVO> buildTimeline(Long instanceId) {
        return logMapper.selectList(new LambdaQueryWrapper<ApprovalLog>()
                        .eq(ApprovalLog::getInstanceId, instanceId)
                        .orderByAsc(ApprovalLog::getId))
                .stream()
                .map(l -> {
                    ApprovalDtos.TimelineItemVO t = new ApprovalDtos.TimelineItemVO();
                    t.setNode(l.getAction());
                    t.setAssignee(currentUserProvider.displayName(l.getOperatorId() == null ? 0 : l.getOperatorId()));
                    t.setAction(l.getAction());
                    t.setComment(l.getComment());
                    t.setTime(fmt(l.getCreatedAt()));
                    String display = enrichHandoverDisplayText(l.getDisplayText());
                    if (display != null && display.startsWith("转交给用户")) {
                        // 兼容历史日志：转交给用户{id} → 转交给{姓名}
                        try {
                            long uid = Long.parseLong(display.substring("转交给用户".length()).trim());
                            display = "转交给" + currentUserProvider.displayName(uid);
                        } catch (Exception ignored) {
                            // keep original
                        }
                    }
                    t.setDisplayText(display);
                    return t;
                }).collect(Collectors.toList());
    }

    /** 流程节点 + 任务派单人/转交人/任务状态 */
    private List<ApprovalDtos.NodeProgressVO> buildNodeProgress(ApprovalInstance instance,
                                                               List<ProcessNodeDef> nodeDefs,
                                                               int current,
                                                               String instStatus) {
        List<ApprovalTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<ApprovalTask>()
                .eq(ApprovalTask::getInstanceId, instance.getId())
                .orderByAsc(ApprovalTask::getId));
        Map<Integer, ApprovalTask> latestByOrder = new HashMap<>();
        if (tasks != null) {
            for (ApprovalTask t : tasks) {
                if (t.getNodeOrder() != null) {
                    latestByOrder.put(t.getNodeOrder(), t);
                }
            }
        }

        List<ApprovalDtos.NodeProgressVO> nodes = new ArrayList<>();
        for (ProcessNodeDef n : nodeDefs) {
            ApprovalDtos.NodeProgressVO np = new ApprovalDtos.NodeProgressVO();
            np.setOrder(n.getOrder());
            np.setLabel(n.getLabel());
            if ("CANCELLED".equals(instStatus) || "WITHDRAWN".equals(instStatus)) {
                np.setState(n.getOrder() < current ? "done" : "cancelled");
            } else if ("APPROVED".equals(instStatus) || "COMPLETED".equals(instStatus)) {
                np.setState("done");
            } else if ("REJECTED".equals(instStatus)) {
                np.setState(n.getOrder() < current ? "done" : (n.getOrder() == current ? "current" : "pending"));
            } else if (n.getOrder() < current) {
                np.setState("done");
            } else if (n.getOrder() == current) {
                np.setState("current");
            } else {
                np.setState("pending");
            }

            if (n.getAssigneeUserId() != null && n.getAssigneeUserId() > 0) {
                np.setAssigneeName(currentUserProvider.displayName(n.getAssigneeUserId()));
            }
            ApprovalTask task = latestByOrder.get(n.getOrder());
            if (task != null) {
                np.setTaskStatus(apiStatus(task.getStatus()));
                if (task.getAssigneeId() != null && task.getAssigneeId() > 0) {
                    np.setAssigneeName(currentUserProvider.displayName(task.getAssigneeId()));
                }
                if (task.getActualAssigneeId() != null
                        && task.getActualAssigneeId() > 0
                        && !Objects.equals(task.getActualAssigneeId(), task.getAssigneeId())) {
                    np.setActualAssigneeName(currentUserProvider.displayName(task.getActualAssigneeId()));
                }
            }
            nodes.add(np);
        }
        return nodes;
    }

    private ApprovalDtos.TaskListItemVO toTaskItem(ApprovalTask task, long userId) {
        ApprovalInstance instance = instanceMapper.selectById(task.getInstanceId());
        InstanceDisplay d = instance == null
                ? new InstanceDisplay()
                : displayOf(instance);
        List<ProcessNodeDef> nodes = instance == null ? List.of() : nodesOf(instance);
        ApprovalDtos.TaskListItemVO vo = new ApprovalDtos.TaskListItemVO();
        vo.setTaskId(task.getId());
        vo.setInstanceId(task.getInstanceId());
        vo.setProcessType(instance != null ? instance.getProcessType() : null);
        vo.setTitle(d.title);
        vo.setApplicantName(d.applicantName);
        vo.setApplicantDept(d.applicantDept);
        vo.setBusinessNo(d.businessNo);
        vo.setBusinessSummary(d.businessSummary);
        vo.setCurrentNodeLabel(labelOf(nodes, task.getNodeOrder()));
        vo.setCreateTime(instance != null ? fmt(instance.getCreatedAt()) : null);
        vo.setDueAt(fmt(task.getSlaDeadline()));
        vo.setStatus(apiStatus(task.getStatus()));
        return vo;
    }

    private List<ProcessNodeDef> nodesOf(ApprovalInstance instance) {
        if (instance.getNodesJson() != null && !instance.getNodesJson().isBlank()) {
            try {
                return objectMapper.readValue(instance.getNodesJson(), NODES_TYPE);
            } catch (Exception ignored) {
                // fall through
            }
        }
        return AssigneeResolver.resolveNodes(instance.getProcessType(), Map.of());
    }

    private static Long parseBusinessId(String businessKey) {
        if (businessKey == null || businessKey.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(businessKey.trim());
        } catch (NumberFormatException e) {
            int idx = businessKey.lastIndexOf(':');
            if (idx >= 0 && idx + 1 < businessKey.length()) {
                try {
                    return Long.parseLong(businessKey.substring(idx + 1).trim());
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
            return null;
        }
    }

    private static boolean isHrOrAdmin() {
        try {
            LoginUser login = SecurityUtils.getLoginUser();
            if (login == null) {
                return false;
            }
            return login.hasRole(RoleCode.HR_STAFF.name())
                    || login.hasRole(RoleCode.SYS_ADMIN.name());
        } catch (Exception ignored) {
            return false;
        }
    }

    /** 列表/详情展示名实时解析，避免库里历史「用户1」桩文案残留 */
    private InstanceDisplay displayOf(ApprovalInstance inst) {
        InstanceDisplay d = InstanceDisplay.from(inst);
        if (inst.getInitiatorId() != null && inst.getInitiatorId() > 0) {
            d.applicantName = currentUserProvider.displayName(inst.getInitiatorId());
        }
        return d;
    }

    private static String labelOf(List<ProcessNodeDef> nodes, Integer order) {
        if (order == null) {
            return "";
        }
        return nodes.stream()
                .filter(n -> n.getOrder() == order)
                .map(ProcessNodeDef::getLabel)
                .findFirst()
                .orElse("节点" + order);
    }

    private static String processTypeLabel(String processType) {
        if (processType == null || processType.isBlank()) {
            return "审批";
        }
        return switch (processType.trim().toUpperCase(Locale.ROOT)) {
            case "ONBOARDING" -> "入职";
            case "REGULARIZATION" -> "转正";
            case "TRANSFER" -> "调岗";
            case "RESIGNATION" -> "离职";
            case "RESIGNATION_REQUEST" -> "离职申请";
            case "MOBILE_CHANGE" -> "手机号变更";
            case "LEAVE" -> "请假";
            case "OVERTIME" -> "加班";
            case "MAKEUP", "PUNCH_FIX" -> "补卡";
            case "PAYROLL", "PAYROLL_BATCH" -> "薪资核算";
            default -> processType;
        };
    }

    private String actionDisplayText(String action, Long handoverEmployeeId) {
        String label = switch (action == null ? "" : action.trim().toUpperCase(Locale.ROOT)) {
            case "SUBMIT" -> "提交";
            case "APPROVE" -> "同意";
            case "REJECT" -> "驳回";
            case "FORWARD" -> "转交";
            case "WITHDRAW" -> "撤回";
            case "REMIND" -> "催办";
            default -> action == null ? "" : action;
        };
        if ("APPROVE".equalsIgnoreCase(action) && handoverEmployeeId != null) {
            return label + "（交接人" + formatEmployeePositionName(handoverEmployeeId) + "）";
        }
        return label;
    }

    /** 职位在前、姓名紧跟；查不到时回退为 #employeeId */
    private String formatEmployeePositionName(Long employeeId) {
        if (employeeId == null) {
            return "";
        }
        try {
            Map<String, Object> row = orgLookupMapper.selectEmployeeNameAndPositionByEmployeeId(employeeId);
            if (row != null) {
                String name = row.get("name") == null ? "" : String.valueOf(row.get("name")).trim();
                String position = row.get("positionName") == null ? "" : String.valueOf(row.get("positionName")).trim();
                if (!name.isEmpty() && !position.isEmpty()) {
                    return position + name;
                }
                if (!name.isEmpty()) {
                    return name;
                }
            }
        } catch (Exception ignored) {
            // fall through
        }
        return "#" + employeeId;
    }

    /** 历史日志「交接人#106」回显时替换为职位+姓名 */
    private String enrichHandoverDisplayText(String displayText) {
        if (displayText == null || displayText.isBlank() || !displayText.contains("交接人#")) {
            return displayText;
        }
        Matcher m = HANDOVER_ID_PATTERN.matcher(displayText);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            Long empId;
            try {
                empId = Long.parseLong(m.group(1));
            } catch (NumberFormatException ex) {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
                continue;
            }
            String label = formatEmployeePositionName(empId);
            m.appendReplacement(sb, Matcher.quoteReplacement("交接人" + label));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String apiStatus(String db) {
        if (db == null) {
            return null;
        }
        return switch (db.toUpperCase(Locale.ROOT)) {
            case "PENDING" -> "pending";
            case "APPROVED" -> "approved";
            case "REJECTED" -> "rejected";
            case "CANCELLED" -> "cancelled";
            default -> db.toLowerCase(Locale.ROOT);
        };
    }

    private static String fmt(LocalDateTime t) {
        return t == null ? null : DT.format(t);
    }

    private static <T> PageResult<T> pageOf(List<T> all, int page, int pageSize) {
        int p = page <= 0 ? 1 : page;
        int size = pageSize <= 0 ? 20 : Math.min(pageSize, 100);
        int from = Math.min((p - 1) * size, all.size());
        int to = Math.min(from + size, all.size());
        return PageResult.of(all.subList(from, to), all.size(), p, size);
    }

    public static class InstanceDisplay {
        public String title;
        public String applicantName;
        public String applicantDept;
        public String businessNo;
        public String businessSummary;

        public static InstanceDisplay of(String title, String applicantName, String applicantDept, String businessNo) {
            InstanceDisplay d = new InstanceDisplay();
            d.title = title;
            d.applicantName = applicantName;
            d.applicantDept = applicantDept;
            d.businessNo = businessNo;
            return d;
        }

        static InstanceDisplay from(ApprovalInstance inst) {
            InstanceDisplay d = of(
                    inst.getTitle() != null ? inst.getTitle() : inst.getProcessType() + "#" + inst.getBusinessKey(),
                    inst.getApplicantName() != null ? inst.getApplicantName() : "用户" + inst.getInitiatorId(),
                    inst.getApplicantDept(),
                    inst.getBusinessNo() != null ? inst.getBusinessNo() : inst.getBusinessKey());
            d.businessSummary = inst.getBusinessSummary();
            return d;
        }
    }
}
