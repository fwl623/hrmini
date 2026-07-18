package com.company.hrms.employee.service.impl;

import com.company.hrms.common.event.EmployeeStatusChangeEvent;
import com.company.hrms.common.exception.BusinessException;
import com.company.hrms.common.exception.ErrorCode;
import com.company.hrms.employee.dto.TransferEffectDTO;
import com.company.hrms.employee.entity.Employee;
import com.company.hrms.employee.entity.EmployeeTransferHistory;
import com.company.hrms.employee.mapper.EmployeeMapper;
import com.company.hrms.employee.mapper.EmployeeTransferHistoryMapper;
import com.company.hrms.employee.service.EmployeeLifecycleService;
import com.company.hrms.employee.vo.PendingRegularizationVO;
import com.company.hrms.module.auth.service.InternalUserService;
import com.company.hrms.module.org.service.EmployeeIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;
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
    private final InternalUserService internalUserService;
    private final EmployeeIdGenerator employeeIdGenerator;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public Employee requireEmployee(Long employeeId) {
        Employee emp = employeeMapper.selectById(employeeId);
        if (emp == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "员工不存在: " + employeeId);
        }
        return emp;
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
        if (dto.getNewDepartmentId() == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "新部门不能为空");
        }
        if (dto.getNewDepartmentId().equals(emp.getDepartmentId())) {
            throw new BusinessException(ErrorCode.TRANSFER_DEPT_UNCHANGED);
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
        log.info("员工调岗生效 employeeId={} toDept={}", employeeId, dto.getNewDepartmentId());
    }

    @Override
    @Transactional
    public void markPendingResign(Long employeeId, LocalDate resignationDate) {
        Employee emp = requireEmployee(employeeId);
        int status = emp.getEmploymentStatus() == null ? -1 : emp.getEmploymentStatus();
        if (status == STATUS_RESIGNED || status == STATUS_PENDING_RESIGN) {
            throw new BusinessException(ErrorCode.EMPLOYEE_STATUS_INVALID, "员工已处于离职流程");
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

        if (emp.getUserId() != null) {
            try {
                internalUserService.updateStatus(emp.getUserId(), 0);
            } catch (Exception e) {
                log.warn("离职禁用账号失败 employeeId={} userId={}: {}", employeeId, emp.getUserId(), e.getMessage());
            }
        }
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
