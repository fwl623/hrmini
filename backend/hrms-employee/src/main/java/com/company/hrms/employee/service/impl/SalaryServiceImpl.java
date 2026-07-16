package com.company.hrms.employee.service.impl;

import com.company.hrms.common.exception.BusinessException;
import com.company.hrms.common.exception.ErrorCode;
import com.company.hrms.common.security.SecurityUtils;
import com.company.hrms.employee.dto.SalaryProfileUpdateDTO;
import com.company.hrms.employee.entity.Employee;
import com.company.hrms.employee.entity.EmployeeSalaryHistory;
import com.company.hrms.employee.entity.EmployeeSalaryProfile;
import com.company.hrms.employee.mapper.EmployeeMapper;
import com.company.hrms.employee.mapper.EmployeeSalaryHistoryMapper;
import com.company.hrms.employee.mapper.EmployeeSalaryProfileMapper;
import com.company.hrms.employee.service.SalaryService;
import com.company.hrms.employee.vo.SalaryProfileVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 薪资档案服务实现
 *
 * SYS_ADMIN 对薪资接口双拦截（权限拦截器返回 20002），
 * 本服务不重复校验角色。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalaryServiceImpl implements SalaryService {

    private final EmployeeMapper employeeMapper;
    private final EmployeeSalaryProfileMapper salaryProfileMapper;
    private final EmployeeSalaryHistoryMapper salaryHistoryMapper;

    @Override
    public SalaryProfileVO getProfile(Long employeeId) {
        employeeMapper.selectById(employeeId); // 校验员工存在

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
    public void updateProfile(Long employeeId, SalaryProfileUpdateDTO dto) {
        Employee emp = employeeMapper.selectById(employeeId);
        if (emp == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "员工不存在");
        }

        EmployeeSalaryProfile profile = salaryProfileMapper.selectByEmployeeId(employeeId);
        if (profile == null) {
            throw new BusinessException(ErrorCode.SALARY_PROFILE_MISSING);
        }

        // 记录调薪历史
        if (dto.getBaseSalary() != null && !dto.getBaseSalary().equals(profile.getBaseSalary())) {
            EmployeeSalaryHistory history = new EmployeeSalaryHistory();
            history.setEmployeeId(employeeId);
            history.setFieldName("baseSalary");
            history.setOldValue(profile.getBaseSalary());
            history.setNewValue(dto.getBaseSalary());
            history.setEffectiveDate(LocalDate.now());
            history.setReason("HR编辑薪资档案");
            history.setOperatorId(SecurityUtils.getUserId());
            salaryHistoryMapper.insert(history);
        }

        // 更新
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
}
