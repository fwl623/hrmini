package com.company.hrms.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.hrms.common.approval.ApprovalEngineService;
import com.company.hrms.common.approval.ApprovalStatusDTO;
import com.company.hrms.common.approval.CreateApprovalRequest;
import com.company.hrms.common.approval.CreateApprovalResult;
import com.company.hrms.common.event.ApprovalCompletedEvent;
import com.company.hrms.common.exception.BusinessException;
import com.company.hrms.common.exception.ErrorCode;
import com.company.hrms.common.web.PageResult;
import com.company.hrms.workflow.dto.ApprovalDtos;
import com.company.hrms.workflow.entity.ApprovalInstance;
import com.company.hrms.workflow.entity.ApprovalLog;
import com.company.hrms.workflow.entity.ApprovalTask;
import com.company.hrms.workflow.mapper.ApprovalInstanceMapper;
import com.company.hrms.workflow.mapper.ApprovalLogMapper;
import com.company.hrms.workflow.mapper.ApprovalTaskMapper;
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
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 统一落库审批引擎（唯一实现）。
 */
@Service
public class DbApprovalService implements ApprovalEngineService {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final TypeReference<List<ProcessNodeDef>> NODES_TYPE = new TypeReference<>() {};

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

    public DbApprovalService(ApprovalInstanceMapper instanceMapper,
                             ApprovalTaskMapper taskMapper,
                             ApprovalLogMapper logMapper,
                             CurrentUserProvider currentUserProvider,
                             @Lazy LifecycleApprovalHandler lifecycleApprovalHandler,
                             ApprovalNotifyPublisher notifyPublisher,
                             ApplicationEventPublisher eventPublisher,
                             ObjectMapper objectMapper,
                             DelegationService delegationService,
                             @Lazy ResignationService resignationService) {
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
    }

