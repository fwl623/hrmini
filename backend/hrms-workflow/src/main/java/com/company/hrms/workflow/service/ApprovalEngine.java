package com.company.hrms.workflow.service;

import com.company.hrms.common.exception.BusinessException;
import com.company.hrms.common.exception.ErrorCode;
import com.company.hrms.common.web.PageResult;
import com.company.hrms.workflow.dto.ApprovalDtos;
import com.company.hrms.workflow.entity.ApprovalInstance;
import com.company.hrms.workflow.entity.ApprovalLog;
import com.company.hrms.workflow.entity.ApprovalProcessDef;
import com.company.hrms.workflow.entity.ApprovalTask;
import com.company.hrms.workflow.entity.OnboardingApplication;
import com.company.hrms.workflow.enums.ApprovalAction;
import com.company.hrms.workflow.model.ProcessNodeDef;
import com.company.hrms.workflow.store.WorkflowMemoryStore;
import com.company.hrms.workflow.support.CurrentUserProvider;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

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
import java.util.stream.Collectors;

@Service
public class ApprovalEngine {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final WorkflowMemoryStore store;
    private final CurrentUserProvider currentUserProvider;
    private final ObjectMapper objectMapper;
    private final OnboardingService onboardingService;

    public ApprovalEngine(WorkflowMemoryStore store,
                          CurrentUserProvider currentUserProvider,
                          ObjectMapper objectMapper,
                          @Lazy OnboardingService onboardingService) {
        this.store = store;
        this.currentUserProvider = currentUserProvider;
        this.objectMapper = objectMapper;
        this.onboardingService = onboardingService;
    }

    public Long createInstance(String processType,
                               String businessKey,
                               Long initiatorId,
                               Map<String, Object> variables,
                               WorkflowMemoryStore.InstanceMeta meta) {
        ApprovalProcessDef def = store.findProcessDef(processType)
                .orElseThrow(() -> new BusinessException(ErrorCode.PARAM_INVALID, "未找到流程定义: " + processType));

        List<ProcessNodeDef> nodes = resolveActiveNodes(def, variables);
        if (nodes.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "审批链为空");
        }

        LocalDateTime now = LocalDateTime.now();
        ApprovalInstance instance = new ApprovalInstance();
        instance.setId(store.nextInstanceId());
        instance.setProcessType(processType);
        instance.setBusinessKey(businessKey);
        instance.setStatus("PENDING");
        instance.setInitiatorId(initiatorId);
        instance.setCurrentNode(nodes.get(0).getOrder());
        instance.setCreatedAt(now);
        instance.setUpdatedAt(now);
        store.saveInstance(instance);

        if (meta == null) {
            meta = new WorkflowMemoryStore.InstanceMeta();
        }
        meta.currentNodeLabel = nodes.get(0).getLabel();
        store.putInstanceMeta(instance.getId(), meta);

        createTaskForNode(instance, def, nodes.get(0), now);

        ApprovalLog log = new ApprovalLog();
        log.setId(store.nextLogId());
        log.setInstanceId(instance.getId());
        log.setOperatorId(initiatorId);
        log.setAction(ApprovalAction.SUBMIT.name());
        log.setFromStatus(null);
        log.setToStatus("PENDING");
        log.setCreatedAt(now);
        store.saveLog(log);

