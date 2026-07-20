package com.company.hrms.employee.service.impl;

import com.company.hrms.common.event.EmployeeStatusChangeEvent;
import com.company.hrms.common.exception.BusinessException;
import com.company.hrms.common.exception.ErrorCode;
import com.company.hrms.employee.dto.TransferEffectDTO;
import com.company.hrms.employee.entity.Employee;
import com.company.hrms.employee.entity.EmployeeSalaryHistory;
import com.company.hrms.employee.entity.EmployeeSalaryProfile;
import com.company.hrms.employee.entity.EmployeeTransferHistory;
import com.company.hrms.employee.mapper.EmployeeMapper;
import com.company.hrms.employee.mapper.EmployeeSalaryHistoryMapper;
import com.company.hrms.employee.mapper.EmployeeSalaryProfileMapper;
import com.company.hrms.employee.mapper.EmployeeTransferHistoryMapper;
import com.company.hrms.employee.service.EmployeeLifecycleService;
import com.company.hrms.employee.vo.PendingRegularizationVO;
import com.company.hrms.module.auth.mapper.SysUserMapper;
import com.company.hrms.module.auth.service.InternalUserService;
import com.company.hrms.module.org.entity.Department;
import com.company.hrms.module.org.mapper.DepartmentMapper;
import com.company.hrms.module.org.service.EmployeeIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeLifecycleServiceImpl implements EmployeeLifecycleService {

    private static final int STATUS_PROBATION = 10;
    private static final int STATUS_REGULAR = 20;
    private static final int STATUS_PENDING_RESIGN = 30;
    private static final int STATUS_RESIGNED = 40;

    private final EmployeeMapper employeeMapper;
    private final EmployeeTransferHistoryMapper transferHistoryMapper;
    private final EmployeeSalaryProfileMapper salaryProfileMapper;
    private final EmployeeSalaryHistoryMapper salaryHistoryMapper;
    private final InternalUserService internalUserService;
    private final EmployeeIdGenerator employeeIdGenerator;
    private final ApplicationEventPublisher eventPublisher;
    private final DepartmentMapper departmentMapper;
    private final SysUserMapper sysUserMapper;

    @Override
    public Employee requireEmployee(Long employeeId) {
        Employee emp = employeeMapper.selectById(employeeId);
        if (emp == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "员工不存在: " + employeeId);
        }
        return emp;
    }

    @Override
    public Long resolveDeptManagerUserId(Long employeeId) {
        Employee emp = requireEmployee(employeeId);
        // 1) 直属上级
        Long fromManager = userIdOfEmployee(emp.getManagerId());
        if (fromManager != null) {
            return fromManager;
        }
        // 2) 沿部门树找负责人（跳过本人）
        Long deptId = emp.getDepartmentId();
        Set<Long> visited = new HashSet<>();
        while (deptId != null && visited.add(deptId)) {
            Department dept = departmentMapper.selectById(deptId);
            if (dept == null || (dept.getDeleted() != null && dept.getDeleted() == 1)) {
                break;
            }
            Long headEmpId = dept.getHeadEmployeeId();
            if (headEmpId != null && !headEmpId.equals(emp.getId())) {
                Long userId = userIdOfEmployee(headEmpId);
                if (userId != null) {
                    return userId;
                }
            }
            deptId = dept.getParentId();
        }
        // 3) 回退：任意 DEPT_MANAGER 角色
        List<Long> managers = sysUserMapper.selectUserIdsByRoleCode("DEPT_MANAGER");
        if (managers != null) {
            for (Long uid : managers) {
                if (uid != null && !Objects.equals(uid, emp.getUserId())) {
                    return uid;
                }
            }
            if (!managers.isEmpty() && managers.get(0) != null) {
                return managers.get(0);
            }
        }
        throw new BusinessException(ErrorCode.PARAM_INVALID,
                "无法解析部门负责人审批人，请为员工设置直属上级或部门负责人");
    }

    @Override
    public Long resolveHrApproverUserId(Long excludeUserId) {
        List<Long> hrs = sysUserMapper.selectUserIdsByRoleCode("HR_STAFF");
        if (hrs == null || hrs.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "系统中无 HR_STAFF 用户，无法派发 HR 审批");
        }
        for (Long uid : hrs) {
            if (uid != null && !Objects.equals(uid, excludeUserId)) {
                return uid;
            }
        }
        return hrs.get(0);
    }

    @Override
    public Long resolveDeptHeadUserIdByDeptId(Long departmentId) {
        if (departmentId == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "部门 ID 不能为空");
        }
        Long deptId = departmentId;
        Set<Long> visited = new HashSet<>();
        while (deptId != null && visited.add(deptId)) {
            Department dept = departmentMapper.selectById(deptId);
            if (dept == null || (dept.getDeleted() != null && dept.getDeleted() == 1)) {
                break;
            }
            Long headEmpId = dept.getHeadEmployeeId();
            if (headEmpId != null) {
                Long userId = userIdOfEmployee(headEmpId);
                if (userId != null) {
                    return userId;
                }
            }
            deptId = dept.getParentId();
        }
        List<Long> managers = sysUserMapper.selectUserIdsByRoleCode("DEPT_MANAGER");
        if (managers != null && !managers.isEmpty() && managers.get(0) != null) {
            return managers.get(0);
        }
        throw new BusinessException(ErrorCode.PARAM_INVALID,
                "无法解析新部门负责人审批人，请为部门设置负责人");
    }

    @Override
    public Long resolveFinanceApproverUserId(Long excludeUserId) {
        List<Long> finance = sysUserMapper.selectUserIdsByRoleCode("FINANCE_MANAGER");
        if (finance == null || finance.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "系统中无 FINANCE_MANAGER（财务经理）用户，含调薪的调岗无法派发财务审批");
        }
        for (Long uid : finance) {
            if (uid != null && !Objects.equals(uid, excludeUserId)) {
                return uid;
            }
        }
        return finance.get(0);
    }

    private Long userIdOfEmployee(Long employeeId) {
        if (employeeId == null) {
            return null;
        }
        Employee e = employeeMapper.selectById(employeeId);
        if (e == null || e.getUserId() == null) {
            return null;
        }
        return e.getUserId();
    }

    @Override
    public List<PendingRegularizationVO> listPendingRegularization(LocalDate from, LocalDate to) {
        return employeeMapper.listPendingRegularization(from, to).stream()
                .map(this::toPendingVo)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void regularizePass(Long employeeId) {
        Employee emp = requireEmployee(employeeId);
        if (emp.getEmploymentStatus() == null || emp.getEmploymentStatus() != STATUS_PROBATION) {
            throw new BusinessException(ErrorCode.EMPLOYEE_STATUS_INVALID, "仅试用期员工可转正");
        }
        Employee patch = new Employee();
        patch.setId(employeeId);
        patch.setEmploymentStatus(STATUS_REGULAR);
        employeeMapper.updateById(patch);
        log.info("员工转正 PASS employeeId={}", employeeId);
    }

    @Override
    @Transactional
    public void regularizeExtend(Long employeeId, int extendMonths) {
        if (extendMonths <= 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "延长月数须 > 0");
        }
        Employee emp = requireEmployee(employeeId);
        if (emp.getEmploymentStatus() == null || emp.getEmploymentStatus() != STATUS_PROBATION) {
            throw new BusinessException(ErrorCode.EMPLOYEE_STATUS_INVALID, "仅试用期员工可延长试用");
        }
        LocalDate base = emp.getProbationEndDate() != null ? emp.getProbationEndDate() : LocalDate.now();
        Employee patch = new Employee();
        patch.setId(employeeId);
        patch.setProbationEndDate(base.plusMonths(extendMonths));
        employeeMapper.updateById(patch);
        log.info("员工延长试用 employeeId={} months={} newEnd={}", employeeId, extendMonths, patch.getProbationEndDate());
    }

    @Override
    @Transactional
    public void applyTransfer(Long employeeId, TransferEffectDTO dto) {
        Employee emp = requireEmployee(employeeId);
        int status = emp.getEmploymentStatus() == null ? -1 : emp.getEmploymentStatus();
        if (status != STATUS_PROBATION && status != STATUS_REGULAR) {
            throw new BusinessException(ErrorCode.EMPLOYEE_STATUS_INVALID, "仅试用/正式员工可调岗");
        }
        if (dto.getNewDepartmentId() == null || dto.getNewDepartmentId() <= 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "新部门不能为空");
        }
        if (dto.getNewDepartmentId().equals(emp.getDepartmentId())) {
            throw new BusinessException(ErrorCode.TRANSFER_DEPT_UNCHANGED);
        }
        if (dto.getNewPositionId() != null && dto.getNewPositionId() <= 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "新职位 ID 无效");
        }

        EmployeeTransferHistory history = new EmployeeTransferHistory();
        history.setEmployeeId(employeeId);
        history.setTransferAppId(dto.getTransferAppId());
        history.setFromDepartmentId(emp.getDepartmentId());
        history.setToDepartmentId(dto.getNewDepartmentId());
        history.setFromPositionId(emp.getPositionId());
        history.setToPositionId(dto.getNewPositionId() != null ? dto.getNewPositionId() : emp.getPositionId());
        history.setTransferDate(dto.getEffectiveDate() != null ? dto.getEffectiveDate() : LocalDate.now());
        history.setReason(dto.getReason());
        transferHistoryMapper.insert(history);

        Employee patch = new Employee();
        patch.setId(employeeId);
        patch.setDepartmentId(dto.getNewDepartmentId());
        if (dto.getNewPositionId() != null) {
            patch.setPositionId(dto.getNewPositionId());
        }
        if (dto.getNewJobLevel() != null && !dto.getNewJobLevel().isBlank()) {
            patch.setGrade(dto.getNewJobLevel());
        }
        if (dto.getNewManagerId() != null) {
            patch.setManagerId(dto.getNewManagerId());
        }
        employeeMapper.updateById(patch);

        if (dto.getNewBaseSalary() != null) {
            applyTransferSalary(employeeId, dto.getNewBaseSalary(), dto.getEffectiveDate());
        }
        log.info("员工调岗生效 employeeId={} toDept={}", employeeId, dto.getNewDepartmentId());
    }

    private void applyTransferSalary(Long employeeId, BigDecimal newBaseSalary, LocalDate effectiveDate) {
        if (newBaseSalary.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "调岗后基本工资须大于 0");
        }
        EmployeeSalaryProfile profile = salaryProfileMapper.selectByEmployeeId(employeeId);
        if (profile == null) {
            throw new BusinessException(ErrorCode.SALARY_PROFILE_MISSING, "员工无薪资档案，无法调岗调薪");
        }
        if (newBaseSalary.equals(profile.getBaseSalary())) {
            return;
        }
        EmployeeSalaryHistory history = new EmployeeSalaryHistory();
        history.setEmployeeId(employeeId);
        history.setFieldName("baseSalary");
        history.setOldValue(profile.getBaseSalary());
        history.setNewValue(newBaseSalary);
        history.setEffectiveDate(effectiveDate != null ? effectiveDate : LocalDate.now());
        history.setReason("调岗调薪");
        try {
            history.setOperatorId(com.company.hrms.common.security.SecurityUtils.getUserId());
        } catch (Exception ignored) {
            history.setOperatorId(null);
        }
        salaryHistoryMapper.insert(history);
        profile.setBaseSalary(newBaseSalary);
        salaryProfileMapper.updateById(profile);
        log.info("调岗调薪生效 employeeId={} newBase={}", employeeId, newBaseSalary);
    }

    @Override
    @Transactional
    public void markPendingResign(Long employeeId, LocalDate resignationDate) {
        Employee emp = requireEmployee(employeeId);
        int status = emp.getEmploymentStatus() == null ? -1 : emp.getEmploymentStatus();
        if (status == STATUS_RESIGNED) {
            throw new BusinessException(ErrorCode.EMPLOYEE_STATUS_INVALID, "员工已离职，无法再次进入离职流程");
        }
        // 已是待离职：幂等（允许 HR 终审补完审批单状态，避免重复抛错）
        if (status == STATUS_PENDING_RESIGN) {
            if (resignationDate != null
                    && (emp.getLastWorkDay() == null || !resignationDate.equals(emp.getLastWorkDay()))) {
                Employee patch = new Employee();
                patch.setId(employeeId);
                patch.setLastWorkDay(resignationDate);
                employeeMapper.updateById(patch);
            }
            log.info("员工已是待离职，跳过重复标记 employeeId={}", employeeId);
            return;
        }
        Employee patch = new Employee();
        patch.setId(employeeId);
        patch.setEmploymentStatus(STATUS_PENDING_RESIGN);
        if (resignationDate != null) {
            patch.setLastWorkDay(resignationDate);
        }
        employeeMapper.updateById(patch);
        log.info("员工标记待离职 employeeId={} date={}", employeeId, resignationDate);
    }

    @Override
    @Transactional
    public void effectResign(Long employeeId) {
        Employee emp = requireEmployee(employeeId);
        // 已离职：仍确保账号禁用（避免只改了员工状态、账号仍可登录）
        if (emp.getEmploymentStatus() != null && emp.getEmploymentStatus() == STATUS_RESIGNED) {
            ensureUserDisabled(emp);
            log.info("员工已是离职状态，补齐账号禁用后跳过 employeeId={}", employeeId);
            return;
        }
        if (emp.getEmploymentStatus() == null || emp.getEmploymentStatus() != STATUS_PENDING_RESIGN) {
            throw new BusinessException(ErrorCode.EMPLOYEE_STATUS_INVALID, "仅待离职员工可生效离职");
        }
        LocalDate lastWorkDay = emp.getLastWorkDay() != null ? emp.getLastWorkDay() : LocalDate.now();
        Employee patch = new Employee();
        patch.setId(employeeId);
        patch.setEmploymentStatus(STATUS_RESIGNED);
        if (emp.getLastWorkDay() == null) {
            patch.setLastWorkDay(lastWorkDay);
        }
        employeeMapper.updateById(patch);

        ensureUserDisabled(emp);
        if (StringUtils.hasText(emp.getEmployeeNo())) {
            try {
                employeeIdGenerator.release(emp.getEmployeeNo());
            } catch (Exception e) {
                log.warn("离职释放工号失败 employeeId={} empNo={}: {}", employeeId, emp.getEmployeeNo(), e.getMessage());
            }
        }

        eventPublisher.publishEvent(new EmployeeStatusChangeEvent(
                this,
                employeeId,
                String.valueOf(STATUS_PENDING_RESIGN),
                String.valueOf(STATUS_RESIGNED),
                LocalDate.now(),
                "RESIGNATION_EFFECT",
                lastWorkDay));
        log.info("员工离职生效 employeeId={} userId={} empNo={} lastWorkDay={}",
                employeeId, emp.getUserId(), emp.getEmployeeNo(), lastWorkDay);
    }

    private void ensureUserDisabled(Employee emp) {
        if (emp.getUserId() == null) {
            log.warn("离职员工未绑定账号，无法禁用登录 employeeId={}", emp.getId());
            return;
        }
        try {
            internalUserService.updateStatus(emp.getUserId(), 0);
        } catch (Exception e) {
            log.warn("离职禁用账号失败 employeeId={} userId={}: {}", emp.getId(), emp.getUserId(), e.getMessage());
        }
    }

    private PendingRegularizationVO toPendingVo(Employee emp) {
        PendingRegularizationVO vo = new PendingRegularizationVO();
        vo.setEmployeeId(emp.getId());
        vo.setEmpNo(emp.getEmployeeNo());
        vo.setName(emp.getName());
        vo.setDepartmentId(emp.getDepartmentId());
        vo.setPositionId(emp.getPositionId());
        vo.setHireDate(emp.getHireDate());
        vo.setProbationEndDate(emp.getProbationEndDate());
        vo.setEmploymentStatus("probation");
        return vo;
    }
}
