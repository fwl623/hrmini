package com.company.hrms.employee.service.impl;

import com.company.hrms.common.config.HrmsSecurityProperties;
import com.company.hrms.common.datascope.DataScopeContext;
import com.company.hrms.common.datascope.DataScopeSqlBuilder;
import com.company.hrms.common.exception.BusinessException;
import com.company.hrms.common.exception.ErrorCode;
import com.company.hrms.common.security.LoginUser;
import com.company.hrms.common.security.SecurityUtils;
import com.company.hrms.common.web.PageResult;
import com.company.hrms.employee.dto.*;
import com.company.hrms.employee.entity.Employee;
import com.company.hrms.employee.entity.EmployeeBank;
import com.company.hrms.employee.entity.EmployeeContract;
import com.company.hrms.employee.entity.EmployeePersonal;
import com.company.hrms.employee.entity.EmployeeSalaryProfile;
import com.company.hrms.employee.entity.EmployeeTransferHistory;
import com.company.hrms.employee.mapper.EmployeeBankMapper;
import com.company.hrms.employee.mapper.EmployeeContractMapper;
import com.company.hrms.employee.mapper.EmployeeMapper;
import com.company.hrms.employee.mapper.EmployeePersonalMapper;
import com.company.hrms.employee.mapper.EmployeeSalaryProfileMapper;
import com.company.hrms.employee.mapper.EmployeeTransferHistoryMapper;
import com.company.hrms.employee.service.EmployeeService;
import com.company.hrms.employee.vo.*;
import com.company.hrms.common.field.FieldPermissionFilter;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.hrms.module.auth.dto.ChangePasswordRequest;
import com.company.hrms.module.auth.entity.LoginLog;
import com.company.hrms.module.auth.mapper.LoginLogMapper;
import com.company.hrms.module.auth.service.AuthService;
import com.company.hrms.module.org.entity.Department;
import com.company.hrms.module.org.entity.Position;
import com.company.hrms.module.org.mapper.DepartmentMapper;
import com.company.hrms.module.org.mapper.PositionMapper;
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
 * 白名单 HR端：name, gender, email, birthday, householdAddress, residenceAddress,
 *               emergencyContact, emergencyPhone
 * 白名单 门户：email, residenceAddress, emergencyContact, emergencyPhone
 * 流程字段（含则 20003）：departmentId, positionId, grade, managerId, workLocation, mobile, idNumber
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeServiceImpl implements EmployeeService {

    private static final Set<String> HR_ALLOWED = Set.of(
            "name", "gender", "email", "birthday", "householdAddress", "residenceAddress",
            "emergencyContact", "emergencyPhone");
    private static final Set<String> PORTAL_ALLOWED = Set.of(
            "email", "residenceAddress", "emergencyContact", "emergencyPhone");
    private static final Set<String> FLOW_FIELDS = Set.of(
            "departmentId", "positionId", "grade", "managerId", "workLocation", "mobile", "idNumber");

    private final EmployeeMapper employeeMapper;
    private final EmployeePersonalMapper employeePersonalMapper;
    private final EmployeeSalaryProfileMapper salaryProfileMapper;
    private final EmployeeContractMapper employeeContractMapper;
    private final EmployeeBankMapper employeeBankMapper;
    private final EmployeeTransferHistoryMapper transferHistoryMapper;
    private final DepartmentMapper departmentMapper;
    private final PositionMapper positionMapper;
    private final HrmsSecurityProperties securityProperties;
    private final AuthService authService;
    private final LoginLogMapper loginLogMapper;
    private final FieldPermissionFilter fieldPermissionFilter;

    // ==================== 花名册分页 ====================

    @Override
    public PageResult<EmployeeListVO> pageSearch(EmployeePageQuery query) {
        // TODO: org接口未完成 — departmentIds 由前端部门树选择器传入，待 hrms-org 提供 /departments/tree 接口后联调
        List<Long> deptIds = parseCommaLongs(query.getDepartmentIds());
        // TODO: org接口未完成 — positionIds 同理，待 hrms-org 提供 /positions 接口
        List<Long> positionIds = parseCommaLongs(query.getPositionIds());
        List<Integer> statusList = parseStatuses(query.getEmploymentStatus());
        List<String> gradeList = parseCommaStrings(query.getGradeLevels());
        // Aspect 有则用；否则按 LoginUser 直接拼装（防止空串导致全表越权）
        String dataScope = resolveEmployeeDataScope();

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
        Employee emp = findEmployeeAccessible(employeeId);
        EmployeeDetailVO vo = new EmployeeDetailVO();
        vo.setEmployeeId(emp.getId());
        vo.setEmpNo(emp.getEmployeeNo());
        vo.setName(emp.getName());
        vo.setGender(emp.getGender());
        vo.setMobile(maskMobile(emp.getMobile()));
        vo.setEmail(emp.getEmail());
        vo.setDepartmentId(emp.getDepartmentId());
        vo.setDepartment(resolveDepartmentName(emp.getDepartmentId()));
        vo.setPositionId(emp.getPositionId());
        vo.setPosition(resolvePositionName(emp.getPositionId()));
        vo.setGrade(emp.getGrade());
        vo.setManagerId(emp.getManagerId());
        vo.setManagerName(resolveEmployeeName(emp.getManagerId()));
        vo.setWorkLocation(emp.getWorkLocation());
        vo.setEmploymentType(emp.getEmploymentType());
        vo.setEmploymentStatus(formatStatus(emp.getEmploymentStatus()));
        vo.setHireDate(emp.getHireDate());
        vo.setProbationPayRatio(emp.getProbationPayRatio());
        vo.setCreatedAt(emp.getCreatedAt());

        EmployeePersonal personal = employeePersonalMapper.selectById(employeeId);
        if (personal != null) {
            vo.setBirthday(personal.getBirthday());
            vo.setHouseholdAddress(personal.getHouseholdAddress());
            vo.setResidenceAddress(personal.getResidenceAddress());
            vo.setEmergencyContact(personal.getEmergencyContact());
            vo.setEmergencyPhone(personal.getEmergencyPhone());
        }

        // 合同 + 薪资档案
        EmployeeContract contract = employeeContractMapper.selectByEmployeeId(employeeId);
        if (contract != null) {
            vo.setContractType(contract.getContractType());
            vo.setContractExpireDate(contract.getContractExpireDate());
            vo.setSchemeId(contract.getSchemeId());
            if (contract.getProbationSalaryRatio() != null) {
                vo.setProbationPayRatio(contract.getProbationSalaryRatio());
            }
            if (contract.getBaseSalary() != null) {
                vo.setBaseSalary(contract.getBaseSalary());
            }
        }
        EmployeeSalaryProfile salaryProfile = salaryProfileMapper.selectByEmployeeId(employeeId);
        if (salaryProfile != null) {
            if (salaryProfile.getSchemeId() != null) {
                vo.setSchemeId(salaryProfile.getSchemeId());
            }
            if (salaryProfile.getBaseSalary() != null) {
                vo.setBaseSalary(salaryProfile.getBaseSalary());
            }
            if (salaryProfile.getProbationRatio() != null) {
                vo.setProbationPayRatio(salaryProfile.getProbationRatio());
            }
        }
        if (vo.getSchemeId() != null) {
            vo.setSchemeName(employeeContractMapper.selectSchemeName(vo.getSchemeId()));
        }

        // 银行信息（脱敏后四位）
        EmployeeBank bank = employeeBankMapper.selectById(employeeId);
        if (bank != null) {
            vo.setBankName(bank.getBankName());
            if (StringUtils.hasText(bank.getBankAccountTail())) {
                vo.setBankAccount("****" + bank.getBankAccountTail());
            }
        }

        // 按角色裁剪敏感字段
        LoginUser loginUser = SecurityUtils.getLoginUser();
        if (loginUser != null) {
            fieldPermissionFilter.filter(vo, loginUser, employeeId);
        }
        return vo;
    }

    // ==================== HR编辑 ====================

    @Override
    @Transactional
    public void update(Long employeeId, EmployeeUpdateDTO dto) {
        Employee emp = findEmployeeAccessible(employeeId);
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
        if (hasUpdate) {
            employeeMapper.updateById(update);
        }

        updatePersonal(employeeId, dto.getBirthday(), dto.getHouseholdAddress(), dto.getResidenceAddress(),
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
        vo.setDepartment(resolveDepartmentName(emp.getDepartmentId()));
        vo.setPosition(resolvePositionName(emp.getPositionId()));
        EmployeeSalaryProfile salaryProfile = salaryProfileMapper.selectByEmployeeId(employeeId);
        if (salaryProfile != null) {
            vo.setBaseSalary(salaryProfile.getBaseSalary());
        }
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
        updatePersonal(employeeId, null, null, dto.getResidenceAddress(),
                dto.getEmergencyContact(), dto.getEmergencyPhone());
    }

    // ==================== 委托至 hrms-auth ====================

    @Override
    public void changePassword(Long userId, PasswordChangeDTO dto, String accessToken) {
        if (dto == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "请求参数不能为空");
        }
        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setOldPassword(dto.getOldPassword());
        req.setNewPassword(dto.getNewPassword());
        req.setConfirmPassword(dto.getConfirmPassword());
        authService.changePassword(req, accessToken);
        // 清掉其余会话态（refresh / 活跃心跳 / 工资条二次验证等）
        authService.invalidateUserSessions(userId);
        log.info("修改密码成功并失效会话: userId={}", userId);
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
        if (userId == null) {
            return Collections.emptyList();
        }
        List<LoginLog> rows = loginLogMapper.selectList(new LambdaQueryWrapper<LoginLog>()
                .eq(LoginLog::getUserId, userId)
                .orderByDesc(LoginLog::getLoginTime)
                .last("LIMIT 50"));
        List<LoginLogVO> list = new ArrayList<>(rows.size());
        for (LoginLog row : rows) {
            LoginLogVO vo = new LoginLogVO();
            vo.setLoginTime(row.getLoginTime());
            vo.setIp(row.getLoginIp());
            vo.setDevice(row.getDevice());
            vo.setLocation(row.getLocation());
            vo.setSuccess(row.getSuccess() != null && row.getSuccess() == 1);
            list.add(vo);
        }
        return list;
    }

    // ==================== 私有方法 ====================

    private Employee findEmployee(Long id) {
        Employee emp = employeeMapper.selectById(id);
        if (emp == null) throw new BusinessException(ErrorCode.PARAM_INVALID, "员工不存在");
        return emp;
    }

    private String resolveEmployeeName(Long employeeId) {
        if (employeeId == null || employeeId <= 0) {
            return null;
        }
        Employee manager = employeeMapper.selectById(employeeId);
        return manager != null ? manager.getName() : null;
    }

    private String resolveDepartmentName(Long departmentId) {
        if (departmentId == null || departmentId <= 0) {
            return null;
        }
        Department dept = departmentMapper.selectById(departmentId);
        if (dept == null || (dept.getDeleted() != null && dept.getDeleted() == 1)) {
            return null;
        }
        return dept.getName();
    }

    private String resolvePositionName(Long positionId) {
        if (positionId == null || positionId <= 0) {
            return null;
        }
        Position position = positionMapper.selectById(positionId);
        if (position == null || (position.getDeleted() != null && position.getDeleted() == 1)) {
            return null;
        }
        return position.getName();
    }

    /** 管理端读/写：按当前用户 DataScope 过滤，越权返回 20002 */
    private Employee findEmployeeAccessible(Long id) {
        Employee emp = employeeMapper.selectByIdScoped(id, resolveEmployeeDataScope());
        if (emp != null) {
            return emp;
        }
        if (employeeMapper.selectById(id) == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "员工不存在");
        }
        throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看该员工");
    }

    private String resolveEmployeeDataScope() {
        // 以 LoginUser 为准拼装；Context 仅在有非空片段时复用（避免 "" 被误当成「不过滤」）
        String fromCtx = DataScopeContext.get();
        if (fromCtx != null && !fromCtx.isBlank()) {
            return fromCtx;
        }
        LoginUser user = SecurityUtils.getLoginUser();
        String built = DataScopeSqlBuilder.buildForEmployeeAlias(user);
        if (built == null || built.isBlank()) {
            // ALL/PAYROLL/NONE_PAYROLL → 不过滤；但 EMPLOYEE 等 SELF 绝不应落到这里却为空串误放行
            if (user != null && user.getDataScope() == com.company.hrms.common.enums.DataScopeType.SELF) {
                log.error("SELF dataScope built empty, force deny. userId={} empId={}",
                        user.getUserId(), user.getEmployeeId());
                return " AND 1=0";
            }
        }
        return built == null ? " AND 1=0" : built;
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

    private void updatePersonal(Long employeeId, LocalDate birthday, String householdAddr, String residenceAddr,
                                 String contact, String phone) {
        // 无任何个人字段变更时跳过，避免空 insert 触发 id_number_enc NOT NULL → 90001
        if (birthday == null && householdAddr == null && residenceAddr == null && contact == null && phone == null) {
            return;
        }
        EmployeePersonal personal = employeePersonalMapper.selectById(employeeId);
        if (personal == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "员工个人信息未建档，无法更新住址/紧急联系人等字段");
        }
        if (birthday != null) personal.setBirthday(birthday);
        if (householdAddr != null) personal.setHouseholdAddress(householdAddr);
        if (residenceAddr != null) personal.setResidenceAddress(residenceAddr);
        if (contact != null) personal.setEmergencyContact(contact);
        if (phone != null) personal.setEmergencyPhone(phone);
        employeePersonalMapper.updateById(personal);
    }

    private EmployeeListVO toListVO(Employee emp) {
        EmployeeListVO vo = new EmployeeListVO();
        vo.setEmployeeId(emp.getId());
        vo.setEmpNo(emp.getEmployeeNo());
        vo.setName(emp.getName());
        vo.setDepartment(emp.getDepartmentName());
        vo.setPosition(emp.getPositionName());
        vo.setGrade(emp.getGrade());
        vo.setEmploymentStatus(formatStatus(emp.getEmploymentStatus()));
        vo.setHireDate(emp.getHireDate());
        return vo;
    }

    @Override
    public List<TransferHistoryVO> listTransferHistory(Long employeeId) {
        if (employeeId == null) {
            return List.of();
        }
        List<EmployeeTransferHistory> rows = transferHistoryMapper.selectByEmployeeId(employeeId);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream().map(this::toTransferHistoryVo).collect(Collectors.toList());
    }

    private TransferHistoryVO toTransferHistoryVo(EmployeeTransferHistory h) {
        TransferHistoryVO vo = new TransferHistoryVO();
        vo.setId(h.getId());
        vo.setEmployeeId(h.getEmployeeId());
        vo.setTransferAppId(h.getTransferAppId());
        vo.setFromDepartmentId(h.getFromDepartmentId());
        vo.setToDepartmentId(h.getToDepartmentId());
        vo.setFromPositionId(h.getFromPositionId());
        vo.setToPositionId(h.getToPositionId());
        vo.setFromDepartmentName(deptName(h.getFromDepartmentId()));
        vo.setToDepartmentName(deptName(h.getToDepartmentId()));
        vo.setFromPositionName(positionName(h.getFromPositionId()));
        vo.setToPositionName(positionName(h.getToPositionId()));
        vo.setTransferDate(h.getTransferDate() == null ? null : h.getTransferDate().toString());
        vo.setReason(h.getReason());
        return vo;
    }

    private String deptName(Long id) {
        if (id == null) {
            return null;
        }
        Department d = departmentMapper.selectById(id);
        return d == null ? null : d.getName();
    }

    private String positionName(Long id) {
        if (id == null) {
            return null;
        }
        Position p = positionMapper.selectById(id);
        return p == null ? null : p.getName();
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