        return instance.getId();
    }

    public ApprovalDtos.TaskStatsVO taskStats(long userId) {
        List<ApprovalTask> mine = store.listAllTasks().stream()
                .filter(t -> Objects.equals(t.getActualAssigneeId(), userId) || Objects.equals(t.getAssigneeId(), userId))
                .toList();

        LocalDate today = LocalDate.now();
        long pending = mine.stream().filter(t -> "pending".equalsIgnoreCase(t.getStatus())).count();
        long approvedToday = mine.stream()
                .filter(t -> "approved".equalsIgnoreCase(t.getStatus()))
                .filter(t -> t.getCompletedAt() != null && t.getCompletedAt().toLocalDate().equals(today))
                .count();
        long overdue = mine.stream()
                .filter(t -> "pending".equalsIgnoreCase(t.getStatus()))
                .filter(t -> t.getSlaDeadline() != null && t.getSlaDeadline().isBefore(LocalDateTime.now()))
                .count();

        ApprovalDtos.TaskStatsVO vo = new ApprovalDtos.TaskStatsVO();
        vo.setPending(pending);
        vo.setApprovedToday(approvedToday);
        vo.setOverdueCount(overdue);
        return vo;
    }

    public PageResult<ApprovalDtos.TaskListItemVO> listTasks(long userId,
                                                             String status,
                                                             String processType,
                                                             String keyword,
                                                             int page,
                                                             int pageSize) {
        String statusFilter = status == null || status.isBlank() ? "pending" : status;
        List<ApprovalDtos.TaskListItemVO> all = store.listAllTasks().stream()
                .filter(t -> matchAssignee(userId, t, statusFilter))
                .filter(t -> matchStatus(t, statusFilter))
                .map(this::toListItem)
                .filter(item -> processType == null || processType.isBlank()
                        || processType.equalsIgnoreCase(item.getProcessType()))
                .filter(item -> keyword == null || keyword.isBlank()
                        || (item.getTitle() != null && item.getTitle().contains(keyword))
                        || (item.getApplicantName() != null && item.getApplicantName().contains(keyword)))
                .sorted(Comparator.comparing(ApprovalDtos.TaskListItemVO::getCreateTime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());

        return pageOf(all, page, pageSize);
    }

    public ApprovalDtos.TaskDetailVO getTaskDetail(long taskId, long userId) {
        ApprovalTask task = store.findTask(taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PARAM_INVALID, "任务不存在"));
        ApprovalInstance instance = store.findInstance(task.getInstanceId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PARAM_INVALID, "实例不存在"));
        WorkflowMemoryStore.InstanceMeta meta = store.getInstanceMeta(instance.getId());

        ApprovalDtos.TaskBriefVO taskVo = new ApprovalDtos.TaskBriefVO();
        taskVo.setId(task.getId());
        taskVo.setStatus(task.getStatus());
        taskVo.setCurrentNodeLabel(meta.currentNodeLabel);
        taskVo.setDueAt(format(task.getSlaDeadline()));

        ApprovalDtos.InstanceBriefVO instanceVo = new ApprovalDtos.InstanceBriefVO();
        instanceVo.setProcessType(instance.getProcessType());
        instanceVo.setBusinessNo(meta.businessNo);
        instanceVo.setInitiator(meta.applicantName);
        instanceVo.setCreatedAt(format(instance.getCreatedAt()));
        instanceVo.setStatus(instance.getStatus());

        ApprovalDtos.TaskDetailVO detail = new ApprovalDtos.TaskDetailVO();
        detail.setTask(taskVo);
        detail.setInstance(instanceVo);
        detail.setBusinessDetail(loadBusinessDetail(instance));
        detail.setTimeline(buildTimeline(instance.getId()));
        detail.setActions(resolveActions(task, instance, userId));
        return detail;
    }

    public void action(long taskId, long operatorId, ApprovalDtos.ActionRequest request) {
        if (request == null || request.getAction() == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "action 必填");
        }
        ApprovalAction action;
        try {
            action = ApprovalAction.valueOf(request.getAction().trim().toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "不支持的 action");
        }
        if (action != ApprovalAction.APPROVE && action != ApprovalAction.REJECT && action != ApprovalAction.FORWARD) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "任务操作仅支持 APPROVE/REJECT/FORWARD");
        }
        if (action == ApprovalAction.REJECT && (request.getComment() == null || request.getComment().isBlank())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "驳回时 comment 必填");
        }
        if (action == ApprovalAction.FORWARD && request.getTargetUserId() == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "转交时 targetUserId 必填");
        }

        ApprovalTask task = store.findTask(taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PARAM_INVALID, "任务不存在"));
        if (!"pending".equalsIgnoreCase(task.getStatus())) {
            throw new BusinessException(ErrorCode.APPROVAL_ALREADY_HANDLED);
        }
        if (!Objects.equals(task.getActualAssigneeId(), operatorId) && !Objects.equals(task.getAssigneeId(), operatorId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "非当前审批人");
        }

        ApprovalInstance instance = store.findInstance(task.getInstanceId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PARAM_INVALID, "实例不存在"));
        if (!"PENDING".equalsIgnoreCase(instance.getStatus())) {
            throw new BusinessException(ErrorCode.APPROVAL_STATE_INVALID);
        }

        LocalDateTime now = LocalDateTime.now();
        String from = instance.getStatus();

        if (action == ApprovalAction.FORWARD) {
            task.setStatus("forwarded");
            task.setComment(request.getComment());
            task.setCompletedAt(now);
            store.saveTask(task);

            ApprovalTask next = new ApprovalTask();
            next.setId(store.nextTaskId());
            next.setInstanceId(instance.getId());
            next.setNodeOrder(task.getNodeOrder());
            next.setAssigneeId(request.getTargetUserId());
            next.setActualAssigneeId(resolveActualAssignee(request.getTargetUserId()));
            next.setStatus("pending");
            next.setSlaDeadline(LocalDateTime.now().plusHours(48));
            next.setOverdue(0);
            store.saveTask(next);

            writeLog(instance.getId(), task.getId(), operatorId, task.getAssigneeId(), action,
                    request.getComment(), from, from, now);
            return;
        }

        if (action == ApprovalAction.REJECT) {
            task.setStatus("rejected");
            task.setComment(request.getComment());
            task.setCompletedAt(now);
            store.saveTask(task);
            instance.setStatus("REJECTED");
            instance.setUpdatedAt(now);
            store.saveInstance(instance);
            writeLog(instance.getId(), task.getId(), operatorId, task.getAssigneeId(), action,
                    request.getComment(), from, "REJECTED", now);
            notifyBusiness(instance, false, request.getComment());
            return;
        }

        // APPROVE
        task.setStatus("approved");
        task.setComment(request.getComment());
        task.setCompletedAt(now);
        store.saveTask(task);

        ApprovalProcessDef def = store.findProcessDef(instance.getProcessType())
                .orElseThrow(() -> new BusinessException(ErrorCode.PARAM_INVALID, "流程定义不存在"));
        Map<String, Object> variables = loadVariables(instance);
        List<ProcessNodeDef> nodes = resolveActiveNodes(def, variables);
        ProcessNodeDef current = nodes.stream()
                .filter(n -> n.getOrder() == task.getNodeOrder())
                .findFirst()
                .orElse(null);
        ProcessNodeDef nextNode = nodes.stream()
                .filter(n -> current != null && n.getOrder() > current.getOrder())
                .min(Comparator.comparingInt(ProcessNodeDef::getOrder))
                .orElse(null);

        if (nextNode != null) {
            instance.setCurrentNode(nextNode.getOrder());
            instance.setUpdatedAt(now);
            store.saveInstance(instance);
            WorkflowMemoryStore.InstanceMeta meta = store.getInstanceMeta(instance.getId());
            meta.currentNodeLabel = nextNode.getLabel();
            store.putInstanceMeta(instance.getId(), meta);
            createTaskForNode(instance, def, nextNode, now);
            writeLog(instance.getId(), task.getId(), operatorId, task.getAssigneeId(), action,
                    request.getComment(), from, "PENDING", now);
            return;
        }

        instance.setStatus("APPROVED");
        instance.setUpdatedAt(now);
        store.saveInstance(instance);
        WorkflowMemoryStore.InstanceMeta meta = store.getInstanceMeta(instance.getId());
        meta.currentNodeLabel = "已结束";
        store.putInstanceMeta(instance.getId(), meta);
        writeLog(instance.getId(), task.getId(), operatorId, task.getAssigneeId(), action,
                request.getComment(), from, "APPROVED", now);
        notifyBusiness(instance, true, request.getComment());
    }

    public void remind(long taskId, long operatorId) {
        ApprovalTask task = store.findTask(taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PARAM_INVALID, "任务不存在"));
        if (!"pending".equalsIgnoreCase(task.getStatus())) {
            throw new BusinessException(ErrorCode.APPROVAL_STATE_INVALID, "仅待办可催办");
        }
        ApprovalLog log = new ApprovalLog();
        log.setId(store.nextLogId());
        log.setInstanceId(task.getInstanceId());
        log.setTaskId(taskId);
        log.setOperatorId(operatorId);
        log.setAction("REMIND");
        log.setComment("催办通知已记录（Day2 不发真实消息）");
        log.setCreatedAt(LocalDateTime.now());
        store.saveLog(log);
    }

    public void withdrawInstance(long instanceId, long operatorId) {
        ApprovalInstance instance = store.findInstance(instanceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PARAM_INVALID, "实例不存在"));
        if (!Objects.equals(instance.getInitiatorId(), operatorId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅发起人可撤回");
        }
        if (!"PENDING".equalsIgnoreCase(instance.getStatus())) {
            throw new BusinessException(ErrorCode.APPROVAL_STATE_INVALID);
        }
        List<ApprovalTask> tasks = store.listTasksByInstance(instanceId);
        boolean hasApproved = tasks.stream().anyMatch(t -> "approved".equalsIgnoreCase(t.getStatus()));
        boolean beyondFirst = instance.getCurrentNode() != null && instance.getCurrentNode() > 1;
        if (hasApproved || beyondFirst) {
            throw new BusinessException(ErrorCode.APPROVAL_STATE_INVALID, "仅第一级未审批前可撤回");
        }

        LocalDateTime now = LocalDateTime.now();
        String from = instance.getStatus();
        instance.setStatus("CANCELLED");
        instance.setUpdatedAt(now);
        store.saveInstance(instance);
        for (ApprovalTask t : tasks) {
            if ("pending".equalsIgnoreCase(t.getStatus())) {
                t.setStatus("cancelled");
                t.setCompletedAt(now);
                store.saveTask(t);
            }
        }
        writeLog(instanceId, null, operatorId, null, ApprovalAction.WITHDRAW, "发起人撤回", from, "CANCELLED", now);
        notifyBusinessWithdraw(instance);
    }

    public PageResult<ApprovalDtos.InstanceListItemVO> listMyInstances(long userId, int page, int pageSize) {
        List<ApprovalDtos.InstanceListItemVO> all = store.listInstancesByInitiator(userId).stream()
                .map(this::toInstanceItem)
                .collect(Collectors.toList());
        return pageOf(all, page, pageSize);
    }

    private void notifyBusiness(ApprovalInstance instance, boolean approved, String comment) {
        if ("ONBOARDING".equalsIgnoreCase(instance.getProcessType())) {
            onboardingService.onApprovalFinished(instance.getBusinessKey(), approved, comment);
        }
    }

    private void notifyBusinessWithdraw(ApprovalInstance instance) {
        if ("ONBOARDING".equalsIgnoreCase(instance.getProcessType())) {
            onboardingService.onApprovalWithdrawn(instance.getBusinessKey());
        }
    }

    private List<ProcessNodeDef> resolveActiveNodes(ApprovalProcessDef def, Map<String, Object> variables) {
        try {
            List<ProcessNodeDef> all = objectMapper.readValue(def.getNodesJson(), new TypeReference<>() {
            });
            boolean needSecond = Boolean.TRUE.equals(variables.get("needSecondApproval"));
            return all.stream()
                    .sorted(Comparator.comparingInt(ProcessNodeDef::getOrder))
                    .filter(n -> !n.isOptional() || needSecond)
                    .collect(Collectors.toList());
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "解析审批链失败");
        }
    }

    private void createTaskForNode(ApprovalInstance instance, ApprovalProcessDef def, ProcessNodeDef node, LocalDateTime now) {
        Long assignee = node.getAssigneeUserId();
        ApprovalTask task = new ApprovalTask();
        task.setId(store.nextTaskId());
        task.setInstanceId(instance.getId());
        task.setNodeOrder(node.getOrder());
        task.setAssigneeId(assignee);
        task.setActualAssigneeId(resolveActualAssignee(assignee));
        task.setStatus("pending");
        int sla = def.getSlaHours() == null ? 48 : def.getSlaHours();
        task.setSlaDeadline(now.plusHours(sla));
        task.setOverdue(0);
        store.saveTask(task);
    }

    private Long resolveActualAssignee(Long assigneeId) {
        if (assigneeId == null) {
            return null;
        }
        return store.findActiveDelegation(assigneeId, LocalDate.now())
                .map(d -> d.getDelegateUserId())
                .orElse(assigneeId);
    }

    private boolean matchAssignee(long userId, ApprovalTask t, String statusFilter) {
        return Objects.equals(t.getActualAssigneeId(), userId) || Objects.equals(t.getAssigneeId(), userId);
    }

    private boolean matchStatus(ApprovalTask t, String statusFilter) {
        if ("done".equalsIgnoreCase(statusFilter)) {
            return !"pending".equalsIgnoreCase(t.getStatus()) && !"cancelled".equalsIgnoreCase(t.getStatus());
        }
        return statusFilter.equalsIgnoreCase(t.getStatus());
    }

    private ApprovalDtos.TaskListItemVO toListItem(ApprovalTask task) {
        ApprovalInstance instance = store.findInstance(task.getInstanceId()).orElse(null);
        WorkflowMemoryStore.InstanceMeta meta = store.getInstanceMeta(task.getInstanceId());
        ApprovalDtos.TaskListItemVO vo = new ApprovalDtos.TaskListItemVO();
        vo.setTaskId(task.getId());
        vo.setInstanceId(task.getInstanceId());
        vo.setProcessType(instance == null ? null : instance.getProcessType());
        vo.setTitle(meta.title);
        vo.setApplicantName(meta.applicantName);
        vo.setApplicantDept(meta.applicantDept);
        vo.setBusinessNo(meta.businessNo);
        vo.setBusinessSummary(meta.businessSummary);
        vo.setCurrentNodeLabel(meta.currentNodeLabel);
        vo.setCreateTime(instance == null ? null : format(instance.getCreatedAt()));
        vo.setDueAt(format(task.getSlaDeadline()));
        vo.setStatus(task.getStatus());
        return vo;
    }

    private ApprovalDtos.InstanceListItemVO toInstanceItem(ApprovalInstance instance) {
        WorkflowMemoryStore.InstanceMeta meta = store.getInstanceMeta(instance.getId());
        ApprovalTask current = store.listTasksByInstance(instance.getId()).stream()
                .filter(t -> "pending".equalsIgnoreCase(t.getStatus()))
                .findFirst()
                .orElse(store.listTasksByInstance(instance.getId()).stream()
                        .reduce((a, b) -> b)
                        .orElse(null));
        ApprovalDtos.InstanceListItemVO vo = new ApprovalDtos.InstanceListItemVO();
        vo.setInstanceId(instance.getId());
        vo.setTaskId(current == null ? 0L : current.getId());
        vo.setProcessType(instance.getProcessType());
        vo.setTitle(meta.title);
        vo.setApplicantName(meta.applicantName);
        vo.setApplicantDept(meta.applicantDept);
        vo.setCurrentNodeLabel(meta.currentNodeLabel);
        vo.setCreateTime(format(instance.getCreatedAt()));
        vo.setDueAt(current == null ? null : format(current.getSlaDeadline()));
        vo.setStatus(mapInstanceStatus(instance.getStatus()));
        return vo;
    }

    private String mapInstanceStatus(String status) {
        if (status == null) {
            return "pending";
        }
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "APPROVED" -> "approved";
            case "REJECTED" -> "rejected";
            case "CANCELLED" -> "cancelled";
            default -> "pending";
        };
    }

    private Map<String, Object> loadBusinessDetail(ApprovalInstance instance) {
        Map<String, Object> detail = new HashMap<>();
        if (instance.getBusinessKey() != null && instance.getBusinessKey().startsWith("ONBOARDING:")) {
            Long id = Long.parseLong(instance.getBusinessKey().substring("ONBOARDING:".length()));
            store.findOnboarding(id).ifPresent(app -> {
                detail.put("name", app.getName());
                detail.put("mobile", app.getMobile());
                detail.put("departmentId", app.getDepartmentId());
                detail.put("positionId", app.getPositionId());
                detail.put("baseSalary", app.getBaseSalary());
                detail.put("expectedOnboardDate", app.getExpectedOnboardDate());
                detail.put("status", app.getStatus());
            });
        }
        return detail;
    }

    private Map<String, Object> loadVariables(ApprovalInstance instance) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("needSecondApproval", false);
        if (instance.getBusinessKey() != null && instance.getBusinessKey().startsWith("ONBOARDING:")) {
            Long id = Long.parseLong(instance.getBusinessKey().substring("ONBOARDING:".length()));
            OnboardingApplication app = store.findOnboarding(id).orElse(null);
            if (app != null) {
                vars.put("needSecondApproval", onboardingService.needSecondApproval(app));
            }
        }
        // 多节点种子：若已有 nodeOrder>1 的任务历史，按已解析链；否则按业务变量
        return vars;
    }

    private List<ApprovalDtos.TimelineItemVO> buildTimeline(Long instanceId) {
        List<ApprovalDtos.TimelineItemVO> list = new ArrayList<>();
        for (ApprovalLog log : store.listLogsByInstance(instanceId)) {
            ApprovalDtos.TimelineItemVO item = new ApprovalDtos.TimelineItemVO();
            item.setNode(log.getAction());
            item.setAssignee(currentUserProvider.displayName(log.getOperatorId() == null ? 0 : log.getOperatorId()));
            item.setAction(log.getAction());
            item.setComment(log.getComment());
            item.setTime(format(log.getCreatedAt()));
            item.setDisplayText(log.getDisplayText());
            list.add(item);
        }
        return list;
    }

    private List<String> resolveActions(ApprovalTask task, ApprovalInstance instance, long userId) {
        if (!"pending".equalsIgnoreCase(task.getStatus()) || !"PENDING".equalsIgnoreCase(instance.getStatus())) {
            return List.of();
        }
        if (!Objects.equals(task.getActualAssigneeId(), userId) && !Objects.equals(task.getAssigneeId(), userId)) {
            return List.of();
        }
        return List.of("APPROVE", "REJECT", "FORWARD");
    }

    private void writeLog(Long instanceId, Long taskId, Long operatorId, Long onBehalfOf,
                          ApprovalAction action, String comment, String from, String to, LocalDateTime now) {
        ApprovalLog log = new ApprovalLog();
        log.setId(store.nextLogId());
        log.setInstanceId(instanceId);
        log.setTaskId(taskId);
        log.setOperatorId(operatorId);
        if (onBehalfOf != null && !Objects.equals(onBehalfOf, operatorId)) {
            log.setOnBehalfOfId(onBehalfOf);
            log.setDisplayText(currentUserProvider.displayName(operatorId) + " 代 "
                    + currentUserProvider.displayName(onBehalfOf) + " 审批");
        }
        log.setAction(action.name());
        log.setComment(comment);
        log.setFromStatus(from);
        log.setToStatus(to);
        log.setCreatedAt(now);
        store.saveLog(log);
    }

    private static String format(LocalDateTime time) {
        return time == null ? null : DT.format(time);
    }

    private static <T> PageResult<T> pageOf(List<T> all, int page, int pageSize) {
        int p = page <= 0 ? 1 : page;
        int size = pageSize <= 0 ? 20 : pageSize;
        int from = Math.min((p - 1) * size, all.size());
        int to = Math.min(from + size, all.size());
        return PageResult.of(all.subList(from, to), all.size(), p, size);
    }
}
