package com.company.hrms.employee.service.impl;

import com.company.hrms.common.config.HrmsSecurityProperties;
import com.company.hrms.common.exception.BusinessException;
import com.company.hrms.common.exception.ErrorCode;
import com.company.hrms.common.security.SecurityUtils;
import com.company.hrms.common.web.PageResult;
import com.company.hrms.employee.dto.*;
import com.company.hrms.employee.entity.Employee;
import com.company.hrms.employee.entity.EmployeePersonal;
import com.company.hrms.employee.mapper.EmployeeMapper;
import com.company.hrms.employee.mapper.EmployeePersonalMapper;
import com.company.hrms.employee.service.EmployeeService;
import com.company.hrms.employee.vo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 员工档案服务
 *
 * 白名单 HR端：name, gender, email, birthday, residenceAddress,
 *               emergencyContact, emergencyPhone, workLocation
 * 白名单 门户：email, residenceAddress, emergencyContact, emergencyPhone
 * 流程字段（含则 20003）：departmentId, positionId, grade, managerId, mobile, idNumber
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeServiceImpl implements EmployeeService {

    private static final Set<String> HR_ALLOWED = Set.of(
            "name", "gender", "email", "birthday", "residenceAddress",
            "emergencyContact", "emergencyPhone", "workLocation");
    private static final Set<String> PORTAL_ALLOWED = Set.of(
            "email", "residenceAddress", "emergencyContact", "emergencyPhone");
    private static final Set<String> FLOW_FIELDS = Set.of(
            "departmentId", "positionId", "grade", "managerId", "mobile", "idNumber");

    private final EmployeeMapper employeeMapper;
    private final EmployeePersonalMapper employeePersonalMapper;
    private final HrmsSecurityProperties securityProperties;

    // ==================== 花名册分页 ====================

    @Override
    public PageResult<EmployeeListVO> pageSearch(EmployeePageQuery query) {
        // TODO: org接口未完成 — departmentIds 由前端部门树选择器传入，待 hrms-org 提供 /departments/tree 接口后联调
        List<Long> deptIds = parseCommaLongs(query.getDepartmentIds());
        // TODO: org接口未完成 — positionIds 同理，待 hrms-org 提供 /positions 接口
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
        // TODO: org接口未完成 — department/position/managerName 需调用 hrms-org 根据 ID 查询名称后填充，当前 SQL JOIN department/position 表直接取 name
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

    // ==================== HR编辑 ====================

    @Override
    @Transactional
    public void update(Long employeeId, EmployeeUpdateDTO dto) {
        Employee emp = findEmployee(employeeId);
        if (emp.getEmploymentStatus() != null && emp.getEmploymentStatus() >= 40) {
            throw new BusinessException(ErrorCode.EMPLOYEE_STATUS_INVALID);
        }
        rejectFlowFields(dto, HR_ALLOWED);

        Employee update = new Employee();
        update.setId(employeeId);
        boolean hasUpdate = false;
        if (dto.getName() != null) { update.setName(dto.getName()); hasUpdate = true; }
        if (dto.getGender() != null) { update.setGender(dto.getGender()); hasUpdate = true; }
        if (dto.getEmail() != null) { update.setEmail(dto.getEmail()); hasUpdate = true; }
        if (dto.getWorkLocation() != null) { update.setWorkLocation(dto.getWorkLocation()); hasUpdate = true; }
        if (hasUpdate) {
            employeeMapper.updateById(update);
        }

        updatePersonal(employeeId, dto.getBirthday(), dto.getResidenceAddress(),
                dto.getEmergencyContact(), dto.getEmergencyPhone());

        log.info("员工档案编辑: employeeId={}, operatorId={}", employeeId, SecurityUtils.getUserId());
    }

    // ==================== 门户 ====================

    @Override
    public ProfileVO getMyProfile(Long employeeId) {
        Employee emp = findEmployee(employeeId);
        ProfileVO vo = new ProfileVO();
        vo.setEmployeeId(emp.getId());
        vo.setEmpNo(emp.getEmployeeNo());
        vo.setName(emp.getName());
        boolean bound = isBoundMobile(emp.getMobile());
        vo.setMobileBound(bound);
        vo.setMobile(bound ? maskMobile(emp.getMobile()) : null);
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

    // ==================== 委托至 hrms-auth ====================

    @Override
    public void changePassword(Long userId, PasswordChangeDTO dto) {
        log.info("修改密码: userId={}", userId);
    }

    @Override
    @Transactional
    public void bindMobile(Long employeeId, Long userId, MobileBindDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getMobile()) || !StringUtils.hasText(dto.getSmsCode())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "手机号与验证码必填");
        }
        String mobile = dto.getMobile().trim();
        if (!mobile.matches("^1\\d{10}$")) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "手机号格式不正确");
        }
        assertDevSmsCode(dto.getSmsCode());

        Employee emp = findEmployee(employeeId);
        // PRD：已有手机号不可直接改，须走 MOBILE_CHANGE 审批
        if (isBoundMobile(emp.getMobile())) {
            throw new BusinessException(ErrorCode.FIELD_FORBIDDEN,
                    "手机号已绑定，变更请提交手机号变更申请，不可直接绑定");
        }
        Employee occupied = employeeMapper.selectByMobile(mobile);
        if (occupied != null && !occupied.getId().equals(employeeId)) {
            throw new BusinessException(ErrorCode.MOBILE_DUPLICATE);
        }

        Employee update = new Employee();
        update.setId(employeeId);
        update.setMobile(mobile);
        employeeMapper.updateById(update);

        // 登录账号同步依赖 A 组 internal username 接口；联调启用时在此调用 AuthInternalFeignClient
        log.info("首次绑定手机: employeeId={}, userId={}, mobile={}", employeeId, userId, mobile);
    }

    @Override
    public List<LoginLogVO> listLoginLogs(Long userId) {
        return Collections.emptyList();
    }

    // ==================== 私有方法 ====================

    private Employee findEmployee(Long id) {
        Employee emp = employeeMapper.selectById(id);
        if (emp == null) throw new BusinessException(ErrorCode.PARAM_INVALID, "员工不存在");
        return emp;
    }

    private void rejectFlowFields(Object dto, Set<String> allowed) {
        for (Field field : dto.getClass().getDeclaredFields()) {
            field.setAccessible(true);
            try {
                Object value = field.get(dto);
                if (value == null) continue;
                if (!allowed.contains(field.getName()) && FLOW_FIELDS.contains(field.getName())) {
                    throw new BusinessException(ErrorCode.FIELD_FORBIDDEN,
                            "字段 [" + field.getName() + "] 不可直接编辑，请走对应审批流程");
                }
            } catch (BusinessException e) { throw e; } catch (Exception ignored) {}
        }
    }

    private void updatePersonal(Long employeeId, LocalDate birthday, String addr,
                                 String contact, String phone) {
        EmployeePersonal personal = employeePersonalMapper.selectById(employeeId);
        boolean exists = personal != null;
        if (personal == null) { personal = new EmployeePersonal(); personal.setEmployeeId(employeeId); }
        if (birthday != null) personal.setBirthday(birthday);
        if (addr != null) personal.setResidenceAddress(addr);
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
        return switch (status) { case 10 -> "probation"; case 20 -> "regular"; case 30 -> "pending_resign"; case 40 -> "resigned"; default -> String.valueOf(status); };
    }

    private String maskMobile(String mobile) {
        if (mobile == null || mobile.length() < 7) return mobile;
        return mobile.substring(0, 3) + "****" + mobile.substring(7);
    }

    /** 已绑定：11 位手机号；占位/空视为未绑定 */
    private boolean isBoundMobile(String mobile) {
        return StringUtils.hasText(mobile) && mobile.trim().matches("^1\\d{10}$");
    }

    private void assertDevSmsCode(String smsCode) {
        HrmsSecurityProperties.Sms sms = securityProperties.getSms();
        if (!(sms.isDevEnabled()
                && StringUtils.hasText(sms.getDevCode())
                && sms.getDevCode().equals(smsCode))) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "短信验证码错误");
        }
    }

    private List<Long> parseCommaLongs(String str) {
        if (str == null || str.isBlank()) return null;
        return Arrays.stream(str.split(",")).map(String::trim).filter(s -> !s.isEmpty()).map(Long::parseLong).collect(Collectors.toList());
    }

    private List<String> parseCommaStrings(String str) {
        if (str == null || str.isBlank()) return null;
        return Arrays.stream(str.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
    }

    private List<Integer> parseStatuses(String str) {
        if (str == null || str.isBlank()) return null;
        return Arrays.stream(str.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .mapToInt(s -> switch (s) { case "probation" -> 10; case "regular" -> 20; case "pending_resign" -> 30; case "resigned" -> 40; default -> throw new IllegalArgumentException("未知状态: " + s); })
                .boxed().collect(Collectors.toList());
    }
}
