package com.company.hrms.workflow.store;

import com.company.hrms.workflow.entity.ApprovalDelegation;
import com.company.hrms.workflow.entity.ApprovalInstance;
import com.company.hrms.workflow.entity.ApprovalLog;
import com.company.hrms.workflow.entity.ApprovalProcessDef;
import com.company.hrms.workflow.entity.ApprovalTask;
import com.company.hrms.workflow.entity.OnboardingApplication;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Day2 内存仓储（后续可替换为 Mapper）。
 */
@Component
public class WorkflowMemoryStore {

    private final AtomicLong processDefSeq = new AtomicLong(1_000_000);
    private final AtomicLong instanceSeq = new AtomicLong(1_000_000);
    private final AtomicLong taskSeq = new AtomicLong(1_000_000);
    private final AtomicLong logSeq = new AtomicLong(1_000_000);
    private final AtomicLong delegationSeq = new AtomicLong(1_000_000);
    private final AtomicLong onboardingSeq = new AtomicLong(1_000_000);

    private final Map<Long, ApprovalProcessDef> processDefs = new ConcurrentHashMap<>();
    private final Map<Long, ApprovalInstance> instances = new ConcurrentHashMap<>();
    private final Map<Long, ApprovalTask> tasks = new ConcurrentHashMap<>();
    private final Map<Long, ApprovalLog> logs = new ConcurrentHashMap<>();
    private final Map<Long, ApprovalDelegation> delegations = new ConcurrentHashMap<>();
    private final Map<Long, OnboardingApplication> onboardings = new ConcurrentHashMap<>();
    /** instanceId -> title / businessNo 等展示字段 */
    private final Map<Long, InstanceMeta> instanceMetas = new ConcurrentHashMap<>();

    @PostConstruct
    public void seed() {
        ApprovalProcessDef onboardingDef = new ApprovalProcessDef();
        onboardingDef.setId(processDefSeq.getAndIncrement());
        onboardingDef.setProcessType("ONBOARDING");
        onboardingDef.setName("入职审批");
        onboardingDef.setSlaHours(48);
        onboardingDef.setStatus(1);
        onboardingDef.setNodesJson("""
                [
                  {"order":1,"label":"部门负责人审批","assigneeUserId":1002,"optional":false},
                  {"order":2,"label":"HR二审","assigneeUserId":1003,"optional":true,"condition":"needSecondApproval"}
                ]
                """);
        processDefs.put(onboardingDef.getId(), onboardingDef);

        OnboardingApplication draft = new OnboardingApplication();
        draft.setId(onboardingSeq.getAndIncrement());
        draft.setStatus("draft");
        draft.setName("待提交候选人");
        draft.setGender("MALE");
        draft.setMobile("13800000001");
        draft.setEmail("draft@example.com");
        draft.setIdNumberEnc("110101199001011234");
        draft.setExpectedOnboardDate(LocalDate.now().plusDays(7));
        draft.setDepartmentId(10L);
        draft.setPositionId(20L);
        draft.setEmploymentType("fulltime");
        draft.setProbationMonths(3);
        draft.setProbationSalaryRatio(new BigDecimal("0.80"));
        draft.setBaseSalary(new BigDecimal("15000"));
        draft.setManagerId(1002L);
        draft.setCreatedBy(1001L);
        draft.setCreatedAt(LocalDateTime.now().minusDays(1));
        draft.setUpdatedAt(draft.getCreatedAt());
        onboardings.put(draft.getId(), draft);

        // 演示待办：已提交、待部门负责人（1002）审批
        OnboardingApplication pendingApp = new OnboardingApplication();
        pendingApp.setId(onboardingSeq.getAndIncrement());
        pendingApp.setStatus("pending");
        pendingApp.setName("张三");
        pendingApp.setGender("MALE");
        pendingApp.setMobile("13800000002");
        pendingApp.setEmail("zhangsan@example.com");
        pendingApp.setIdNumberEnc("110101199002021234");
        pendingApp.setExpectedOnboardDate(LocalDate.now().plusDays(3));
        pendingApp.setDepartmentId(10L);
        pendingApp.setPositionId(20L);
        pendingApp.setEmploymentType("fulltime");
        pendingApp.setProbationMonths(3);
        pendingApp.setProbationSalaryRatio(new BigDecimal("0.80"));
        pendingApp.setBaseSalary(new BigDecimal("18000"));
        pendingApp.setManagerId(1002L);
        pendingApp.setCreatedBy(1001L);
        pendingApp.setCreatedAt(LocalDateTime.now().minusHours(5));
        pendingApp.setUpdatedAt(pendingApp.getCreatedAt());

        ApprovalInstance instance = new ApprovalInstance();
        instance.setId(instanceSeq.getAndIncrement());
        instance.setProcessType("ONBOARDING");
        instance.setBusinessKey("ONBOARDING:" + pendingApp.getId());
        instance.setStatus("PENDING");
        instance.setInitiatorId(1001L);
        instance.setCurrentNode(1);
        instance.setCreatedAt(pendingApp.getCreatedAt());
        instance.setUpdatedAt(pendingApp.getCreatedAt());
        instances.put(instance.getId(), instance);

        pendingApp.setInstanceId(instance.getId());
        onboardings.put(pendingApp.getId(), pendingApp);

        InstanceMeta meta = new InstanceMeta();
        meta.title = "张三入职审批";
        meta.businessNo = "OA-2026-" + String.format("%03d", pendingApp.getId());
        meta.applicantName = "HR李四";
        meta.applicantDept = "人力资源部";
        meta.businessSummary = "技术部-Java开发工程师";
        meta.currentNodeLabel = "部门负责人审批";
        instanceMetas.put(instance.getId(), meta);

        ApprovalTask task = new ApprovalTask();
        task.setId(taskSeq.getAndIncrement());
        task.setInstanceId(instance.getId());
        task.setNodeOrder(1);
        task.setAssigneeId(1002L);
        task.setActualAssigneeId(1002L);
        task.setStatus("pending");
        task.setSlaDeadline(LocalDateTime.now().plusHours(24));
        task.setOverdue(0);
        tasks.put(task.getId(), task);

        ApprovalLog submitLog = new ApprovalLog();
        submitLog.setId(logSeq.getAndIncrement());
        submitLog.setInstanceId(instance.getId());
        submitLog.setTaskId(null);
        submitLog.setOperatorId(1001L);
        submitLog.setAction("SUBMIT");
        submitLog.setFromStatus("draft");
        submitLog.setToStatus("pending");
        submitLog.setCreatedAt(pendingApp.getCreatedAt());
        logs.put(submitLog.getId(), submitLog);

        ApprovalDelegation delegation = new ApprovalDelegation();
        delegation.setId(delegationSeq.getAndIncrement());
        delegation.setDelegatorId(1002L);
        delegation.setDelegateUserId(1004L);
        delegation.setStartDate(LocalDate.now().minusDays(1));
        delegation.setEndDate(LocalDate.now().plusDays(7));
        delegation.setReason("出差委托演示");
        delegation.setStatus("CANCELLED"); // 默认不生效，避免干扰；需要时可改为 ACTIVE
        delegation.setCreatedAt(LocalDateTime.now().minusDays(1));
        delegations.put(delegation.getId(), delegation);
    }

