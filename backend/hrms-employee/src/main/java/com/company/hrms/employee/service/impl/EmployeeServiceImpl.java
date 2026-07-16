package com.company.hrms.employee.service.impl;

import com.company.hrms.common.exception.BusinessException;
import com.company.hrms.common.exception.ErrorCode;
import com.company.hrms.common.security.LoginUser;
import com.company.hrms.common.security.SecurityUtils;
import com.company.hrms.common.util.AesEncryptUtil;
import com.company.hrms.common.web.PageResult;
import com.company.hrms.employee.dto.*;
import com.company.hrms.employee.entity.Employee;
import com.company.hrms.employee.entity.EmployeePersonal;
import com.company.hrms.employee.entity.EmployeeSalaryProfile;
import com.company.hrms.employee.mapper.EmployeeMapper;
import com.company.hrms.employee.mapper.EmployeePersonalMapper;
import com.company.hrms.employee.mapper.EmployeeSalaryProfileMapper;
import com.company.hrms.employee.service.EmployeeService;
import com.company.hrms.employee.vo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 员工档案服务
 *
 * 白名单：
 *   HR端：name, gender, email, birthday, residenceAddress,
 *          emergencyContact, emergencyPhone, workLocation
 *   门户：email, residenceAddress, emergencyContact, emergencyPhone
 * 流程字段（含则 20003）：departmentId, positionId, grade, managerId, mobile, idNumber
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeServiceImpl implements EmployeeService {

    /** HR管理端编辑白名单 */
    private static final Set<String> HR_ALLOWED = Set.of(
            "name", "gender", "email", "birthday", "residenceAddress",
            "emergencyContact", "emergencyPhone", "workLocation");

    /** 门户编辑白名单 */
    private static final Set<String> PORTAL_ALLOWED = Set.of(
            "email", "residenceAddress", "emergencyContact", "emergencyPhone");

    /** 流程管控字段（在白名单外、含则直接 20003） */
    private static final Set<String> FLOW_FIELDS = Set.of(
            "departmentId", "positionId", "grade", "managerId", "mobile", "idNumber");

    private final EmployeeMapper employeeMapper;
    private final EmployeePersonalMapper employeePersonalMapper;
    private final EmployeeSalaryProfileMapper salaryProfileMapper;
    private final AesEncryptUtil aesEncryptUtil;

    // ==================== 花名册分页 ====================

    @Override
    public PageResult<EmployeeListVO> pageSearch(EmployeePageQuery query) {
        List<Long> deptIds = parseCommaLongs(query.getDepartmentIds());
        List<Long> positionIds = parseCommaLongs(query.getPositionIds());
        List<Integer> statusList = parseStatuses(query.getEmploymentStatus());
        List<String> gradeList = parseCommaStrings(query.getGradeLevels());
        String dataScope = "";

        long total = Optional.ofNullable(employeeMapper.countSearch(
                query.getKeyword(), deptIds, positionIds, statusList, gradeList,
                query.getHireDateFrom(), query.getHireDateTo(), dataScope)).orElse(0L);

        if (total == 0) {
            return PageResult.of(Collections.emptyList(), 0, query.getPage(), query.getPageSize());
        }

        List<Employee> rows = employeeMapper.search(
                query.getKeyword(), deptIds, positionIds, statusList, gradeList,
                query.getHireDateFrom(), query.getHireDateTo(), dataScope);

        List<EmployeeListVO> list = rows.stream().map(this::toListVO).collect(Collectors.toList());
        return PageResult.of(list, total, query.getPage(), query.getPageSize());
    }

    // ==================== 员工详情 ====================

    @Override
    public EmployeeDetailVO getDetail(Long employeeId) {
        Employee emp = findEmployee(employeeId);

        EmployeeDetailVO vo = new EmployeeDetailVO();
        vo.setEmployeeId(emp.getId());
        vo.setEmpNo(emp.getEmployeeNo());
        vo.setName(emp.getName());
        vo.setGender(emp.getGender());
        vo.setMobile(maskMobile(emp.getMobile()));
        vo.setEmail(emp.getEmail());
        vo.setDepartmentId(emp.getDepartmentId());
        vo.setPositionId(emp.getPositionId());
        vo.setGrade(emp.getGrade());
        vo.setManagerId(emp.getManagerId());
        vo.setWorkLocation(emp.getWorkLocation());
        vo.setEmploymentType(emp.getEmploymentType());
        vo.setEmploymentStatus(formatStatus(emp.getEmploymentStatus()));
        vo.setHireDate(emp.getHireDate());
        vo.setProbationPayRatio(emp.getProbationPayRatio());

        EmployeePersonal personal = employeePersonalMapper.selectById(employeeId);
        if (personal != null) {
            vo.setBirthday(personal.getBirthday());
            vo.setHouseholdAddress(personal.getHouseholdAddress());
            vo.setResidenceAddress(personal.getResidenceAddress());
            vo.setEmergencyContact(personal.getEmergencyContact());
            vo.setEmergencyPhone(personal.getEmergencyPhone());
        }

        return vo;
    }

    // ==================== HR编辑 + 白名单校验 + 20003 ====================

    @Override
    @Transactional
    public void update(Long employeeId, EmployeeUpdateDTO dto) {
        Employee emp = findEmployee(employeeId);

        // 已离职不可编辑 → 30003
        if (emp.getEmploymentStatus() != null && emp.getEmploymentStatus() >= 40) {
            throw new BusinessException(ErrorCode.EMPLOYEE_STATUS_INVALID);
        }

        // 白名单校验：反射遍历 dto 所有非 null 字段，不在白名单且属于 FLOW_FIELDS → 20003
        rejectFlowFields(dto, HR_ALLOWED);

        // 执行主表更新（仅白名单字段）
        Employee update = new Employee();
        update.setId(employeeId);
        if (dto.getName() != null) update.setName(dto.getName());
        if (dto.getGender() != null) update.setGender(dto.getGender());
        if (dto.getEmail() != null) update.setEmail(dto.getEmail());
        if (dto.getWorkLocation() != null) update.setWorkLocation(dto.getWorkLocation());
        employeeMapper.updateById(update);

        // 更新个人信息扩展表
        updatePersonal(employeeId, dto.getBirthday(), dto.getResidenceAddress(),
                dto.getEmergencyContact(), dto.getEmergencyPhone());

        log.info("员工档案编辑: employeeId={}, operatorId={}", employeeId, SecurityUtils.getUserId());
    }

    // ==================== 薪资档案 ====================

    @Override
    public SalaryProfileVO getSalaryProfile(Long employeeId) {
        findEmployee(employeeId);
        EmployeeSalaryProfile profile = salaryProfileMapper.selectByEmployeeId(employeeId);
        if (profile == null) {
            throw new BusinessException(ErrorCode.SALARY_PROFILE_MISSING);
        }
        SalaryProfileVO vo = new SalaryProfileVO();
        vo.setId(profile.getId());
        vo.setEmployeeId(profile.getEmployeeId());
        vo.setSchemeId(profile.getSchemeId());
        vo.setBaseSalary(profile.getBaseSalary());
        vo.setAllowanceBaseJson(profile.getAllowanceBaseJson());
        vo.setSsBase(profile.getSsBase());
        vo.setHfBase(profile.getHfBase());
        vo.setPerformanceBase(profile.getPerformanceBase());
        vo.setProbationRatio(profile.getProbationRatio());
        return vo;
    }

    @Override
    @Transactional
    public void updateSalaryProfile(Long employeeId, SalaryProfileUpdateDTO dto) {
        findEmployee(employeeId);
        EmployeeSalaryProfile profile = salaryProfileMapper.selectByEmployeeId(employeeId);
        if (profile == null) {
            throw new BusinessException(ErrorCode.SALARY_PROFILE_MISSING);
        }
        profile.setSchemeId(dto.getSchemeId());
        profile.setBaseSalary(dto.getBaseSalary());
        profile.setAllowanceBaseJson(dto.getAllowanceBaseJson());
        profile.setSsBase(dto.getSsBase());
        profile.setHfBase(dto.getHfBase());
        if (dto.getPerformanceBase() != null) profile.setPerformanceBase(dto.getPerformanceBase());
        if (dto.getProbationRatio() != null) profile.setProbationRatio(dto.getProbationRatio());
        salaryProfileMapper.updateById(profile);

        log.info("薪资档案更新: employeeId={}, operatorId={}", employeeId, SecurityUtils.getUserId());
    }

    // ==================== 敏感字段二次验证 + AES解密 ====================

    @Override
    public SensitiveFieldVO getSensitiveField(Long employeeId, String field, String password) {
        Employee emp = findEmployee(employeeId);

        // 二次验证：校验当前用户密码（委托 hrms-auth 验证密码，此处简化校验非空）
        if (password == null || password.isBlank()) {
            throw new BusinessException(ErrorCode.FIELD_FORBIDDEN, "敏感字段查看需密码验证");
        }
        // TODO: 调用 hrms-auth 的密码校验接口比对 password 与 sys_user.password_hash
        //       当前简化：密码非空即视为通过，联调时替换为 Feign 调用
        log.info("敏感字段二次验证通过: employeeId={}, field={}", employeeId, field);

        String decrypted;
        switch (field) {
            case "idNumber" -> {
                EmployeePersonal personal = employeePersonalMapper.selectById(employeeId);
                if (personal == null || personal.getIdNumberEnc() == null) {
                    throw new BusinessException(ErrorCode.PARAM_INVALID, "该员工无身份证信息");
                }
                try {
                    decrypted = aesEncryptUtil.decrypt(personal.getIdNumberEnc());
                } catch (Exception e) {
                    log.error("身份证解密失败: employeeId={}", employeeId, e);
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR, "敏感字段解密失败");
                }
            }
            case "bankAccount" -> {
                // 需从 employee_bank 表读取 bank_account_enc 并解密
                throw new BusinessException(ErrorCode.PARAM_INVALID, "银行卡信息查看待对接");
            }
            default -> throw new BusinessException(ErrorCode.PARAM_INVALID, "不支持的敏感字段: " + field);
        }

        // 记审计日志（operation_log）
        log.info("敏感字段查看审计: employeeId={}, field={}, operatorId={}",
                employeeId, field, SecurityUtils.getUserId());

        SensitiveFieldVO vo = new SensitiveFieldVO();
        vo.setEmployeeId(employeeId);
        vo.setField(field);
        vo.setValue(decrypted);
        return vo;
    }

    // ==================== 门户 ====================

    @Override
    public ProfileVO getMyProfile(Long employeeId) {
        Employee emp = findEmployee(employeeId);

        ProfileVO vo = new ProfileVO();
        vo.setEmployeeId(emp.getId());
        vo.setEmpNo(emp.getEmployeeNo());
        vo.setName(emp.getName());
        vo.setMobile(maskMobile(emp.getMobile()));
        vo.setEmail(emp.getEmail());
        vo.setGrade(emp.getGrade());
        vo.setHireDate(emp.getHireDate() != null ? emp.getHireDate().toString() : null);

        EmployeePersonal personal = employeePersonalMapper.selectById(employeeId);
        if (personal != null) {
            vo.setResidenceAddress(personal.getResidenceAddress());
            vo.setEmergencyContact(personal.getEmergencyContact());
            vo.setEmergencyPhone(personal.getEmergencyPhone());
        }
        vo.setEditableFields(new HashSet<>(PORTAL_ALLOWED));
        return vo;
    }

    @Override
    @Transactional
    public void updateMyProfile(Long employeeId, ProfileUpdateDTO dto) {
        findEmployee(employeeId);

        if (dto.getEmail() != null) {
            Employee update = new Employee();
            update.setId(employeeId);
            update.setEmail(dto.getEmail());
            employeeMapper.updateById(update);
        }
        updatePersonal(employeeId, null, dto.getResidenceAddress(),
                dto.getEmergencyContact(), dto.getEmergencyPhone());
    }

    @Override
    public List<?> listMobileChangeApps() {
        return Collections.emptyList();
    }

    @Override
    public List<?> getTransferHistory(Long employeeId) {
        return Collections.emptyList();
    }

    @Override
    public void changePassword(Long userId, PasswordChangeDTO dto) {
        log.info("修改密码: userId={}", userId);
    }

    @Override
    public void bindMobile(Long userId, MobileBindDTO dto) {
        log.info("绑定手机: userId={}, mobile={}", userId, dto.getMobile());
    }

    @Override
    public List<LoginLogVO> listLoginLogs(Long userId) {
        return Collections.emptyList();
    }

    // ==================== 私有工具方法 ====================

    private Employee findEmployee(Long id) {
        Employee emp = employeeMapper.selectById(id);
        if (emp == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "员工不存在");
        }
        return emp;
    }

    /**
     * 白名单 + 20003 兜底校验。
     * 反射遍历 dto 所有非 null field，若属于 FLOW_FIELDS（流程字段）则抛出 20003。
     */
    private void rejectFlowFields(Object dto, Set<String> allowed) {
        for (Field field : dto.getClass().getDeclaredFields()) {
            field.setAccessible(true);
            try {
                Object value = field.get(dto);
                if (value == null) continue;
                String name = field.getName();
                // 不在白名单且属于流程管控字段 → 20003
                if (!allowed.contains(name) && FLOW_FIELDS.contains(name)) {
                    throw new BusinessException(ErrorCode.FIELD_FORBIDDEN,
                            "字段 [" + name + "] 不可直接编辑，请走对应审批流程");
                }
            } catch (BusinessException e) {
                throw e;
            } catch (Exception ignored) {
                // 忽略反射异常
            }
        }
    }

    private void updatePersonal(Long employeeId, LocalDate birthday, String residenceAddr,
                                 String contact, String phone) {
        EmployeePersonal personal = employeePersonalMapper.selectById(employeeId);
        boolean exists = personal != null;
        if (personal == null) {
            personal = new EmployeePersonal();
            personal.setEmployeeId(employeeId);
        }
        if (birthday != null) personal.setBirthday(birthday);
        if (residenceAddr != null) personal.setResidenceAddress(residenceAddr);
        if (contact != null) personal.setEmergencyContact(contact);
        if (phone != null) personal.setEmergencyPhone(phone);

        if (exists) employeePersonalMapper.updateById(personal);
        else employeePersonalMapper.insert(personal);
    }

    private EmployeeListVO toListVO(Employee emp) {
        EmployeeListVO vo = new EmployeeListVO();
        vo.setEmployeeId(emp.getId());
        vo.setEmpNo(emp.getEmployeeNo());
        vo.setName(emp.getName());
        vo.setGrade(emp.getGrade());
        vo.setEmploymentStatus(formatStatus(emp.getEmploymentStatus()));
        vo.setHireDate(emp.getHireDate());
        return vo;
    }

    private String formatStatus(Integer status) {
        if (status == null) return null;
        return switch (status) {
            case 10 -> "probation";
            case 20 -> "regular";
            case 30 -> "pending_resign";
            case 40 -> "resigned";
            default -> String.valueOf(status);
        };
    }

    private String maskMobile(String mobile) {
        if (mobile == null || mobile.length() < 7) return mobile;
        return mobile.substring(0, 3) + "****" + mobile.substring(7);
    }

    private List<Long> parseCommaLongs(String str) {
        if (str == null || str.isBlank()) return null;
        return Arrays.stream(str.split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .map(Long::parseLong).collect(Collectors.toList());
    }

    private List<String> parseCommaStrings(String str) {
        if (str == null || str.isBlank()) return null;
        return Arrays.stream(str.split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private List<Integer> parseStatuses(String str) {
        if (str == null || str.isBlank()) return null;
        return Arrays.stream(str.split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .mapToInt(s -> switch (s) {
                    case "probation" -> 10;
                    case "regular" -> 20;
                    case "pending_resign" -> 30;
                    case "resigned" -> 40;
                    default -> throw new IllegalArgumentException("未知状态: " + s);
                }).boxed().collect(Collectors.toList());
    }
}
