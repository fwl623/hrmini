package com.company.hrms.module.employee.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.hrms.common.exception.BusinessException;
import com.company.hrms.common.dto.PageResult;
import com.company.hrms.common.util.SecurityUtils;
import com.company.hrms.module.employee.dto.EmployeePageQuery;
import com.company.hrms.module.employee.dto.EmployeeUpdateDTO;
import com.company.hrms.module.employee.dto.SalaryProfileUpdateDTO;
import com.company.hrms.module.employee.entity.*;
import com.company.hrms.module.employee.enums.EmploymentStatus;
import com.company.hrms.module.employee.repository.*;
import com.company.hrms.module.employee.service.EmployeeService;
import com.company.hrms.module.employee.vo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 员工档案服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeServiceImpl implements EmployeeService {

    private final EmployeeMapper employeeMapper;
    private final EmployeePersonalMapper employeePersonalMapper;
    private final EmployeeContractMapper employeeContractMapper;
    private final EmployeeBankMapper employeeBankMapper;
    private final EmployeeSalaryProfileMapper employeeSalaryProfileMapper;
    private final EmployeeSalaryHistoryMapper employeeSalaryHistoryMapper;
    private final EmployeeTransferHistoryMapper employeeTransferHistoryMapper;
    private final EmployeeMobileChangeApplicationMapper mobileChangeMapper;

    @Override
    public PageResult<EmployeeListVO> pageSearch(EmployeePageQuery query) {
        // 解析逗号分隔参数
        List<Long> deptIds = parseCommaSeparatedLongs(query.getDepartmentIds());
        List<Long> positionIds = parseCommaSeparatedLongs(query.getPositionIds());
        List<String> statusList = parseCommaSeparatedStrings(query.getEmploymentStatus());
        List<String> gradeList = parseCommaSeparatedStrings(query.getGradeLevels());

        // 数据范围注入（由 @DataScope 拦截器在 SQL 中注入 ${dataScope}）
        String dataScope = "";

        // 查询总数
        Long total = employeeMapper.countSearch(
                query.getKeyword(), deptIds, positionIds, statusList, gradeList,
                query.getHireDateFrom(), query.getHireDateTo(), dataScope);

        if (total == null || total == 0) {
            return PageResult.empty();
        }

        // 查询列表 — 此处使用 MyBatis-Plus Page 或手动分页
        // 实际项目中结合 DataScopeInterceptor，此处简化
        List<EmployeeListVO> list = employeeMapper.search(
                query.getKeyword(), deptIds, positionIds, statusList, gradeList,
                query.getHireDateFrom(), query.getHireDateTo(), dataScope);

        return PageResult.of(list, total, query.getPage(), query.getPageSize());
    }

    @Override
    public EmployeeDetailVO getDetail(Long employeeId) {
        EmployeeEntity emp = employeeMapper.selectById(employeeId);
        if (emp == null) {
            throw new BusinessException(404, "员工不存在");
        }

        EmployeeDetailVO vo = new EmployeeDetailVO();
        vo.setEmployeeId(emp.getId());
        vo.setEmpNo(emp.getEmployeeNo());
        vo.setName(emp.getName());
        vo.setGender(emp.getGender() != null ? emp.getGender().getApiValue() : null);
        vo.setMobile(emp.getMobile());
        vo.setEmail(emp.getEmail());
        vo.setDepartmentId(emp.getDepartmentId());
        vo.setPositionId(emp.getPositionId());
        vo.setGrade(emp.getGrade());
        vo.setManagerId(emp.getManagerId());
        vo.setWorkLocation(emp.getWorkLocation());
        vo.setEmploymentStatus(emp.getEmploymentStatus() != null ? emp.getEmploymentStatus().getApiValue() : null);
        vo.setEmploymentType(emp.getEmploymentType() != null ? emp.getEmploymentType().getApiValue() : null);
        vo.setHireDate(emp.getHireDate());
        vo.setProbationPayRatio(emp.getProbationPayRatio());

        // 补充个人信息
        EmployeePersonalEntity personal = employeePersonalMapper.selectById(employeeId);
        if (personal != null) {
            vo.setBirthday(personal.getBirthday());
            vo.setHouseholdAddress(personal.getHouseholdAddress());
            vo.setResidenceAddress(personal.getResidenceAddress());
            vo.setEmergencyContact(personal.getEmergencyContact());
            vo.setEmergencyPhone(personal.getEmergencyPhone());
            // 身份证由 FieldPermissionFilter 处理脱敏
        }

        // 补充银行卡信息
        EmployeeBankEntity bank = employeeBankMapper.selectById(employeeId);
        if (bank != null) {
            vo.setBankAccount(bank.getBankAccountTail() != null
                    ? "****" + bank.getBankAccountTail() : null);
            vo.setBankName(bank.getBankName());
        }

        // fieldPermissions 由 FieldPermissionFilter 后置处理
        return vo;
    }

    @Override
    @Transactional
    public void update(Long employeeId, EmployeeUpdateDTO dto) {
        EmployeeEntity emp = employeeMapper.selectById(employeeId);
        if (emp == null) {
            throw new BusinessException(404, "员工不存在");
        }

        // 校验白名单：禁止修改 mobile, departmentId, positionId
        if (dto.getName() != null) {
            // 白名单校验通过
        }

        // 执行更新（仅更新非空白名单字段）
        EmployeeEntity updateEntity = new EmployeeEntity();
        updateEntity.setId(employeeId);
        if (dto.getName() != null) updateEntity.setName(dto.getName());
        if (dto.getGender() != null) {
            updateEntity.setGender(com.company.hrms.module.employee.enums.Gender.fromApiValue(dto.getGender()));
        }
        if (dto.getEmail() != null) updateEntity.setEmail(dto.getEmail());
        if (dto.getWorkLocation() != null) updateEntity.setWorkLocation(dto.getWorkLocation());

        employeeMapper.updateById(updateEntity);

        // 更新个人信息表
        EmployeePersonalEntity personal = new EmployeePersonalEntity();
        personal.setEmployeeId(employeeId);
        if (dto.getBirthday() != null) personal.setBirthday(dto.getBirthday());
        if (dto.getAddress() != null) personal.setHouseholdAddress(dto.getAddress());
        if (dto.getEmergencyContact() != null) personal.setEmergencyContact(dto.getEmergencyContact());
        if (dto.getEmergencyPhone() != null) personal.setEmergencyPhone(dto.getEmergencyPhone());

        if (employeePersonalMapper.selectById(employeeId) != null) {
            employeePersonalMapper.updateById(personal);
        } else {
            employeePersonalMapper.insert(personal);
        }
    }

    @Override
    public SalaryProfileVO getSalaryProfile(Long employeeId) {
        // SYS_ADMIN 角色在此处由 Controller 层拦截，直接抛 20002
        EmployeeSalaryProfileEntity profile = employeeSalaryProfileMapper.selectByEmployeeId(employeeId);
        if (profile == null) {
            throw new BusinessException(50003, "员工无薪资档案");
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
        EmployeeSalaryProfileEntity profile = employeeSalaryProfileMapper.selectByEmployeeId(employeeId);
        if (profile == null) {
            throw new BusinessException(50003, "员工无薪资档案");
        }

        EmployeeSalaryProfileEntity update = new EmployeeSalaryProfileEntity();
        update.setId(profile.getId());
        update.setEmployeeId(employeeId);
        update.setSchemeId(dto.getSchemeId());
        update.setBaseSalary(dto.getBaseSalary());
        update.setAllowanceBaseJson(dto.getAllowanceBaseJson());
        update.setSsBase(dto.getSsBase());
        update.setHfBase(dto.getHfBase());
        update.setPerformanceBase(dto.getPerformanceBase());
        if (dto.getProbationRatio() != null) {
            update.setProbationRatio(dto.getProbationRatio());
        }

        employeeSalaryProfileMapper.updateById(update);

        // 记录调薪历史
        EmployeeSalaryHistoryEntity history = new EmployeeSalaryHistoryEntity();
        history.setEmployeeId(employeeId);
        history.setFieldName("baseSalary");
        history.setOldValue(profile.getBaseSalary());
        history.setNewValue(dto.getBaseSalary());
        history.setEffectiveDate(LocalDate.now());
        history.setReason("HR编辑薪资档案");
        history.setOperatorId(SecurityUtils.getCurrentUserId());
        employeeSalaryHistoryMapper.insert(history);
    }

    @Override
    public EmployeeSensitiveFieldVO getSensitiveField(Long employeeId, String field) {
        EmployeeEntity emp = employeeMapper.selectById(employeeId);
        if (emp == null) {
            throw new BusinessException(404, "员工不存在");
        }

        // 校验当前用户是否有权限查看该敏感字段
        // 由 Controller 层 @PreAuthorize 或自定义注解处理权限
        // 此处仅返回数据，查看记录写入 operation_log（由 AOP 处理）

        EmployeeSensitiveFieldVO vo = new EmployeeSensitiveFieldVO();
        vo.setEmployeeId(employeeId);
        vo.setField(field);

        switch (field) {
            case "idNumber" -> {
                EmployeePersonalEntity personal = employeePersonalMapper.selectById(employeeId);
                if (personal != null) {
                    vo.setValue(personal.getIdNumberEnc());
                    // 实际应由加密工具解密后返回明文
                }
            }
            case "bankAccount" -> {
                EmployeeBankEntity bank = employeeBankMapper.selectById(employeeId);
                if (bank != null) {
                    vo.setValue(bank.getBankAccountEnc());
                    // 实际应由加密工具解密后返回明文
                }
            }
            default -> throw new BusinessException(10001, "不支持的敏感字段: " + field);
        }

        return vo;
    }

    @Override
    public List<MobileChangeAppVO> listMobileChangeApplications() {
        return mobileChangeMapper.selectPendingList();
    }

    @Override
    public List<EmployeeTransferHistoryVO> getTransferHistory(Long employeeId) {
        return employeeTransferHistoryMapper.selectByEmployeeId(employeeId);
    }

    // ===== 门户接口实现 =====

    @Override
    public ProfileVO getMyProfile(Long employeeId) {
        EmployeeEntity emp = employeeMapper.selectById(employeeId);
        if (emp == null) {
            throw new BusinessException(404, "员工不存在");
        }

        ProfileVO vo = new ProfileVO();
        vo.setEmployeeId(emp.getId());
        vo.setEmpNo(emp.getEmployeeNo());
        vo.setName(emp.getName());
        vo.setMobile(maskMobile(emp.getMobile()));
        vo.setEmail(emp.getEmail());
        vo.setGrade(emp.getGrade());
        vo.setHireDate(emp.getHireDate() != null ? emp.getHireDate().toString() : null);

        // 补充个人信息
        EmployeePersonalEntity personal = employeePersonalMapper.selectById(employeeId);
        if (personal != null) {
            vo.setResidenceAddress(personal.getResidenceAddress());
            vo.setEmergencyContact(personal.getEmergencyContact());
            vo.setEmergencyPhone(personal.getEmergencyPhone());
        }

        // 可编辑字段白名单
        vo.setEditableFields(new HashSet<>(Arrays.asList(
                "email", "residenceAddress", "emergencyContact", "emergencyPhone")));

        return vo;
    }

    @Override
    @Transactional
    public void updateMyProfile(Long employeeId, ProfileUpdateDTO dto) {
        EmployeePersonalEntity personal = employeePersonalMapper.selectById(employeeId);
        boolean exists = personal != null;

        if (personal == null) {
            personal = new EmployeePersonalEntity();
            personal.setEmployeeId(employeeId);
        }

        if (dto.getEmail() != null) {
            EmployeeEntity emp = new EmployeeEntity();
            emp.setId(employeeId);
            emp.setEmail(dto.getEmail());
            employeeMapper.updateById(emp);
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

    // ===== 工资条相关 =====

    @Override
    public List<PayslipListVO> listMyPayslips(Long employeeId) {
        // 通过 Feign 调用 hrms-payroll 获取工资条列表
        // 此处为桩实现，实际通过 PayrollFeignClient 调用
        return Collections.emptyList();
    }

    @Override
    public List<PayslipTrendVO> getPayslipTrend(Long employeeId) {
        return Collections.emptyList();
    }

    @Override
    public PayslipDetailVO getPayslipDetail(Long employeeId, String period) {
        // 需先校验二次验证 Token（Redis hrms:payslip:verified:{userId}）
        return null;
    }

    @Override
    public byte[] getPayslipPdf(Long employeeId, String period) {
        return new byte[0];
    }

    @Override
    public void verifyPayslip(Long userId, String verifyType, String verifyCode) {
        // 调用 common 的验证服务，写入 Redis TTL 30min
    }

    // ===== 工具方法 =====

    private List<Long> parseCommaSeparatedLongs(String str) {
        if (str == null || str.isBlank()) return null;
        return Arrays.stream(str.split(","))
                .map(String::trim)
                .map(Long::parseLong)
                .collect(Collectors.toList());
    }

    private List<String> parseCommaSeparatedStrings(String str) {
        if (str == null || str.isBlank()) return null;
        return Arrays.stream(str.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private String maskMobile(String mobile) {
        if (mobile == null || mobile.length() != 11) return mobile;
        return mobile.substring(0, 3) + "****" + mobile.substring(7);
    }
}
