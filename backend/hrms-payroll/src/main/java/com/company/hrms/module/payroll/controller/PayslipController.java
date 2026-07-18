package com.company.hrms.module.payroll.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.hrms.common.exception.BusinessException;
import com.company.hrms.common.exception.ErrorCode;
import com.company.hrms.common.security.SecurityUtils;
import com.company.hrms.common.web.PageParam;
import com.company.hrms.common.web.Result;
import com.company.hrms.employee.entity.Employee;
import com.company.hrms.employee.mapper.EmployeeMapper;
import com.company.hrms.module.auth.entity.SysUser;
import com.company.hrms.module.auth.mapper.SysUserMapper;
import com.company.hrms.module.org.entity.Department;
import com.company.hrms.module.org.mapper.DepartmentMapper;
import com.company.hrms.module.payroll.dto.*;
import com.company.hrms.payroll.entity.PayrollBatch;
import com.company.hrms.payroll.entity.PayrollDetail;
import com.company.hrms.payroll.entity.PayslipViewLog;
import com.company.hrms.payroll.mapper.PayrollBatchMapper;
import com.company.hrms.payroll.mapper.PayrollDetailMapper;
import com.company.hrms.payroll.mapper.PayslipViewLogMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;

@RestController
@RequiredArgsConstructor
public class PayslipController {

    private final PayrollBatchMapper batchMapper;
    private final PayrollDetailMapper detailMapper;
    private final PayslipViewLogMapper viewLogMapper;
    private final EmployeeMapper employeeMapper;
    private final SysUserMapper sysUserMapper;
    private final DepartmentMapper departmentMapper;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    // ==================== HR 端 ====================

    /**
     * HR端工资条列表
     * GET /payroll/payslips?period=
     * 查 PayrollDetail，按批次+账期过滤，组装 PayslipVO
     */
    @GetMapping("/payroll/payslips")
    public Result<Object> list(PageParam pageParam,
                               @RequestParam(required = false) String period) {
        LambdaQueryWrapper<PayrollBatch> wrapper = new LambdaQueryWrapper<PayrollBatch>()
                .orderByDesc(PayrollBatch::getPeriod);
        if (period != null && !period.isEmpty()) {
            wrapper.eq(PayrollBatch::getPeriod, period);
        }
        var page = batchMapper.selectPage(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageParam.getPage(), pageParam.getPageSize()),
                wrapper);

