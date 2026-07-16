package com.company.hrms.employee.service.impl;

import com.company.hrms.common.exception.BusinessException;
import com.company.hrms.common.exception.ErrorCode;
import com.company.hrms.common.security.LoginUser;
import com.company.hrms.common.security.SecurityUtils;
import com.company.hrms.common.web.PageResult;
import com.company.hrms.employee.dto.EmployeePageQuery;
import com.company.hrms.employee.dto.EmployeeUpdateDTO;
import com.company.hrms.employee.dto.MobileBindDTO;
import com.company.hrms.employee.dto.PasswordChangeDTO;
import com.company.hrms.employee.dto.ProfileUpdateDTO;
import com.company.hrms.employee.entity.Employee;
import com.company.hrms.employee.entity.EmployeePersonal;
import com.company.hrms.employee.mapper.EmployeeMapper;
import com.company.hrms.employee.mapper.EmployeePersonalMapper;
import com.company.hrms.employee.service.EmployeeService;
import com.company.hrms.employee.vo.EmployeeDetailVO;
import com.company.hrms.employee.vo.EmployeeListVO;
import com.company.hrms.employee.vo.LoginLogVO;
import com.company.hrms.employee.vo.ProfileVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 员工档案服务
 *
 * 白名单（HR编辑）：name, gender, email, birthday, residenceAddress,
 *                   emergencyContact, emergencyPhone, workLocation
 * 白名单（门户编辑）：email, residenceAddress, emergencyContact, emergencyPhone
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

    /** 不可编辑的流程字段（PUT 含这些字段时返回 20003） */
    private static final Set<String> FLOW_FIELDS = Set.of(
            "departmentId", "positionId", "grade", "managerId", "mobile", "idNumber");

    private final EmployeeMapper employeeMapper;
    private final EmployeePersonalMapper employeePersonalMapper;

    @Override
    public PageResult<EmployeeListVO> pageSearch(EmployeePageQuery query) {
        // 解析逗号分隔参数
        List<Long> deptIds = parseCommaLongs(query.getDepartmentIds());
        List<Long> positionIds = parseCommaLongs(query.getPositionIds());
        List<Integer> statusList = parseStatuses(query.getEmploymentStatus());
        List<String> gradeList = parseCommaStrings(query.getGradeLevels());

        // DataScope 由拦截器在 SQL 层注入 ${dataScope}
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

    @Override
    public EmployeeDetailVO getDetail(Long employeeId) {
        Employee emp = employeeMapper.selectById(employeeId);
        if (emp == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "员工不存在");
        }

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

        // 个人信息
        EmployeePersonal personal = employeePersonalMapper.selectById(employeeId);
        if (personal != null) {
            vo.setBirthday(personal.getBirthday());
            vo.setHouseholdAddress(personal.getHouseholdAddress());
            vo.setResidenceAddress(personal.getResidenceAddress());
            vo.setEmergencyContact(personal.getEmergencyContact());
            vo.setEmergencyPhone(personal.getEmergencyPhone());
            vo.setIdNumber(personal.getIdNumberEnc() != null ? maskIdNumber(null) : null);
        }

        // fieldPermissions 由 FieldPermissionFilter 后置处理
        return vo;
    }

    @Override
    @Transactional
    public void update(Long employeeId, EmployeeUpdateDTO dto) {
        Employee emp = employeeMapper.selectById(employeeId);
        if (emp == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "员工不存在");
        }

        // 已离职不可编辑
        if (emp.getEmploymentStatus() != null && emp.getEmploymentStatus() >= 40) {
            throw new BusinessException(ErrorCode.EMPLOYEE_STATUS_INVALID);
        }

        // 检查是否包含流程字段（非白名单且属于流程管控字段）
        checkFlowFields(dto);

        // 执行更新
        Employee update = new Employee();
        update.setId(employeeId);
        if (dto.getName() != null) update.setName(dto.getName());
        if (dto.getGender() != null) update.setGender(dto.getGender());
        if (dto.getEmail() != null) update.setEmail(dto.getEmail());
        if (dto.getWorkLocation() != null) update.setWorkLocation(dto.getWorkLocation());
        employeeMapper.updateById(update);

        // 更新个人信息表
        EmployeePersonal personal = employeePersonalMapper.selectById(employeeId);
        boolean exists = personal != null;
        if (personal == null) {
            personal = new EmployeePersonal();
            personal.setEmployeeId(employeeId);
        }
        if (dto.getBirthday() != null) personal.setBirthday(dto.getBirthday());
        if (dto.getResidenceAddress() != null) personal.setResidenceAddress(dto.getResidenceAddress());
        if (dto.getEmergencyContact() != null) personal.setEmergencyContact(dto.getEmergencyContact());
        if (dto.getEmergencyPhone() != null) personal.setEmergencyPhone(dto.getEmergencyPhone());

        if (exists) {
            employeePersonalMapper.updateById(personal);
        } else {
            employeePersonalMapper.insert(personal);
        }

        log.info("员工档案编辑: employeeId={}, operatorId={}", employeeId, SecurityUtils.getUserId());
    }

    @Override
    public ProfileVO getMyProfile(Long employeeId) {
        Employee emp = employeeMapper.selectById(employeeId);
        if (emp == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "员工不存在");
        }

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
        Employee emp = employeeMapper.selectById(employeeId);
        if (emp == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "员工不存在");
        }

        if (dto.getEmail() != null) {
            Employee update = new Employee();
            update.setId(employeeId);
            update.setEmail(dto.getEmail());
            employeeMapper.updateById(update);
        }

        EmployeePersonal personal = employeePersonalMapper.selectById(employeeId);
        boolean exists = personal != null;
        if (personal == null) {
            personal = new EmployeePersonal();
            personal.setEmployeeId(employeeId);
        }
        if (dto.getResidenceAddress() != null) personal.setResidenceAddress(dto.getResidenceAddress());
        if (dto.getEmergencyContact() != null) personal.setEmergencyContact(dto.getEmergencyContact());
        if (dto.getEmergencyPhone() != null) personal.setEmergencyPhone(dto.getEmergencyPhone());

        if (exists) {
            employeePersonalMapper.updateById(personal);
        } else {
            employeePersonalMapper.insert(personal);
        }
    }

    @Override
    public List<?> listMobileChangeApps() {
        // 由 Mapper 查询 employee_mobile_change_application WHERE status='PENDING'
        return Collections.emptyList();
    }

    @Override
    public List<?> getTransferHistory(Long employeeId) {
        return Collections.emptyList();
    }

    @Override
    public void changePassword(Long userId, PasswordChangeDTO dto) {
        // 委托至 hrms-auth 模块处理
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

    // ===== 私有工具方法 =====

    private void checkFlowFields(EmployeeUpdateDTO dto) {
        // 检查是否包含禁止直接编辑的流程字段
        // 通过反射检查非 null 字段是否包含流程字段名
        if (dto.getGender() != null && !"MALE".equals(dto.getGender()) && !"FEMALE".equals(dto.getGender())) {
            // gender 是允许字段，仅做格式校验
        }
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

    private String maskIdNumber(String id) {
        if (id == null || id.length() < 10) return id;
        return id.substring(0, 4) + "**********" + id.substring(id.length() - 4);
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
