package com.company.hrms.module.attendance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.hrms.attendance.entity.HolidayCalendar;
import com.company.hrms.attendance.entity.LeaveApplication;
import com.company.hrms.attendance.entity.LeaveBalance;
import com.company.hrms.attendance.entity.WorkdayConfig;
import com.company.hrms.attendance.mapper.HolidayCalendarMapper;
import com.company.hrms.attendance.mapper.LeaveApplicationMapper;
import com.company.hrms.attendance.mapper.LeaveBalanceMapper;
import com.company.hrms.attendance.mapper.WorkdayConfigMapper;
import com.company.hrms.common.approval.ApprovalEngineService;
import com.company.hrms.common.approval.CreateApprovalRequest;
import com.company.hrms.common.approval.CreateApprovalResult;
import com.company.hrms.common.exception.BusinessException;
import com.company.hrms.common.exception.ErrorCode;
import com.company.hrms.common.security.SecurityUtils;
import com.company.hrms.common.web.PageParam;
import com.company.hrms.common.web.PageResult;
import com.company.hrms.module.attendance.dto.CalcDaysVO;
import com.company.hrms.module.attendance.dto.LeaveApplicationDTO;
import com.company.hrms.module.attendance.dto.LeaveApplicationVO;
import com.company.hrms.module.attendance.dto.LeaveBalanceVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 请假管理 Service
 * 涵盖假期余额、请假申请、天数计算
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeaveService {

    private final LeaveBalanceMapper leaveBalanceMapper;
    private final LeaveApplicationMapper leaveApplicationMapper;
    private final WorkdayConfigMapper workdayConfigMapper;
    private final HolidayCalendarMapper holidayCalendarMapper;
    private final ApprovalEngineService approvalEngineService;
    private final com.company.hrms.employee.mapper.EmployeeMapper employeeMapper;

    // ========== 假期余额 ==========

    /**
     * 查询员工假期余额（年假 + 调休）
     */
    public List<LeaveBalanceVO> getBalances(Long employeeId) {
        List<LeaveBalance> balances = leaveBalanceMapper.selectByEmployeeId(employeeId);
        // 如果没有任何假期余额记录，返回默认值（显示0而不是空）
        if (balances == null || balances.isEmpty()) {
            return java.util.List.of(
                    new LeaveBalanceVO("ANNUAL", java.math.BigDecimal.ZERO),
                    new LeaveBalanceVO("COMP_OFF", java.math.BigDecimal.ZERO));
        }
        List<LeaveBalanceVO> vos = new ArrayList<>();
        for (LeaveBalance lb : balances) {
            vos.add(new LeaveBalanceVO(lb.getLeaveType(), lb.getBalance()));
        }
        return vos;
    }

    /**
     * 初始化年假余额（入职时调用）
     */
    @Transactional(rollbackFor = Exception.class)
    public void initAnnualBalance(Long employeeId, LocalDate hireDate) {
        int currentYear = LocalDate.now().getYear();
        BigDecimal annualDays = calculateAnnualLeaveDays(hireDate);

        LeaveBalance balance = new LeaveBalance();
        balance.setEmployeeId(employeeId);
        balance.setLeaveType("ANNUAL");
        balance.setBalance(annualDays);
        balance.setYear(currentYear);
        leaveBalanceMapper.insert(balance);
        log.info("初始化年假余额: empId={}, year={}, days={}", employeeId, currentYear, annualDays);
    }

    /**
     * 年假计算
     * <1年=0, 1~10年=5, 10~20年=10, ≥20年=15, 首年按比例折算
     */
    public BigDecimal calculateAnnualLeaveDays(LocalDate hireDate) {
        if (hireDate == null) {
            return BigDecimal.ZERO;
        }
        int years = (int) ChronoUnit.YEARS.between(hireDate, LocalDate.now());
        if (years < 1) {
            return BigDecimal.ZERO;
        }
        int baseDays;
        if (years < 10) {
            baseDays = 5;
        } else if (years < 20) {
            baseDays = 10;
        } else {
            baseDays = 15;
        }
        // 首年按比例折算
        if (years < 1) {
            LocalDate yearStart = LocalDate.of(LocalDate.now().getYear(), 1, 1);
            long daysEmployed = ChronoUnit.DAYS.between(hireDate, LocalDate.now());
            return BigDecimal.valueOf(baseDays)
                    .multiply(BigDecimal.valueOf(daysEmployed))
                    .divide(BigDecimal.valueOf(365), 1, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(baseDays);
    }

    // ========== 请假申请 ==========

    /**
     * 请假申请列表（分页）
     */
    public PageResult<LeaveApplicationVO> pageApplications(PageParam pageParam, String leaveType, String status, Long employeeId) {
        LambdaQueryWrapper<LeaveApplication> wrapper = new LambdaQueryWrapper<LeaveApplication>()
                .orderByDesc(LeaveApplication::getCreatedAt);

        if (leaveType != null) {
            wrapper.eq(LeaveApplication::getLeaveType, leaveType);
        }
        if (status != null) {
            wrapper.eq(LeaveApplication::getStatus, status);
        }
        if (employeeId != null) {
            wrapper.eq(LeaveApplication::getEmployeeId, employeeId);
        }

        IPage<LeaveApplication> page = leaveApplicationMapper.selectPage(
                new Page<>(pageParam.getPage(), pageParam.getPageSize()), wrapper);

        // 收集员工ID，批量查询姓名和部门
        java.util.Set<Long> empIds = page.getRecords().stream()
                .map(LeaveApplication::getEmployeeId)
                .collect(java.util.stream.Collectors.toSet());
        java.util.Map<Long, String> nameMap = new java.util.HashMap<>();
        java.util.Map<Long, String> deptMap = new java.util.HashMap<>();
        if (!empIds.isEmpty()) {
            String idList = empIds.stream().map(String::valueOf)
                    .collect(java.util.stream.Collectors.joining(","));
            List<com.company.hrms.employee.entity.Employee> empList = employeeMapper.search(
                    null, null, null, null, null, null, null,
                    " AND e.id IN (" + idList + ")");
            for (com.company.hrms.employee.entity.Employee emp : empList) {
                if (emp != null && emp.getId() != null) {
                    nameMap.put(emp.getId(), emp.getName());
                    deptMap.put(emp.getId(), emp.getDepartmentName());
                }
            }
        }

        List<LeaveApplicationVO> voList = new ArrayList<>();
        for (LeaveApplication la : page.getRecords()) {
            LeaveApplicationVO vo = new LeaveApplicationVO();
            vo.setId(la.getId());
            vo.setEmployeeId(la.getEmployeeId());
            vo.setEmployeeName(nameMap.getOrDefault(la.getEmployeeId(), String.valueOf(la.getEmployeeId())));
            vo.setDepartment(deptMap.get(la.getEmployeeId()));
            vo.setLeaveType(la.getLeaveType());
            vo.setStartTime(la.getStartTime() != null ? la.getStartTime().toString() : null);
            vo.setEndTime(la.getEndTime() != null ? la.getEndTime().toString() : null);
            vo.setLeaveDays(la.getLeaveDays() != null ? la.getLeaveDays().doubleValue() : 0);
            vo.setReason(la.getReason());
            vo.setStatus(la.getStatus());
            vo.setInstanceId(la.getInstanceId());
            voList.add(vo);
        }

        return PageResult.of(voList, page.getTotal(), pageParam);
    }

    /**
     * 提交请假申请
     * 余额预扣 → 插入申请 → 返回
     */
    @Transactional(rollbackFor = Exception.class)
    public LeaveApplication submit(Long employeeId, LeaveApplicationDTO dto) {
        if (dto.getDays() == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "请假天数不能为空");
        }

        LocalDateTime startTime = OffsetDateTime.parse(dto.getStartTime(), DateTimeFormatter.ISO_DATE_TIME)
                .atZoneSameInstant(java.time.ZoneId.of("Asia/Shanghai")).toLocalDateTime();
        LocalDateTime endTime = OffsetDateTime.parse(dto.getEndTime(), DateTimeFormatter.ISO_DATE_TIME)
                .atZoneSameInstant(java.time.ZoneId.of("Asia/Shanghai")).toLocalDateTime();

        // 重新计算实际请假天数（排除周末和节假日），不信任前端传值
        BigDecimal days = recalcLeaveDays(startTime, endTime);

        // 病假>1天需上传附件（PRD §6.3.1）
        if ("SICK".equalsIgnoreCase(dto.getLeaveType())
                && days.compareTo(BigDecimal.ONE) > 0
                && (dto.getAttachment() == null || dto.getAttachment().isBlank())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "病假超过1天需上传医院证明");
        }

        // 年假/调休需校验余额
        if ("ANNUAL".equalsIgnoreCase(dto.getLeaveType())
                || "COMP_OFF".equalsIgnoreCase(dto.getLeaveType())) {
            String leaveType = dto.getLeaveType().toUpperCase();
            int year = LocalDate.now().getYear();
            LeaveBalance balance = leaveBalanceMapper.selectByEmployeeAndTypeAndYear(employeeId, leaveType, year);
            if (balance == null || balance.getBalance().compareTo(days) < 0) {
                throw new BusinessException(ErrorCode.LEAVE_BALANCE_INSUFFICIENT, "请假余额不足");
            }
            // 预扣余额
            balance.setBalance(balance.getBalance().subtract(days));
            leaveBalanceMapper.updateById(balance);
        }

        LeaveApplication app = new LeaveApplication();
        app.setEmployeeId(employeeId);
        app.setLeaveType(dto.getLeaveType().toUpperCase());
        app.setStartTime(startTime);
        app.setEndTime(endTime);
        app.setLeaveDays(days);
        app.setReason(dto.getReason());
        app.setHandoverEmployeeId(dto.getHandoverEmployeeId());
        app.setAttachmentUrl(dto.getAttachment());
        app.setStatus("PENDING");
        leaveApplicationMapper.insert(app);

        CreateApprovalRequest req = new CreateApprovalRequest();
        req.setProcessType("LEAVE");
        req.setBusinessId(app.getId());
        req.setApplicantId(SecurityUtils.getCurrentUser().getUserId());
        req.setTitle("请假申请#" + app.getId());
        req.setBusinessSummary(app.getLeaveType() + " " + days + "天");
        req.setBusinessNo("LEAVE-" + app.getId());
        Map<String, Object> form = new HashMap<>();
        form.put("leaveType", app.getLeaveType());
        form.put("days", days);
        form.put("employeeId", employeeId);
        req.setFormData(form);
        CreateApprovalResult result = approvalEngineService.createInstance(req);
        app.setInstanceId(result.getInstanceId());
        leaveApplicationMapper.updateById(app);

        log.info("提交请假: empId={}, type={}, days={}, id={}, instanceId={}",
                employeeId, dto.getLeaveType(), days, app.getId(), result.getInstanceId());
        return app;
    }

    /**
     * 撤销请假（仅 PENDING 状态）
     * 恢复预扣余额
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        cancel(id, null);
    }

    /** 本人撤销时传入 employeeId 校验归属 */
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id, Long employeeId) {
        LeaveApplication app = leaveApplicationMapper.selectById(id);
        if (app == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "请假申请不存在");
        }
        if (employeeId != null && !employeeId.equals(app.getEmployeeId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能撤销本人的请假申请");
        }
        if (!"PENDING".equals(app.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "仅待审批状态的申请可撤销");
        }

        // 恢复预扣余额
        if ("ANNUAL".equals(app.getLeaveType()) || "COMP_OFF".equals(app.getLeaveType())) {
            LeaveBalance balance = leaveBalanceMapper.selectByEmployeeAndTypeAndYear(
                    app.getEmployeeId(), app.getLeaveType(), LocalDate.now().getYear());
            if (balance != null) {
                balance.setBalance(balance.getBalance().add(app.getLeaveDays()));
                leaveBalanceMapper.updateById(balance);
            }
        }

        // 先撤回审批实例，再改请假单状态。本人撤：发起人校验；HR 代撤：引擎侧放行 HR/管理员
        if (app.getInstanceId() != null) {
            Long operatorId = SecurityUtils.getUserId();
            approvalEngineService.withdrawInstance(app.getInstanceId(),
                    operatorId != null ? operatorId : app.getEmployeeId());
        }
        app.setStatus("CANCELLED");
        leaveApplicationMapper.updateById(app);
        log.info("撤销请假: id={}, empId={}", id, app.getEmployeeId());
    }

    // ========== 天数预览 ==========

    /**
     * 根据起止时间重算请假天数（排除周末和节假日）
     */
    private BigDecimal recalcLeaveDays(LocalDateTime start, LocalDateTime end) {
        List<WorkdayConfig> workdayConfigs = workdayConfigMapper.selectList(null);
        Set<Integer> workdaySet = workdayConfigs.stream()
                .filter(w -> w.getIsWorkday() == 1)
                .map(WorkdayConfig::getDayOfWeek)
                .collect(Collectors.toSet());

        List<HolidayCalendar> holidays = holidayCalendarMapper.selectList(null);
        Set<LocalDate> holidayDates = holidays.stream()
                .map(HolidayCalendar::getHolidayDate)
                .collect(Collectors.toSet());

        BigDecimal days = BigDecimal.ZERO;
        LocalDate current = start.toLocalDate();
        LocalDate endDate = end.toLocalDate();

        while (!current.isAfter(endDate)) {
            DayOfWeek dow = current.getDayOfWeek();
            int dayOfWeek = dow.getValue(); // 1=Mon..7=Sun

            boolean isWorkday = workdaySet.contains(dayOfWeek);
            boolean isHoliday = holidayDates.contains(current);

            if (isWorkday && !isHoliday) {
                boolean isFirstDay = current.equals(start.toLocalDate());
                boolean isLastDay = current.equals(endDate);

                if (isFirstDay && isLastDay && start.toLocalTime().isAfter(end.toLocalTime())) {
                    // 同一天开始结束，无效区间
                } else if (isFirstDay || isLastDay) {
                    days = days.add(BigDecimal.valueOf(0.5));
                } else {
                    days = days.add(BigDecimal.ONE);
                }
            }
            current = current.plusDays(1);
        }

        // 同一天：按小时折算
        if (start.toLocalDate().equals(end.toLocalDate())) {
            long hours = java.time.temporal.ChronoUnit.HOURS.between(start, end);
            if (hours >= 4) {
                days = BigDecimal.ONE;
            } else if (hours > 0) {
                days = BigDecimal.valueOf(0.5);
            } else {
                days = BigDecimal.ZERO;
            }
        }

        return days;
    }

    /**
     * 预览请假天数
     * 排除周末和法定节假日，支持 0.5 天
     */
    public CalcDaysVO calcDays(String startTimeStr, String endTimeStr) {
        LocalDateTime start = OffsetDateTime.parse(startTimeStr, DateTimeFormatter.ISO_DATE_TIME)
                .atZoneSameInstant(java.time.ZoneId.of("Asia/Shanghai")).toLocalDateTime();
        LocalDateTime end = OffsetDateTime.parse(endTimeStr, DateTimeFormatter.ISO_DATE_TIME)
                .atZoneSameInstant(java.time.ZoneId.of("Asia/Shanghai")).toLocalDateTime();
        BigDecimal days = recalcLeaveDays(start, end);
        return new CalcDaysVO(days);
    }
}