    public long nextProcessDefId() {
        return processDefSeq.getAndIncrement();
    }

    public long nextInstanceId() {
        return instanceSeq.getAndIncrement();
    }

    public long nextTaskId() {
        return taskSeq.getAndIncrement();
    }

    public long nextLogId() {
        return logSeq.getAndIncrement();
    }

    public long nextOnboardingId() {
        return onboardingSeq.getAndIncrement();
    }

    public Optional<ApprovalProcessDef> findProcessDef(String processType) {
        return processDefs.values().stream()
                .filter(d -> processType.equals(d.getProcessType()) && Integer.valueOf(1).equals(d.getStatus()))
                .findFirst();
    }

    public void saveInstance(ApprovalInstance instance) {
        instances.put(instance.getId(), instance);
    }

    public Optional<ApprovalInstance> findInstance(Long id) {
        return Optional.ofNullable(instances.get(id));
    }

    public List<ApprovalInstance> listInstancesByInitiator(Long initiatorId) {
        return instances.values().stream()
                .filter(i -> initiatorId.equals(i.getInitiatorId()))
                .sorted(Comparator.comparing(ApprovalInstance::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    public void saveTask(ApprovalTask task) {
        tasks.put(task.getId(), task);
    }

    public Optional<ApprovalTask> findTask(Long id) {
        return Optional.ofNullable(tasks.get(id));
    }

    public List<ApprovalTask> listTasksByInstance(Long instanceId) {
        return tasks.values().stream()
                .filter(t -> instanceId.equals(t.getInstanceId()))
                .sorted(Comparator.comparing(ApprovalTask::getNodeOrder))
                .collect(Collectors.toList());
    }

    public List<ApprovalTask> listAllTasks() {
        return new ArrayList<>(tasks.values());
    }

    public void saveLog(ApprovalLog log) {
        logs.put(log.getId(), log);
    }

    public List<ApprovalLog> listLogsByInstance(Long instanceId) {
        return logs.values().stream()
                .filter(l -> instanceId.equals(l.getInstanceId()))
                .sorted(Comparator.comparing(ApprovalLog::getCreatedAt))
                .collect(Collectors.toList());
    }

    public Optional<ApprovalDelegation> findActiveDelegation(Long delegatorId, LocalDate onDate) {
        return delegations.values().stream()
                .filter(d -> "ACTIVE".equalsIgnoreCase(d.getStatus()))
                .filter(d -> delegatorId.equals(d.getDelegatorId()))
                .filter(d -> (d.getStartDate() == null || !onDate.isBefore(d.getStartDate())))
                .filter(d -> (d.getEndDate() == null || !onDate.isAfter(d.getEndDate())))
                .findFirst();
    }

    public void saveOnboarding(OnboardingApplication app) {
        onboardings.put(app.getId(), app);
    }

    public void removeOnboarding(Long id) {
        onboardings.remove(id);
    }

    public Optional<OnboardingApplication> findOnboarding(Long id) {
        return Optional.ofNullable(onboardings.get(id));
    }

    public List<OnboardingApplication> listOnboardings() {
        return onboardings.values().stream()
                .sorted(Comparator.comparing(OnboardingApplication::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    public boolean existsMobile(String mobile, Long excludeId) {
        return onboardings.values().stream()
                .anyMatch(a -> mobile.equals(a.getMobile()) && (excludeId == null || !excludeId.equals(a.getId())));
    }

    public void putInstanceMeta(Long instanceId, InstanceMeta meta) {
        instanceMetas.put(instanceId, meta);
    }

    public InstanceMeta getInstanceMeta(Long instanceId) {
        return instanceMetas.getOrDefault(instanceId, new InstanceMeta());
    }

    public static class InstanceMeta {
        public String title = "";
        public String businessNo = "";
        public String applicantName = "";
        public String applicantDept = "";
        public String businessSummary = "";
        public String currentNodeLabel = "";
    }
}
