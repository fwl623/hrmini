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
import com.company.hrms.workflow.notify.ApprovalNotifyPublisher;
import com.company.hrms.workflow.support.AssigneeResolver;
import com.company.hrms.workflow.support.CurrentUserProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
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

    /** 门户：当前登录员工发起离职申请（SELF） */
    @Transactional
    public LifecycleDtos.ResignationRequestVO createMyRequest(LifecycleDtos.ResignationRequestCreate req) {
        Long employeeId = requireSelfEmployeeId();
        return createRequest(employeeId, req);
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
        if (req == null || req.getEmployeeId() == null || req.getRequestId() == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "employeeId / requestId 必填");
        }
        if (req.getResignationDate() == null || req.getHandoverEmployeeId() == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "resignationDate / handoverEmployeeId 必填");
        }
        LocalDate resignDate = LocalDate.parse(req.getResignationDate());
        if (resignDate.isBefore(LocalDate.now())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "离职日须 ≥ 今天");
        }

        EmployeeResignationRequest request = requestMapper.selectById(req.getRequestId());
        if (request == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "员工离职申请不存在");
        }
        if (!"APPROVED".equalsIgnoreCase(request.getStatus())) {
            throw new BusinessException(ErrorCode.APPROVAL_STATE_INVALID, "须关联已批准的员工离职申请");
        }
        if (!req.getEmployeeId().equals(request.getEmployeeId())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "employeeId 与 requestId 不匹配");
        }

        employeeLifecycleService.requireEmployee(req.getHandoverEmployeeId());
        Employee emp = employeeLifecycleService.requireEmployee(req.getEmployeeId());
        long userId = currentUserProvider.requireUserId();

        ResignationApplication app = new ResignationApplication();
        app.setRequestId(req.getRequestId());
        app.setEmployeeId(req.getEmployeeId());
        app.setStatus("APPROVING");
        app.setResignationDate(resignDate);
        app.setReasonCategory(req.getReasonCategory() != null ? req.getReasonCategory() : request.getReasonCategory());
        app.setResignationType(req.getResignationType() != null ? req.getResignationType() : request.getResignationType());
        app.setReasonDetail(req.getReasonDetail() != null ? req.getReasonDetail() : request.getReasonDetail());
        app.setHandoverEmployeeId(req.getHandoverEmployeeId());
        app.setCreatedBy(userId);
        app.setCreatedAt(LocalDateTime.now());
        resignationMapper.insert(app);

        Long instanceId = dbApprovalService.createInstance(
                "RESIGNATION",
                String.valueOf(app.getId()),
                userId,
                AssigneeResolver.resignationNodes(),
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
        employeeLifecycleService.markPendingResign(app.getEmployeeId(), app.getResignationDate());
        app.setStatus("PENDING_RESIGN");
        resignationMapper.updateById(app);
    }

    @Transactional
    public void onResignationRejectedOrWithdrawn(Long appId, boolean withdrawn) {
        ResignationApplication app = resignationMapper.selectById(appId);
        if (app == null) {
            return;
        }
        app.setStatus(withdrawn ? "CANCELLED" : "REJECTED");
        resignationMapper.updateById(app);
    }

    /** Job：到期生效 */
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
        return count;
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

        Long instanceId = dbApprovalService.createInstance(
                "RESIGNATION_REQUEST",
                String.valueOf(app.getId()),
                userId,
                AssigneeResolver.resignationRequestNodes(),
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
