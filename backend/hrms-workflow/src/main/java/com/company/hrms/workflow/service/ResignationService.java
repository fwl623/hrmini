package com.company.hrms.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.hrms.common.exception.BusinessException;
import com.company.hrms.common.exception.ErrorCode;
import com.company.hrms.common.security.LoginUser;
import com.company.hrms.common.security.SecurityUtils;
import com.company.hrms.common.web.PageResult;
import com.company.hrms.employee.entity.Employee;
import com.company.hrms.employee.service.EmployeeLifecycleService;
import com.company.hrms.workflow.dto.LifecycleDtos;
import com.company.hrms.workflow.entity.EmployeeResignationRequest;
import com.company.hrms.workflow.entity.ResignationApplication;
import com.company.hrms.workflow.mapper.EmployeeResignationRequestMapper;
import com.company.hrms.workflow.mapper.ResignationApplicationMapper;
import com.company.hrms.workflow.model.ProcessNodeDef;
import com.company.hrms.workflow.notify.ApprovalNotifyPublisher;
import com.company.hrms.workflow.support.CurrentUserProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ResignationService {

    private static final Logger log = LoggerFactory.getLogger(ResignationService.class);
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final EmployeeResignationRequestMapper requestMapper;
    private final ResignationApplicationMapper resignationMapper;
    private final EmployeeLifecycleService employeeLifecycleService;
    private final DbApprovalService dbApprovalService;
    private final CurrentUserProvider currentUserProvider;
    private final ApprovalNotifyPublisher notifyPublisher;

    public ResignationService(EmployeeResignationRequestMapper requestMapper,
                              ResignationApplicationMapper resignationMapper,
                              EmployeeLifecycleService employeeLifecycleService,
                              DbApprovalService dbApprovalService,
                              CurrentUserProvider currentUserProvider,
                              ApprovalNotifyPublisher notifyPublisher) {
        this.requestMapper = requestMapper;
        this.resignationMapper = resignationMapper;
        this.employeeLifecycleService = employeeLifecycleService;
        this.dbApprovalService = dbApprovalService;
        this.currentUserProvider = currentUserProvider;
        this.notifyPublisher = notifyPublisher;
    }

    /**
     * 门户员工发起离职申请（PRD §5.4 双通道第一阶段）。
     * 审批通过后由 HR 决定是否立即发起正式离职。
     */
    @Transactional
    public LifecycleDtos.ResignationRequestVO createMyRequest(LifecycleDtos.ResignationRequestCreate req) {
        return createRequest(requireSelfEmployeeId(), req);
    }

    public PageResult<LifecycleDtos.ResignationRequestVO> listMyRequests(int page, int pageSize) {
        Long employeeId = requireSelfEmployeeId();
        LambdaQueryWrapper<EmployeeResignationRequest> q = new LambdaQueryWrapper<EmployeeResignationRequest>()
                .eq(EmployeeResignationRequest::getEmployeeId, employeeId)
                .orderByDesc(EmployeeResignationRequest::getId);
        return pageRequests(requestMapper.selectList(q), page, pageSize);
    }

    @Transactional
    public void cancelMyRequest(Long id) {
        Long employeeId = requireSelfEmployeeId();
        EmployeeResignationRequest app = requestMapper.selectById(id);
        if (app == null || !employeeId.equals(app.getEmployeeId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能撤销本人的离职申请");
        }
        if (!"PENDING".equalsIgnoreCase(app.getStatus())) {
            throw new BusinessException(ErrorCode.APPROVAL_STATE_INVALID, "仅 PENDING 可撤销");
        }
        if (app.getInstanceId() != null) {
            dbApprovalService.withdrawInstance(app.getInstanceId(), currentUserProvider.requireUserId());
        } else {
            app.setStatus("CANCELLED");
            app.setUpdatedAt(LocalDateTime.now());
            requestMapper.updateById(app);
        }
    }

    public PageResult<LifecycleDtos.ResignationRequestVO> listRequests(int page, int pageSize, String status) {
        LambdaQueryWrapper<EmployeeResignationRequest> q = new LambdaQueryWrapper<EmployeeResignationRequest>()
                .orderByDesc(EmployeeResignationRequest::getId);
        if (status != null && !status.isBlank()) {
            q.eq(EmployeeResignationRequest::getStatus, status.toUpperCase(Locale.ROOT));
        }
        return pageRequests(requestMapper.selectList(q), page, pageSize);
    }

    @Transactional
    public LifecycleDtos.ResignationVO createResignation(LifecycleDtos.ResignationCreateRequest req) {
        requireHrOrAdmin();
        if (req == null || req.getEmployeeId() == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "employeeId 必填");
        }
        if (req.getResignationDate() == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "resignationDate 必填");
        }
        if (req.getReasonCategory() == null || req.getResignationType() == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "reasonCategory / resignationType 必填");
        }
        LocalDate resignDate = LocalDate.parse(req.getResignationDate());
        if (resignDate.isBefore(LocalDate.now())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "离职日须 ≥ 今天");
        }

        Employee emp = employeeLifecycleService.requireEmployee(req.getEmployeeId());
        Integer empStatus = emp.getEmploymentStatus();
        if (empStatus != null && (empStatus == 30 || empStatus == 40)) {
            throw new BusinessException(ErrorCode.EMPLOYEE_STATUS_INVALID,
                    empStatus == 40 ? "员工已离职，不可再发起离职" : "员工已在待离职中，不可重复发起");
        }
        long approvingCount = resignationMapper.selectCount(new LambdaQueryWrapper<ResignationApplication>()
                .eq(ResignationApplication::getEmployeeId, req.getEmployeeId())
                .eq(ResignationApplication::getStatus, "APPROVING"));
        if (approvingCount > 0) {
            throw new BusinessException(ErrorCode.APPROVAL_STATE_INVALID, "该员工已有审批中的正式离职单");
        }
        // 交接人由部门负责人在审批时确认；发起时若误传则校验「不能是本人」
        if (req.getHandoverEmployeeId() != null) {
            validateHandover(req.getEmployeeId(), req.getHandoverEmployeeId());
        }
        long userId = currentUserProvider.requireUserId();

        // requestId 可选：无则落一条线下协商占位（APPROVED），满足表 request_id NOT NULL
        Long requestId = req.getRequestId();
        if (requestId != null) {
            EmployeeResignationRequest request = requestMapper.selectById(requestId);
            if (request == null) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "关联离职申请不存在");
            }
            if (!"APPROVED".equalsIgnoreCase(request.getStatus())) {
                throw new BusinessException(ErrorCode.APPROVAL_STATE_INVALID, "须关联已批准的离职申请");
            }
            if (!req.getEmployeeId().equals(request.getEmployeeId())) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "employeeId 与 requestId 不匹配");
            }
        } else {
            LocalDateTime now = LocalDateTime.now();
            EmployeeResignationRequest placeholder = new EmployeeResignationRequest();
            placeholder.setEmployeeId(req.getEmployeeId());
            placeholder.setStatus("APPROVED");
            placeholder.setExpectedResignDate(resignDate);
            placeholder.setReasonCategory(req.getReasonCategory());
            placeholder.setResignationType(req.getResignationType());
            placeholder.setReasonDetail(
                    req.getReasonDetail() != null ? req.getReasonDetail() : "线下协商后由 HR 发起正式离职");
            placeholder.setCreatedAt(now);
            placeholder.setUpdatedAt(now);
            requestMapper.insert(placeholder);
            requestId = placeholder.getId();
        }

        ResignationApplication app = new ResignationApplication();
        app.setRequestId(requestId);
        app.setEmployeeId(req.getEmployeeId());
        app.setStatus("APPROVING");
        app.setResignationDate(resignDate);
        app.setReasonCategory(req.getReasonCategory());
        app.setResignationType(req.getResignationType());
        app.setReasonDetail(req.getReasonDetail());
        app.setHandoverEmployeeId(req.getHandoverEmployeeId());
        app.setCreatedBy(userId);
        app.setCreatedAt(LocalDateTime.now());
        resignationMapper.insert(app);

        List<ProcessNodeDef> nodes = buildResignationNodes(emp, userId);
        Long instanceId = dbApprovalService.createInstance(
                "RESIGNATION",
                String.valueOf(app.getId()),
                userId,
                nodes,
                DbApprovalService.InstanceDisplay.of(
                        emp.getName() + "正式离职",
                        emp.getName(),
                        null,
                        "RS-" + app.getId()),
                null);
        app.setInstanceId(instanceId);
        resignationMapper.updateById(app);
        return toResignVo(app);
    }

    /**
     * 部门负责人审批同意时确认工作交接人（PRD：确认交接安排）。
     */
    @Transactional
    public void confirmHandover(Long appId, Long handoverEmployeeId) {
        if (appId == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "离职单 ID 无效");
        }
        if (handoverEmployeeId == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "请指定工作交接人");
        }
        ResignationApplication app = resignationMapper.selectById(appId);
        if (app == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "离职申请不存在");
        }
        validateHandover(app.getEmployeeId(), handoverEmployeeId);
        app.setHandoverEmployeeId(handoverEmployeeId);
        resignationMapper.updateById(app);
    }

    public Map<String, Object> resignationBusinessDetail(Long appId) {
        Map<String, Object> biz = new java.util.HashMap<>();
        if (appId == null) {
            return biz;
        }
        ResignationApplication app = resignationMapper.selectById(appId);
        if (app == null) {
            return biz;
        }
        Employee emp = null;
        try {
            emp = employeeLifecycleService.requireEmployee(app.getEmployeeId());
        } catch (Exception ignored) {
            // ignore
        }
        biz.put("resignationId", app.getId());
        biz.put("employeeId", app.getEmployeeId());
        biz.put("employeeName", emp != null ? emp.getName() : null);
        biz.put("employeeNo", emp != null ? emp.getEmployeeNo() : null);
        biz.put("resignationDate", app.getResignationDate() != null ? app.getResignationDate().toString() : null);
        biz.put("reasonCategory", app.getReasonCategory());
        biz.put("resignationType", app.getResignationType());
        biz.put("reasonDetail", app.getReasonDetail());
        biz.put("handoverEmployeeId", app.getHandoverEmployeeId());
        if (app.getHandoverEmployeeId() != null) {
            try {
                Employee ho = employeeLifecycleService.requireEmployee(app.getHandoverEmployeeId());
                biz.put("handoverEmployeeName", ho.getName());
                biz.put("handoverEmployeeNo", ho.getEmployeeNo());
            } catch (Exception ignored) {
                // ignore
            }
        }
        return biz;
    }

    /** 员工离职申请详情（审批中心展示） */
    public Map<String, Object> requestBusinessDetail(Long requestId) {
        Map<String, Object> biz = new java.util.HashMap<>();
        if (requestId == null) {
            return biz;
        }
        EmployeeResignationRequest app = requestMapper.selectById(requestId);
        if (app == null) {
            return biz;
        }
        Employee emp = null;
        try {
            emp = employeeLifecycleService.requireEmployee(app.getEmployeeId());
        } catch (Exception ignored) {
            // ignore
        }
        biz.put("requestId", app.getId());
        biz.put("employeeId", app.getEmployeeId());
        biz.put("employeeName", emp != null ? emp.getName() : null);
        biz.put("employeeNo", emp != null ? emp.getEmployeeNo() : null);
        biz.put("expectedResignDate",
                app.getExpectedResignDate() != null ? app.getExpectedResignDate().toString() : null);
        biz.put("reasonCategory", app.getReasonCategory());
        biz.put("resignationType", app.getResignationType());
        biz.put("reasonDetail", app.getReasonDetail());
        biz.put("status", app.getStatus());
        return biz;
    }

    private void validateHandover(Long employeeId, Long handoverEmployeeId) {
        if (employeeId != null && employeeId.equals(handoverEmployeeId)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "工作交接人不能是离职员工本人");
        }
        employeeLifecycleService.requireEmployee(handoverEmployeeId);
    }

    /** PRD §5.4：仅 HR / 系统管理员可发起正式离职 */
    private void requireHrOrAdmin() {
        LoginUser login = SecurityUtils.getLoginUser();
        if (login == null
                || (!login.hasRole("HR_STAFF") && !login.hasRole("SYS_ADMIN"))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅 HR/管理员可发起正式离职");
        }
    }

    /** 部门负责人 → HR：写入真实 assigneeUserId，供审批中心按 JWT 用户可见 */
    private List<ProcessNodeDef> buildResignationNodes(Employee emp, long initiatorUserId) {
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

    public PageResult<LifecycleDtos.ResignationVO> listResignations(int page, int pageSize, String status) {
        LambdaQueryWrapper<ResignationApplication> q = new LambdaQueryWrapper<ResignationApplication>()
                .orderByDesc(ResignationApplication::getId);
        if (status != null && !status.isBlank()) {
            q.eq(ResignationApplication::getStatus, status.toUpperCase(Locale.ROOT));
        }
        List<ResignationApplication> all = resignationMapper.selectList(q);
        int p = page <= 0 ? 1 : page;
        int size = pageSize <= 0 ? 20 : Math.min(pageSize, 100);
        int from = Math.min((p - 1) * size, all.size());
        int to = Math.min(from + size, all.size());
        List<LifecycleDtos.ResignationVO> list = all.subList(from, to).stream()
                .map(this::toResignVo)
                .collect(Collectors.toList());
        return PageResult.of(list, all.size(), p, size);
    }

    public LifecycleDtos.ResignationVO resignationDetail(Long id) {
        ResignationApplication app = resignationMapper.selectById(id);
        if (app == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "离职单不存在");
        }
        return toResignVo(app);
    }

    public LifecycleDtos.ResignationStatsVO stats() {
        LifecycleDtos.ResignationStatsVO vo = new LifecycleDtos.ResignationStatsVO();
        vo.setPendingRequest(requestMapper.selectCount(new LambdaQueryWrapper<EmployeeResignationRequest>()
                .eq(EmployeeResignationRequest::getStatus, "PENDING")));
        vo.setApproving(resignationMapper.selectCount(new LambdaQueryWrapper<ResignationApplication>()
                .eq(ResignationApplication::getStatus, "APPROVING")));
        vo.setPendingResign(resignationMapper.selectCount(new LambdaQueryWrapper<ResignationApplication>()
                .eq(ResignationApplication::getStatus, "PENDING_RESIGN")));
        YearMonth ym = YearMonth.now();
        LocalDateTime monthStart = ym.atDay(1).atStartOfDay();
        vo.setResignedThisMonth(resignationMapper.selectCount(new LambdaQueryWrapper<ResignationApplication>()
                .eq(ResignationApplication::getStatus, "RESIGNED")
                .ge(ResignationApplication::getCreatedAt, monthStart)));
        return vo;
    }

    @Transactional
    public void onRequestApproved(Long requestId) {
        EmployeeResignationRequest app = requestMapper.selectById(requestId);
        if (app == null) {
            return;
        }
        requireStatus(app.getStatus(), "PENDING", "APPROVING");
        app.setStatus("APPROVED");
        app.setUpdatedAt(LocalDateTime.now());
        requestMapper.updateById(app);
    }

    @Transactional
    public void onRequestRejectedOrWithdrawn(Long requestId, boolean withdrawn) {
        EmployeeResignationRequest app = requestMapper.selectById(requestId);
        if (app == null) {
            return;
        }
        requireStatus(app.getStatus(), "PENDING", "APPROVING");
        app.setStatus(withdrawn ? "CANCELLED" : "REJECTED");
        app.setUpdatedAt(LocalDateTime.now());
        requestMapper.updateById(app);
    }

    @Transactional
    public void onResignationApproved(Long appId) {
        ResignationApplication app = resignationMapper.selectById(appId);
        if (app == null) {
            return;
        }
        requireStatus(app.getStatus(), "PENDING", "APPROVING");
        if (app.getHandoverEmployeeId() == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "离职单缺少工作交接人，无法进入待离职");
        }
        Employee emp = employeeLifecycleService.requireEmployee(app.getEmployeeId());
        // 员工已离职：同步单状态，并补齐账号禁用（避免手工改 40 后仍可登录）
        if (emp.getEmploymentStatus() != null && emp.getEmploymentStatus() == 40) {
            app.setStatus("RESIGNED");
            resignationMapper.updateById(app);
            try {
                employeeLifecycleService.effectResign(app.getEmployeeId());
            } catch (Exception e) {
                log.warn("已离职员工补齐账号禁用失败 appId={}: {}", appId, e.getMessage());
            }
            log.info("员工已离职，同步正式离职单为 RESIGNED appId={}", appId);
            return;
        }
        employeeLifecycleService.markPendingResign(app.getEmployeeId(), app.getResignationDate());
        app.setStatus("PENDING_RESIGN");
        resignationMapper.updateById(app);

        // 离职日已到（含当天）：立即生效，不必等次日 00:05 Job
        LocalDate resignDate = app.getResignationDate();
        if (resignDate != null && !resignDate.isAfter(LocalDate.now())) {
            try {
                employeeLifecycleService.effectResign(app.getEmployeeId());
                app.setStatus("RESIGNED");
                resignationMapper.updateById(app);
                notifyPublisher.publishResignationEffected(app.getEmployeeId());
                log.info("离职日已到，终审后立即生效 appId={} employeeId={}", appId, app.getEmployeeId());
            } catch (Exception e) {
                log.warn("终审后立即生效失败，将由 Job 重试 appId={}: {}", appId, e.getMessage());
            }
        }
    }

    @Transactional
    public void onResignationRejectedOrWithdrawn(Long appId, boolean withdrawn) {
        ResignationApplication app = resignationMapper.selectById(appId);
        if (app == null) {
            return;
        }
        requireStatus(app.getStatus(), "PENDING", "APPROVING");
        app.setStatus(withdrawn ? "CANCELLED" : "REJECTED");
        resignationMapper.updateById(app);
    }

    /** Job：到期生效；并补齐「员工已离职但账号未禁」的脏数据 */
    @Transactional
    public int effectDueResignations(LocalDate today) {
        List<ResignationApplication> due = resignationMapper.selectList(new LambdaQueryWrapper<ResignationApplication>()
                .eq(ResignationApplication::getStatus, "PENDING_RESIGN")
                .le(ResignationApplication::getResignationDate, today));
        int count = 0;
        for (ResignationApplication app : due) {
            try {
                employeeLifecycleService.effectResign(app.getEmployeeId());
                app.setStatus("RESIGNED");
                resignationMapper.updateById(app);
                notifyPublisher.publishResignationEffected(app.getEmployeeId());
                count++;
            } catch (Exception e) {
                log.warn("离职生效失败 appId={} employeeId={}: {}",
                        app.getId(), app.getEmployeeId(), e.getMessage());
            }
        }
        // 正式离职单已是 RESIGNED，但可能只改了员工状态、账号仍启用 → 补禁
        List<ResignationApplication> resigned = resignationMapper.selectList(new LambdaQueryWrapper<ResignationApplication>()
                .eq(ResignationApplication::getStatus, "RESIGNED"));
        for (ResignationApplication app : resigned) {
            try {
                employeeLifecycleService.effectResign(app.getEmployeeId());
            } catch (Exception e) {
                log.warn("已离职账号补禁失败 appId={} employeeId={}: {}",
                        app.getId(), app.getEmployeeId(), e.getMessage());
            }
        }
        return count;
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

    private LifecycleDtos.ResignationRequestVO createRequest(Long employeeId, LifecycleDtos.ResignationRequestCreate req) {
        if (req == null || req.getExpectedResignDate() == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "expectedResignDate 必填");
        }
        LocalDate expected = LocalDate.parse(req.getExpectedResignDate());
        if (expected.isBefore(LocalDate.now())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "期望离职日须 ≥ 今天");
        }
        if (req.getReasonCategory() == null || req.getResignationType() == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "reasonCategory / resignationType 必填");
        }

        Employee emp = employeeLifecycleService.requireEmployee(employeeId);
        long pending = requestMapper.selectCount(new LambdaQueryWrapper<EmployeeResignationRequest>()
                .eq(EmployeeResignationRequest::getEmployeeId, employeeId)
                .eq(EmployeeResignationRequest::getStatus, "PENDING"));
        if (pending > 0) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "已有进行中的离职申请");
        }

        long userId = currentUserProvider.requireUserId();
        LocalDateTime now = LocalDateTime.now();
        EmployeeResignationRequest app = new EmployeeResignationRequest();
        app.setEmployeeId(employeeId);
        app.setStatus("PENDING");
        app.setExpectedResignDate(expected);
        app.setReasonCategory(req.getReasonCategory());
        app.setResignationType(req.getResignationType());
        app.setReasonDetail(req.getReasonDetail());
        app.setCreatedAt(now);
        app.setUpdatedAt(now);
        requestMapper.insert(app);

        List<ProcessNodeDef> nodes = buildResignationRequestNodes(emp, userId);
        Long instanceId = dbApprovalService.createInstance(
                "RESIGNATION_REQUEST",
                String.valueOf(app.getId()),
                userId,
                nodes,
                DbApprovalService.InstanceDisplay.of(
                        emp.getName() + "离职申请",
                        emp.getName(),
                        null,
                        "RR-" + app.getId()),
                null);
        app.setInstanceId(instanceId);
        requestMapper.updateById(app);
        return toRequestVo(app);
    }

    /** 员工离职申请：由 HR 审批（写入真实 assigneeUserId） */
    private List<ProcessNodeDef> buildResignationRequestNodes(Employee emp, long initiatorUserId) {
        Long hrUserId = employeeLifecycleService.resolveHrApproverUserId(initiatorUserId);
        ProcessNodeDef n1 = new ProcessNodeDef();
        n1.setOrder(1);
        n1.setLabel("HR 审批");
        n1.setAssigneeType("HR_STAFF");
        n1.setAssigneeUserId(hrUserId);
        return List.of(n1);
    }

    private Long requireSelfEmployeeId() {
        LoginUser login = SecurityUtils.getLoginUser();
        if (login != null && login.getEmployeeId() != null) {
            return login.getEmployeeId();
        }
        // 开发兜底：用 X-User-Id 当 employeeId（联调种子）
        return currentUserProvider.requireUserId();
    }

    private PageResult<LifecycleDtos.ResignationRequestVO> pageRequests(
            List<EmployeeResignationRequest> all, int page, int pageSize) {
        int p = page <= 0 ? 1 : page;
        int size = pageSize <= 0 ? 20 : Math.min(pageSize, 100);
        int from = Math.min((p - 1) * size, all.size());
        int to = Math.min(from + size, all.size());
        List<LifecycleDtos.ResignationRequestVO> list = all.subList(from, to).stream()
                .map(this::toRequestVo)
                .collect(Collectors.toList());
        return PageResult.of(list, all.size(), p, size);
    }

    private LifecycleDtos.ResignationRequestVO toRequestVo(EmployeeResignationRequest app) {
        LifecycleDtos.ResignationRequestVO vo = new LifecycleDtos.ResignationRequestVO();
        vo.setId(app.getId());
        vo.setEmployeeId(app.getEmployeeId());
        try {
            vo.setEmployeeName(employeeLifecycleService.requireEmployee(app.getEmployeeId()).getName());
        } catch (Exception ignored) {
        }
        vo.setStatus(app.getStatus());
        vo.setExpectedResignDate(app.getExpectedResignDate() == null ? null : app.getExpectedResignDate().toString());
        vo.setReasonCategory(app.getReasonCategory());
        vo.setResignationType(app.getResignationType());
        vo.setReasonDetail(app.getReasonDetail());
        vo.setInstanceId(app.getInstanceId());
        vo.setCreatedAt(app.getCreatedAt() == null ? null : DT.format(app.getCreatedAt()));
        return vo;
    }

    private LifecycleDtos.ResignationVO toResignVo(ResignationApplication app) {
        LifecycleDtos.ResignationVO vo = new LifecycleDtos.ResignationVO();
        vo.setId(app.getId());
        vo.setRequestId(app.getRequestId());
        vo.setEmployeeId(app.getEmployeeId());
        try {
            vo.setEmployeeName(employeeLifecycleService.requireEmployee(app.getEmployeeId()).getName());
        } catch (Exception ignored) {
        }
        vo.setStatus(app.getStatus());
        vo.setResignationDate(app.getResignationDate() == null ? null : app.getResignationDate().toString());
        vo.setReasonCategory(app.getReasonCategory());
        vo.setResignationType(app.getResignationType());
        vo.setReasonDetail(app.getReasonDetail());
        vo.setHandoverEmployeeId(app.getHandoverEmployeeId());
        vo.setInstanceId(app.getInstanceId());
        vo.setCreatedAt(app.getCreatedAt() == null ? null : DT.format(app.getCreatedAt()));
        return vo;
    }
}
