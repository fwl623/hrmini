package com.company.hrms.employee.service.impl;

import com.company.hrms.common.exception.BusinessException;
import com.company.hrms.common.exception.ErrorCode;
import com.company.hrms.employee.dto.OnboardingArchiveCommand;
import com.company.hrms.employee.entity.Employee;
import com.company.hrms.employee.entity.EmployeeContract;
import com.company.hrms.employee.entity.EmployeePersonal;
import com.company.hrms.employee.mapper.EmployeeContractMapper;
import com.company.hrms.employee.mapper.EmployeeMapper;
import com.company.hrms.employee.mapper.EmployeePersonalMapper;
import com.company.hrms.employee.service.OnboardingService;
import com.company.hrms.module.auth.dto.InternalCreateUserRequest;
import com.company.hrms.module.auth.service.InternalUserService;
import com.company.hrms.module.org.entity.Department;
import com.company.hrms.module.org.mapper.DepartmentMapper;
import com.company.hrms.module.org.service.EmployeeIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OnboardingServiceImpl implements OnboardingService {

    /** 开发种子账套；无种子时合同仍写入该 ID（表无物理 FK） */
    private static final long DEFAULT_SCHEME_ID = 1L;

    private final EmployeeMapper employeeMapper;
    private final EmployeePersonalMapper employeePersonalMapper;
    private final EmployeeContractMapper employeeContractMapper;
    private final InternalUserService internalUserService;
    private final EmployeeIdGenerator employeeIdGenerator;
    private final DepartmentMapper departmentMapper;

    @Override
    @Transactional
    public Long confirm(OnboardingArchiveCommand cmd) {
        if (cmd == null || cmd.getApplicationId() == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "applicationId 必填");
        }
        if (cmd.getName() == null || cmd.getName().isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "姓名不能为空");
        }
        if (cmd.getMobile() == null || !cmd.getMobile().matches("^1\\d{10}$")) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "手机号无效");
        }
        if (employeeMapper.selectByMobile(cmd.getMobile()) != null) {
            throw new BusinessException(ErrorCode.MOBILE_DUPLICATE);
        }

        LocalDate hireDate = cmd.getActualOnboardDate() != null
                ? cmd.getActualOnboardDate()
                : (cmd.getExpectedOnboardDate() != null ? cmd.getExpectedOnboardDate() : LocalDate.now());
        int probationMonths = cmd.getProbationMonths() == null || cmd.getProbationMonths() <= 0
                ? 3 : cmd.getProbationMonths();

        String deptCode = resolveDeptCode(cmd.getDepartmentId());
        String empNo = employeeIdGenerator.generate(deptCode);

        Employee emp = new Employee();
        emp.setEmployeeNo(empNo);
        emp.setName(cmd.getName().trim());
        emp.setGender(cmd.getGender() == null ? "MALE" : cmd.getGender());
        emp.setMobile(cmd.getMobile());
        emp.setEmail(cmd.getEmail());
        emp.setDepartmentId(cmd.getDepartmentId() == null ? 0L : cmd.getDepartmentId());
        emp.setPositionId(cmd.getPositionId() == null ? 0L : cmd.getPositionId());
        emp.setManagerId(cmd.getManagerId());
        emp.setHireDate(hireDate);
        emp.setEmploymentType(cmd.getEmploymentType() == null ? "fulltime" : cmd.getEmploymentType());
        emp.setEmploymentStatus(10);
        emp.setProbationPayRatio(cmd.getProbationSalaryRatio() != null
                ? cmd.getProbationSalaryRatio() : new BigDecimal("0.80"));
        emp.setProbationEndDate(hireDate.plusMonths(probationMonths));
        emp.setDeleted(0);
        employeeMapper.insert(emp);
        Long employeeId = emp.getId();

        employeeIdGenerator.bindEmployee(empNo, employeeId);

        String idNumber = cmd.getIdNumber() == null ? "" : cmd.getIdNumber();
        EmployeePersonal personal = new EmployeePersonal();
        personal.setEmployeeId(employeeId);
        // 开发期明文占位；正式环境应走 AES（SensitiveFieldService）
        personal.setIdNumberEnc(idNumber.isBlank() ? "PENDING" : idNumber);
        personal.setIdNumberHash(sha256(idNumber.isBlank() ? ("app-" + cmd.getApplicationId()) : idNumber));
        personal.setCreatedAt(LocalDateTime.now());
        personal.setUpdatedAt(LocalDateTime.now());
        employeePersonalMapper.insert(personal);

        EmployeeContract contract = new EmployeeContract();
        contract.setEmployeeId(employeeId);
        contract.setContractType("FIXED");
        contract.setContractExpireDate(hireDate.plusYears(3));
        contract.setProbationSalaryRatio(emp.getProbationPayRatio());
        contract.setSchemeId(DEFAULT_SCHEME_ID);
        contract.setBaseSalary(cmd.getBaseSalary() != null ? cmd.getBaseSalary() : BigDecimal.ZERO);
        contract.setCreatedAt(LocalDateTime.now());
        contract.setUpdatedAt(LocalDateTime.now());
        employeeContractMapper.insert(contract);

        InternalCreateUserRequest userReq = new InternalCreateUserRequest();
        userReq.setUsername(cmd.getMobile());
        userReq.setEmployeeId(employeeId);
        userReq.setRoleCodes(List.of("EMPLOYEE"));
        Long userId = internalUserService.createUser(userReq);

        Employee userPatch = new Employee();
        userPatch.setId(employeeId);
        userPatch.setUserId(userId);
        employeeMapper.updateById(userPatch);

        // TODO: MQ hrms.employee.event 通知考勤/薪资
        log.info("入职建档完成 applicationId={} employeeId={} empNo={} userId={}",
                cmd.getApplicationId(), employeeId, empNo, userId);
        return employeeId;
    }

    /** 工号部门码须为 2 位；不足补 0，过长截断 */
    private String resolveDeptCode(Long departmentId) {
        String raw = null;
        if (departmentId != null) {
            Department dept = departmentMapper.selectById(departmentId);
            if (dept != null && StringUtils.hasText(dept.getCode())) {
                raw = dept.getCode().trim().toUpperCase();
            }
        }
        if (!StringUtils.hasText(raw)) {
            raw = departmentId == null ? "00" : String.format("%02d", Math.abs(departmentId % 100));
        }
        String alnum = raw.replaceAll("[^A-Za-z0-9]", "");
        if (alnum.length() >= 2) {
            return alnum.substring(0, 2).toUpperCase();
        }
        if (alnum.length() == 1) {
            return ("0" + alnum).toUpperCase();
        }
        return "00";
    }

    private static String sha256(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(dig);
        } catch (Exception e) {
            return Integer.toHexString(raw.hashCode());
        }
    }
}