    @Override
    @Transactional
    public CreateApprovalResult createInstance(CreateApprovalRequest request) {
        if (request == null || request.getProcessType() == null || request.getBusinessId() == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "processType/businessId 必填");
        }
        String processType = request.getProcessType().trim().toUpperCase(Locale.ROOT);
        List<ProcessNodeDef> nodes = AssigneeResolver.resolveNodes(processType, request.getFormData());
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
                "提交 " + processType + " 审批");
        return instance.getId();
    }

    public boolean ownsTask(long taskId) {
        return taskMapper.selectById(taskId) != null;
    }

    public boolean ownsInstance(long instanceId) {
        return instanceMapper.selectById(instanceId) != null;
    }

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

    public ApprovalDtos.TaskDetailVO getTaskDetail(long taskId, long userId) {
        ApprovalTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "任务不存在");
        }
        ApprovalInstance instance = instanceMapper.selectById(task.getInstanceId());
        if (instance == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "实例不存在");
        }
        InstanceDisplay display = InstanceDisplay.from(instance);
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

        Map<String, Object> biz = new HashMap<>();
        biz.put("businessKey", instance.getBusinessKey());
        biz.put("title", display.title);
        biz.put("businessSummary", display.businessSummary);
        if ("RESIGNATION".equalsIgnoreCase(instance.getProcessType())) {
            try {
                Long appId = Long.parseLong(instance.getBusinessKey());
                biz.putAll(resignationService.resignationBusinessDetail(appId));
                boolean needHandover = task.getNodeOrder() != null && task.getNodeOrder() == 1
                        && "PENDING".equalsIgnoreCase(task.getStatus());
                biz.put("needHandoverConfirm", needHandover);
            } catch (Exception ignored) {
                biz.put("needHandoverConfirm", false);
            }
        }
        detail.setBusinessDetail(biz);

        detail.setTimeline(buildTimeline(instance.getId()));
        List<String> actions = new ArrayList<>();
        if ("PENDING".equalsIgnoreCase(task.getStatus())
                && (userId == task.getAssigneeId()
                || (task.getActualAssigneeId() != null && userId == task.getActualAssigneeId()))) {
            actions.add("APPROVE");
            actions.add("REJECT");
            actions.add("FORWARD");
        }
        detail.setActions(actions);
        return detail;
    }

    @Transactional
    public void remind(long taskId, long operatorId) {
        ApprovalTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "任务不存在");
        }
        if (!"PENDING".equalsIgnoreCase(task.getStatus())) {
            throw new BusinessException(ErrorCode.APPROVAL_ALREADY_HANDLED, "仅待办可催办");
        }
        long assignee = task.getActualAssigneeId() != null ? task.getActualAssigneeId() : task.getAssigneeId();
        notifyPublisher.publishImmediateRemind(taskId, assignee);
        writeLog(task.getInstanceId(), taskId, operatorId, "REMIND", "催办通知已记录",
                "PENDING", "PENDING", "催办");
    }

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
        long effective = task.getActualAssigneeId() != null ? task.getActualAssigneeId() : task.getAssigneeId();
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
            writeLog(instance.getId(), taskId, userId, "FORWARD", body.getComment(),
                    "PENDING", "PENDING", "转交给用户" + body.getTargetUserId());
            notifyPublisher.scheduleRemind(taskId, body.getTargetUserId(), instance.getProcessType());
            return;
        }

        // 正式离职第一岗：部门负责人须确认工作交接人
        if ("APPROVE".equals(action)
                && "RESIGNATION".equalsIgnoreCase(instance.getProcessType())
                && task.getNodeOrder() != null
                && task.getNodeOrder() == 1) {
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
                "APPROVE".equals(action) && body.getHandoverEmployeeId() != null
                        ? action + "（交接人#" + body.getHandoverEmployeeId() + "）"
                        : action);

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

    @Transactional
    public void doWithdrawInstance(long instanceId, long userId) {
        doWithdrawInstance(instanceId, userId, null);
    }

    /**
     * 撤回审批实例。initiatorId 可能存 userId 或 employeeId（请假等业务用 employeeId）。
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
        if (!owner) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅发起人可撤回");
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
    }

    public PageResult<ApprovalDtos.InstanceListItemVO> listMyInstances(long userId, int page, int pageSize) {
        List<ApprovalInstance> list = instanceMapper.selectList(new LambdaQueryWrapper<ApprovalInstance>()
                .eq(ApprovalInstance::getInitiatorId, userId)
                .orderByDesc(ApprovalInstance::getId));
        List<ApprovalDtos.InstanceListItemVO> items = list.stream().map(inst -> {
            InstanceDisplay d = InstanceDisplay.from(inst);
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
     * 发起人查看实例进度（兼容 initiatorId 存 userId 或 employeeId）
     */
    public ApprovalDtos.InstanceDetailVO getInstanceDetail(long instanceId, long userId, Long employeeId) {
        ApprovalInstance instance = instanceMapper.selectById(instanceId);
        if (instance == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "审批实例不存在");
        }
        Long initiator = instance.getInitiatorId();
        boolean owner = initiator != null
                && (initiator.equals(userId) || (employeeId != null && initiator.equals(employeeId)));
        if (!owner) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看该审批进度");
        }

        InstanceDisplay display = InstanceDisplay.from(instance);
        List<ProcessNodeDef> nodeDefs = nodesOf(instance);
        int current = instance.getCurrentNode() == null ? 1 : instance.getCurrentNode();
        String instStatus = instance.getStatus() == null ? "" : instance.getStatus().toUpperCase(Locale.ROOT);

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
            } else {
                // PENDING
                if (n.getOrder() < current) np.setState("done");
                else if (n.getOrder() == current) np.setState("current");
                else np.setState("pending");
            }
            nodes.add(np);
        }

        ApprovalDtos.InstanceDetailVO vo = new ApprovalDtos.InstanceDetailVO();
        vo.setInstanceId(instance.getId());
        vo.setProcessType(instance.getProcessType());
        vo.setTitle(display.title);
        vo.setStatus(apiStatus(instance.getStatus()));
        vo.setCurrentNodeLabel(labelOf(nodeDefs, current));
        vo.setCreatedAt(fmt(instance.getCreatedAt()));
        vo.setNodes(nodes);
        vo.setTimeline(buildTimeline(instance.getId()));
        return vo;
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

    /** 可见：原审批人或实际审批人（委托） */
    private static LambdaQueryWrapper<ApprovalTask> tasksVisibleToUser(long userId) {
        return new LambdaQueryWrapper<ApprovalTask>()
                .and(w -> w.eq(ApprovalTask::getAssigneeId, userId)
                        .or()
                        .eq(ApprovalTask::getActualAssigneeId, userId));
    }

    /** 待办可操作人：有 actual 时仅 actual，否则 assignee */
    private static boolean isEffectiveAssignee(ApprovalTask task, long userId) {
        if (task.getActualAssigneeId() != null) {
            return userId == task.getActualAssigneeId();
        }
        return task.getAssigneeId() != null && userId == task.getAssigneeId();
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
                    t.setDisplayText(l.getDisplayText());
                    return t;
                }).collect(Collectors.toList());
    }

    private ApprovalDtos.TaskListItemVO toTaskItem(ApprovalTask task, long userId) {
        ApprovalInstance instance = instanceMapper.selectById(task.getInstanceId());
        InstanceDisplay d = instance == null
                ? new InstanceDisplay()
                : InstanceDisplay.from(instance);
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