        List<PayslipVO> voList = new ArrayList<>();
        for (PayrollBatch batch : page.getRecords()) {
            List<PayrollDetail> details = detailMapper.selectByBatchId(batch.getId());
            for (PayrollDetail detail : details) {
                Employee emp = employeeMapper.selectById(detail.getEmployeeId());
                PayslipVO vo = new PayslipVO();
                vo.setEmployeeId(detail.getEmployeeId());
                vo.setEmployeeName(emp != null ? emp.getName() : "");
                vo.setPeriod(batch.getPeriod());
                vo.setGrossSalary(detail.getGrossSalary() != null ? detail.getGrossSalary().doubleValue() : 0);
                vo.setNetSalary(detail.getNetSalary() != null ? detail.getNetSalary().doubleValue() : 0);
                vo.setStatus(batch.getStatus());
                voList.add(vo);
            }
        }
        return Result.success(Map.of("list", voList, "total", page.getTotal()));
    }

    /**
     * HR端查看某期详情
     * GET /payroll/payslips/{month}?employeeId=
     * 按账期查批次，再查明细
     */
    @GetMapping("/payroll/payslips/{month}")
    public Result<PayslipDetailVO> detail(@PathVariable String month,
                                           @RequestParam Long employeeId) {
        PayrollBatch batch = batchMapper.selectByPeriod(month);
        if (batch == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "该账期批次不存在");
        }
        PayrollDetail detail = detailMapper.selectByBatchAndEmployee(batch.getId(), employeeId);
        if (detail == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "未找到该员工核算明细");
        }
        return Result.success(buildPayslipDetailVO(detail, batch.getPeriod(), employeeId));
    }

    // ==================== 员工端 ====================

    /**
     * 员工端工资条列表
     * GET /profile/payslips
     * 查本人所有已发放批次的 PayslipVO
     */
    @GetMapping("/profile/payslips")
    public Result<Object> portalList(PageParam pageParam) {
        Long empId = SecurityUtils.getCurrentUser().getEmployeeId();
        Employee emp = employeeMapper.selectById(empId);

        LambdaQueryWrapper<PayrollBatch> batchWrapper = new LambdaQueryWrapper<PayrollBatch>()
                .eq(PayrollBatch::getStatus, "DISTRIBUTED")
                .orderByDesc(PayrollBatch::getPeriod);
        var batchPage = batchMapper.selectPage(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageParam.getPage(), pageParam.getPageSize()),
                batchWrapper);

        List<PayslipVO> voList = new ArrayList<>();
        for (PayrollBatch batch : batchPage.getRecords()) {
            PayrollDetail detail = detailMapper.selectByBatchAndEmployee(batch.getId(), empId);
            if (detail != null) {
                PayslipVO vo = new PayslipVO();
                vo.setEmployeeId(empId);
                vo.setEmployeeName(emp != null ? emp.getName() : "");
                vo.setPeriod(batch.getPeriod());
                vo.setGrossSalary(detail.getGrossSalary() != null ? detail.getGrossSalary().doubleValue() : 0);
                vo.setNetSalary(detail.getNetSalary() != null ? detail.getNetSalary().doubleValue() : 0);
                vo.setStatus(batch.getStatus());
                voList.add(vo);
            }
        }
        return Result.success(Map.of("list", voList, "total", batchPage.getTotal()));
    }

    /**
     * 员工端详情
     * GET /profile/payslips/{period}
     * 查本人某期明细（需批次状态为 APPROVED 或 DISTRIBUTED）
     */
    @GetMapping("/profile/payslips/{period}")
    public Result<PayslipDetailVO> portalDetail(@PathVariable String period) {
        Long empId = SecurityUtils.getCurrentUser().getEmployeeId();

        PayrollBatch batch = batchMapper.selectByPeriod(period);
        if (batch == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "该账期批次不存在");
        }
        if (!"APPROVED".equals(batch.getStatus()) && !"DISTRIBUTED".equals(batch.getStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "该批次尚未发放，无法查看");
        }
        PayrollDetail detail = detailMapper.selectByBatchAndEmployee(batch.getId(), empId);
        if (detail == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "未找到该员工核算明细");
        }

        // 记录查看日志
        PayslipViewLog viewLog = new PayslipViewLog();
        viewLog.setEmployeeId(empId);
        viewLog.setBatchId(batch.getId());
        viewLog.setViewedAt(LocalDateTime.now());
        viewLog.setVerifyMethod("PASSWORD");
        viewLogMapper.insert(viewLog);

        return Result.success(buildPayslipDetailVO(detail, batch.getPeriod(), empId));
    }

    /**
     * 二次验证（密码）
     * POST /profile/payslips/verify
     */
    @PostMapping("/profile/payslips/verify")
    public Result<VerifyVO> verify(@RequestBody VerifyDTO dto) {
        Long userId = SecurityUtils.getUserId();
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户不存在");
        }
        boolean verified = passwordEncoder.matches(dto.getPassword(), user.getPasswordHash());
        VerifyVO vo = new VerifyVO();
        vo.setVerified(verified);
        return Result.success(vo);
    }

    /**
     * 近6月趋势
     * GET /profile/payslips/trend
     * 聚合本人已发放批次
     */
    @GetMapping("/profile/payslips/trend")
    public Result<List<PayrollTrendVO>> trend() {
        Long empId = SecurityUtils.getCurrentUser().getEmployeeId();

        // 计算近6个月的账期
        YearMonth current = YearMonth.now();
        List<String> periods = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            periods.add(current.minusMonths(i).toString());
        }

        // 查询所有已发放且在这6个账期内的批次
        List<PayrollBatch> batches = batchMapper.selectList(
                new LambdaQueryWrapper<PayrollBatch>()
                        .eq(PayrollBatch::getStatus, "DISTRIBUTED")
                        .in(PayrollBatch::getPeriod, periods)
                        .orderByAsc(PayrollBatch::getPeriod));

        List<PayrollTrendVO> trendList = new ArrayList<>();
        for (PayrollBatch batch : batches) {
            PayrollDetail detail = detailMapper.selectByBatchAndEmployee(batch.getId(), empId);
            if (detail != null) {
                PayrollTrendVO vo = new PayrollTrendVO();
                vo.setPeriod(batch.getPeriod());
                vo.setNetSalary(detail.getNetSalary() != null ? detail.getNetSalary().doubleValue() : 0);
                trendList.add(vo);
            }
        }
        return Result.success(trendList);
    }

    // ==================== 私有方法 ====================

    /**
     * 构建 PayslipDetailVO（解析 detailJson，查询员工/部门信息）
     */
    private PayslipDetailVO buildPayslipDetailVO(PayrollDetail detail, String period, Long employeeId) {
        PayslipDetailVO vo = new PayslipDetailVO();
        vo.setPeriod(period);

        // 员工信息
        Employee emp = employeeMapper.selectById(employeeId);
        PayslipDetailVO.EmployeeInfo empInfo = new PayslipDetailVO.EmployeeInfo();
        if (emp != null) {
            empInfo.setName(emp.getName());
            empInfo.setEmployeeNo(emp.getEmployeeNo());
            if (emp.getDepartmentId() != null) {
                Department dept = departmentMapper.selectById(emp.getDepartmentId());
                empInfo.setDepartment(dept != null ? dept.getName() : "");
            } else {
                empInfo.setDepartment("");
            }
        } else {
            empInfo.setName("");
            empInfo.setEmployeeNo("");
            empInfo.setDepartment("");
        }
        vo.setEmployee(empInfo);

        // 解析 detailJson 为 earnings 和 deductions
        List<PayslipItemVO> earnings = new ArrayList<>();
        List<PayslipItemVO> deductions = new ArrayList<>();
        if (detail.getDetailJson() != null && !detail.getDetailJson().isEmpty()) {
            try {
                List<Map<String, Object>> items = objectMapper.readValue(
                        detail.getDetailJson(),
                        new TypeReference<List<Map<String, Object>>>() {});
                for (Map<String, Object> item : items) {
                    PayslipItemVO itemVO = new PayslipItemVO();
                    itemVO.setName((String) item.get("itemName"));
                    itemVO.setAmount(item.get("amount") instanceof Number
                            ? ((Number) item.get("amount")).doubleValue() : 0);
                    String type = (String) item.get("type");
                    if ("EARNING".equals(type)) {
                        earnings.add(itemVO);
                    } else if ("DEDUCTION".equals(type)) {
                        deductions.add(itemVO);
                    }
                }
            } catch (Exception e) {
                // 解析失败时忽略
            }
        }
        vo.setEarnings(earnings);
        vo.setDeductions(deductions);

        // 汇总金额
        vo.setGrossSalary(detail.getGrossSalary() != null ? detail.getGrossSalary().doubleValue() : 0);
        vo.setNetSalary(detail.getNetSalary() != null ? detail.getNetSalary().doubleValue() : 0);
        double totalDeduction = deductions.stream().mapToDouble(PayslipItemVO::getAmount).sum();
        vo.setTotalDeduction(totalDeduction);

        return vo;
    }
}
