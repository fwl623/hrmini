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
 * <p>
 * 提供薪资档案的查询和更新功能。
 * 更新时自动比较新旧值，若基本工资变更则写入调薪历史表。
 * SYS_ADMIN 角色对薪资接口的拦截由权限层统一处理，本服务不重复校验。
 * </p>
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
        // 校验员工存在
        employeeMapper.selectById(employeeId);

        // 查询薪资档案
        EmployeeSalaryProfile profile = salaryProfileMapper.selectByEmployeeId(employeeId);
        if (profile == null) {
            throw new BusinessException(ErrorCode.SALARY_PROFILE_MISSING);
        }

        // 组装 VO 返回
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
        // 校验员工存在
        Employee emp = employeeMapper.selectById(employeeId);
        if (emp == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "员工不存在");
        }

        // 查询现有薪资档案
        EmployeeSalaryProfile profile = salaryProfileMapper.selectByEmployeeId(employeeId);
        if (profile == null) {
            throw new BusinessException(ErrorCode.SALARY_PROFILE_MISSING);
        }

        // 记录调薪历史（仅 baseSalary 变更时记录）
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

        // 更新薪资档案字段
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
