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
import com.company.hrms.workflow.mapper.TransferApplicationMapper;
import com.company.hrms.workflow.model.ProcessNodeDef;
import com.company.hrms.workflow.support.AssigneeResolver;
import com.company.hrms.workflow.support.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class TransferService {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final TransferApplicationMapper mapper;
    private final ApprovalInstanceMapper instanceMapper;
    private final EmployeeLifecycleService employeeLifecycleService;
    private final DbApprovalService dbApprovalService;
    private final CurrentUserProvider currentUserProvider;

    public TransferService(TransferApplicationMapper mapper,
                           ApprovalInstanceMapper instanceMapper,
                           EmployeeLifecycleService employeeLifecycleService,
                           DbApprovalService dbApprovalService,
                           CurrentUserProvider currentUserProvider) {
        this.mapper = mapper;
        this.instanceMapper = instanceMapper;
        this.employeeLifecycleService = employeeLifecycleService;
        this.dbApprovalService = dbApprovalService;
        this.currentUserProvider = currentUserProvider;
    }

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

    public LifecycleDtos.TransferVO detail(Long id) {
        requireHrOrAdmin();
        TransferApplication app = mapper.selectById(id);
        if (app == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "调岗申请不存在");
        }
        return toVo(app, true);
    }

    @Transactional
    public LifecycleDtos.TransferVO create(LifecycleDtos.TransferCreateRequest req) {
        // TC-TRF-004 / BUG-008：仅 HR/管理员可发起调岗，前端藏菜单不够
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

        Employee emp = employeeLifecycleService.requireEmployee(req.getEmployeeId());
        int st = emp.getEmploymentStatus() == null ? -1 : emp.getEmploymentStatus();
        if (st != 10 && st != 20) {
            throw new BusinessException(ErrorCode.EMPLOYEE_STATUS_INVALID, "仅试用/正式员工可调岗");
        }
        if (req.getNewDepartmentId().equals(emp.getDepartmentId())) {
            throw new BusinessException(ErrorCode.TRANSFER_DEPT_UNCHANGED);
        }

        long userId = currentUserProvider.requireUserId();
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

        Long instanceId = dbApprovalService.createInstance(
                "TRANSFER",
                String.valueOf(app.getId()),
                userId,
                AssigneeResolver.transferNodes(),
                DbApprovalService.InstanceDisplay.of(
                        emp.getName() + "调岗申请",
                        emp.getName(),
                        null,
                        "TR-" + app.getId()),
                null);
        app.setInstanceId(instanceId);
        mapper.updateById(app);
        return toVo(app, true);
    }

    @Transactional
    public void onApproved(Long appId) {
        TransferApplication app = mapper.selectById(appId);
        if (app == null) {
            return;
        }
        requireStatus(app.getStatus(), "APPROVING", "PENDING");
        TransferEffectDTO dto = new TransferEffectDTO();
        dto.setTransferAppId(app.getId());
        dto.setNewDepartmentId(app.getNewDepartmentId());
        dto.setNewPositionId(app.getNewPositionId());
        dto.setNewJobLevel(app.getNewJobLevel());
        dto.setNewManagerId(app.getNewManagerId());
        dto.setEffectiveDate(app.getEffectiveDate());
        dto.setReason(app.getReason());
        employeeLifecycleService.applyTransfer(app.getEmployeeId(), dto);
        app.setStatus("APPROVED");
        mapper.updateById(app);
    }

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
            List<ProcessNodeDef> defs = AssigneeResolver.transferNodes();
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
                if ("APPROVED".equalsIgnoreCase(instStatus) || "APPROVED".equalsIgnoreCase(app.getStatus())) {
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

    /** PRD / TC-TRF-004：仅 HR / 系统管理员可发起与查看调岗管理接口 */
    private void requireHrOrAdmin() {
        LoginUser login = SecurityUtils.getLoginUser();
        if (login == null
                || (!login.hasRole("HR_STAFF") && !login.hasRole("SYS_ADMIN"))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅 HR/管理员可发起调岗");
        }
    }
}
