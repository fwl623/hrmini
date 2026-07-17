package com.company.hrms.employee.service.impl;

import com.company.hrms.common.exception.BusinessException;
import com.company.hrms.common.exception.ErrorCode;
import com.company.hrms.employee.dto.TransferEffectDTO;
import com.company.hrms.employee.entity.Employee;
import com.company.hrms.employee.entity.EmployeeTransferHistory;
import com.company.hrms.employee.mapper.EmployeeMapper;
import com.company.hrms.employee.mapper.EmployeeTransferHistoryMapper;
import com.company.hrms.employee.service.EmployeeLifecycleService;
import com.company.hrms.employee.vo.PendingRegularizationVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        Employee patch = new Employee();
        patch.setId(employeeId);
        patch.setEmploymentStatus(STATUS_RESIGNED);
        employeeMapper.updateById(patch);
        // TODO: 禁用账号、释放工号、移出考勤组（依赖 auth / attendance 联动）
        log.info("员工离职生效 employeeId={}（账号禁用/工号释放/考勤移出 TODO）", employeeId);
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
