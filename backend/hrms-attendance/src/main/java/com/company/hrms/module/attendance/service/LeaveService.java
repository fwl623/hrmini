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
 *
 * 涵盖三大块：
 * 1. 假期余额（年假初始化、调休余额查询）
 * 2. 请假申请（提交、撤销、列表查询）
 * 3. 天数计算（排除周末/节假日，支持 0.5 天折算）
 *
 * 依赖审批引擎 ApprovalEngineService 处理请假流程审批。
 * 年假和调休使用乐观锁（@Version）控制余额并发扣减。
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
    private final com.company.hrms.attendance.mapper.BalanceChangeLogMapper balanceChangeLogMapper;

    // ========================================================================
    //  假期余额
    // ========================================================================

    /**
     * 查询员工假期余额（年假 + 调休）
     *
     * 返回当前员工的 ANNUAL（年假）和 COMP_OFF（调休）余额。
     * 如果数据库无记录（如新员工刚入职还未初始化），返回 0 而非空列表。
     *
     * @param employeeId 员工 ID
     * @return 假期余额列表，至少包含年假和调休两项
     */
    public List<LeaveBalanceVO> getBalances(Long employeeId) {
        List<LeaveBalance> balances = leaveBalanceMapper.selectByEmployeeId(employeeId);
        // 无记录时返回默认 0 值，避免前端展示空列表导致 UI 异常
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
     *
     * 员工入职时由 EmployeeStatusEventListener 触发调用。
     * 根据入职日期计算当年应享年假天数，创建 leave_balance 记录。
     * 首年按比例折算（如 7 月入职仅享半年额度）。
     *
     * @param employeeId 员工 ID
     * @param hireDate   入职日期（用于计算首年比例）
     */
    @Transactional(rollbackFor = Exception.class)
    public void initAnnualBalance(Long employeeId, LocalDate hireDate) {
        int currentYear = LocalDate.now().getYear();
        BigDecimal annualDays = calculateAnnualLeaveDays(hireDate);

        LeaveBalance lb = new LeaveBalance();
        lb.setEmployeeId(employeeId);
        lb.setLeaveType("ANNUAL");
        lb.setYear(currentYear);
        lb.setTotalQuota(annualDays);
        lb.setUsedQuota(java.math.BigDecimal.ZERO);
        lb.setRemainingQuota(annualDays);
        lb.setBalance(annualDays);
        lb.setEffectiveDate(hireDate);
        lb.setVersion(0);
        leaveBalanceMapper.insert(lb);
        log.info("初始化年假余额: empId={}, year={}, days={}", employeeId, currentYear, annualDays);
    }

    /**
     * 年假计算（本公司工龄）
     *
     * 规则：
     *   <1年  → 按比例折算（baseDays 取 5）
     *   1~10年 → 5 天
     *   10~20年 → 10 天
     *   >=20年 → 15 天
     *
     * 首年公式：baseDays × (入职至今天数 / 365)，保留 1 位小数四舍五入。
     * 使用本公司工龄而非社会累计工龄，由业务方确认。
     *
     * @param hireDate 入职日期
     * @return 年假天数（BigDecimal，精确到 0.1 天）
     */
    public BigDecimal calculateAnnualLeaveDays(LocalDate hireDate) {
        if (hireDate == null) {
            return BigDecimal.ZERO;
        }
        int years = (int) ChronoUnit.YEARS.between(hireDate, LocalDate.now());
        int baseDays;
        if (years < 1) {
            baseDays = 5; // 首年按比例折算
        } else if (years < 10) {
            baseDays = 5;
        } else if (years < 20) {
            baseDays = 10;
        } else {
            baseDays = 15;
        }
        // 首年按比例折算（入职当年未满整年）
        if (years < 1) {
            LocalDate yearStart = LocalDate.of(LocalDate.now().getYear(), 1, 1);
            long daysEmployed = ChronoUnit.DAYS.between(hireDate, LocalDate.now());
            return BigDecimal.valueOf(baseDays)
                    .multiply(BigDecimal.valueOf(daysEmployed))
                    .divide(BigDecimal.valueOf(365), 1, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(baseDays);
    }

    // ========================================================================
    //  请假申请
    // ========================================================================

    /**
     * 请假申请列表（分页）
     *
     * 支持按请假类型、状态、员工 ID 筛选。
     * 自动查询员工姓名和部门名称用于展示（因 leave_application 只存 employee_id）。
     *
     * @param pageParam  分页参数
     * @param leaveType  请假类型筛选（可选）
     * @param status     状态筛选（可选）
     * @param employeeId 员工 ID 筛选（可选，null=查全部）
     * @return 分页结果
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

        // 收集员工 ID，批量查询姓名和部门
        // 避免 N+1 问题：逐条 selectById 会导致多次 SQL 查询
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
            vo.setStartTime(la.getStartTime() != null ? la.getStartTime().toString().replace("T", " ") : null);
            vo.setEndTime(la.getEndTime() != null ? la.getEndTime().toString().replace("T", " ") : null);
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
     *
     * 核心流程：
     *   1. 校验开始日期不能是过去日期（请假需提前申请）
     *   2. 服务端重算请假天数（不信任前端传值，排除周末和节假日）
     *   3. 病假 >1 天需上传附件
     *   4. 年假/调休余额预扣（乐观锁 @Version 防止并发超扣）
     *   5. 插入请假记录（status=PENDING）
     *   6. 触发审批流程
     *
     * 余额变动记录 BalanceChangeLog 用于审计追溯，
     * 审批通过/驳回时由 ApprovalEventListener 更新日志状态。
     *
     * @param employeeId 员工 ID（从 SecurityUtils 获取）
     * @param dto        请假申请参数
     * @return 创建的请假申请实体（含 ID 和审批实例 ID）
     */
    @Transactional(rollbackFor = Exception.class)
    public LeaveApplication submit(Long employeeId, LeaveApplicationDTO dto) {
        if (dto.getDays() == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "请假天数不能为空");
        }

        // 前端传 ISO 8601 UTC 时间（如 "2026-07-21T11:00:00.000Z"），转成 Asia/Shanghai
        LocalDateTime startTime = OffsetDateTime.parse(dto.getStartTime(), DateTimeFormatter.ISO_DATE_TIME)
                .atZoneSameInstant(java.time.ZoneId.of("Asia/Shanghai")).toLocalDateTime();
        LocalDateTime endTime = OffsetDateTime.parse(dto.getEndTime(), DateTimeFormatter.ISO_DATE_TIME)
                .atZoneSameInstant(java.time.ZoneId.of("Asia/Shanghai")).toLocalDateTime();

        // 校验：开始日期不能是过去日期（请假需提前申请）
        if (startTime.toLocalDate().isBefore(LocalDate.now())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "请假开始日期不能是过去日期，请选择今天或未来的日期");
        }

        // 重新计算实际请假天数（排除周末和节假日），不信任前端传值
        // 防止前端篡改或时区差异导致天数错误
        BigDecimal days = recalcLeaveDays(startTime, endTime);

        // 病假>1天需上传附件（PRD §6.3.1）
        if ("SICK".equalsIgnoreCase(dto.getLeaveType())
                && days.compareTo(BigDecimal.ONE) > 0
                && (dto.getAttachment() == null || dto.getAttachment().isBlank())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "病假超过1天需上传医院证明");
        }

        // 年假/调休需校验余额（预扣模式）
        // 采用"提交即预扣"策略：提交时扣减余额，驳回时归还，避免审批期间余额被他人占用
        String snapshotJson = null;
        if ("ANNUAL".equalsIgnoreCase(dto.getLeaveType())
                || "COMP_OFF".equalsIgnoreCase(dto.getLeaveType())) {
            String leaveType = dto.getLeaveType().toUpperCase();
            int year = LocalDate.now().getYear();
            LeaveBalance lb = leaveBalanceMapper.selectByEmployeeAndTypeAndYear(employeeId, leaveType, year);
            if (lb == null) {
                throw new BusinessException(ErrorCode.LEAVE_BALANCE_INSUFFICIENT, "请假余额不足");
            }
            // 使用 remaining_quota 判断（后备用 balance，兼容旧数据）
            BigDecimal remaining = lb.getRemainingQuota() != null ? lb.getRemainingQuota() : lb.getBalance();
            if (remaining.compareTo(days) < 0) {
                throw new BusinessException(ErrorCode.LEAVE_BALANCE_INSUFFICIENT, "请假余额不足");
            }
            // 乐观锁预扣：@Version 注解自动校验 version，并发时 updateById 返回 0
            BigDecimal before = remaining;
            BigDecimal after = before.subtract(days);
            if (lb.getRemainingQuota() != null) {
                lb.setRemainingQuota(after);
            }
            lb.setBalance(after); // 同步旧字段
            lb.setUsedQuota(lb.getUsedQuota() != null ? lb.getUsedQuota().add(days) : days);
            int rows = leaveBalanceMapper.updateById(lb);
            if (rows <= 0) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "余额扣减失败，请重试");
            }
            // 记录余额快照（JSON 格式，用于审计对账）
            snapshotJson = "{\"before\":" + before + ",\"after\":" + after + ",\"leaveType\":\"" + leaveType + "\",\"year\":" + year + "}";
            // 写入余额变动日志（初始状态 PENDING，审批通过后变为 CONFIRMED）
            com.company.hrms.attendance.entity.BalanceChangeLog log = new com.company.hrms.attendance.entity.BalanceChangeLog();
            log.setEmployeeId(employeeId);
            log.setLeaveType(leaveType);
            log.setChangeAmount(days);
            log.setSourceType("SUBMIT");
            log.setBalanceBefore(before);
            log.setBalanceAfter(after);
            log.setStatus("PENDING");
            balanceChangeLogMapper.insert(log);
        }

        // 插入请假申请记录
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
        if (snapshotJson != null) {
            app.setDeductedBalanceSnapshot(snapshotJson);
        }
        leaveApplicationMapper.insert(app);

        // 发起审批流程（LEAVE 类型）
        // 审批引擎会按流程定义自动路由到相应审批人
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
        if (app.getAttachmentUrl() != null && !app.getAttachmentUrl().isBlank()) {
            form.put("attachment", app.getAttachmentUrl());
        }
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

    /**
     * 撤销请假（支持本人撤销和 HR 代撤）
     *
     * 流程：
     *   1. 校验申请存在且状态为 PENDING
     *   2. 本人撤销校验 employeeId 归属
     *   3. 恢复年假/调休预扣余额，记录 REFUND 日志
     *   4. 撤回审批实例（withdrawInstance）
     *   5. 更新申请状态为 CANCELLED
     *
     * @param id         请假申请 ID
     * @param employeeId 员工 ID（本人撤销时传入，HR 代撤传 null）
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id, Long employeeId) {
        LeaveApplication app = leaveApplicationMapper.selectById(id);
        if (app == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "请假申请不存在");
        }
        // 本人撤销时校验归属，防止撤销他人的申请
        if (employeeId != null && !employeeId.equals(app.getEmployeeId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能撤销本人的请假申请");
        }
        // 仅 PENDING 状态的申请可撤销（已通过/已驳回的走其他流程）
        if (!"PENDING".equals(app.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "仅待审批状态的申请可撤销");
        }

        // 恢复预扣余额（年假/调休）
        if ("ANNUAL".equals(app.getLeaveType()) || "COMP_OFF".equals(app.getLeaveType())) {
            LeaveBalance balance = leaveBalanceMapper.selectByEmployeeAndTypeAndYear(
                    app.getEmployeeId(), app.getLeaveType(), LocalDate.now().getYear());
            if (balance != null) {
                BigDecimal before = balance.getRemainingQuota() != null ? balance.getRemainingQuota() : balance.getBalance();
                BigDecimal after = before.add(app.getLeaveDays());
                if (balance.getRemainingQuota() != null) balance.setRemainingQuota(after);
                balance.setBalance(after);
                balance.setUsedQuota(balance.getUsedQuota() != null
                        ? balance.getUsedQuota().subtract(app.getLeaveDays()) : BigDecimal.ZERO);
                // @Version 乐观锁，防止并发操作
                leaveBalanceMapper.updateById(balance);

                // 记录归还日志（REFUNDED 表示已归还）
                com.company.hrms.attendance.entity.BalanceChangeLog log = new com.company.hrms.attendance.entity.BalanceChangeLog();
                log.setEmployeeId(app.getEmployeeId());
                log.setLeaveType(app.getLeaveType());
                log.setChangeAmount(app.getLeaveDays().negate());
                log.setSourceType("REFUND");
                log.setSourceId(app.getId());
                log.setBalanceBefore(before);
                log.setBalanceAfter(after);
                log.setStatus("REFUNDED");
                log.setRemark("撤回归还");
                balanceChangeLogMapper.insert(log);
            }
        }

        // 先撤回审批实例，再改请假单状态
        // 本人撤：发起人校验；HR 代撤：引擎侧放行 HR/管理员
        if (app.getInstanceId() != null) {
            Long operatorId = SecurityUtils.getUserId();
            approvalEngineService.withdrawInstance(app.getInstanceId(),
                    operatorId != null ? operatorId : app.getEmployeeId());
        }
        app.setStatus("CANCELLED");
        app.setCancelReason("WITHDRAW");
        leaveApplicationMapper.updateById(app);
        log.info("撤销请假: id={}, empId={}", id, app.getEmployeeId());
    }

    // ========================================================================
    //  天数计算
    // ========================================================================

    /**
     * 根据起止时间重算请假天数（排除周末和节假日）
     *
     * 核心规则：
     *   - 只计算工作日的天数（从 WorkdayConfig 读取周几上班）
     *   - 排除法定节假日（从 HolidayCalendar 读取）
     *   - 首日/末日按小时折算：>=4h=1天，>0h=0.5天
     *   - 中间工作日计为 1 天
     *   - 同一天：>=4h=1天，>0h=0.5天，否则=0
     *   - 结束时间为午夜 00:00 时，结束日期减一天处理
     *
     * @param start 开始时间
     * @param end   结束时间
     * @return 实际请假天数（支持 0.5 天）
     */
    private BigDecimal recalcLeaveDays(LocalDateTime start, LocalDateTime end) {
        // 加载工作日配置（周几上班/不上班）
        List<WorkdayConfig> workdayConfigs = workdayConfigMapper.selectList(null);
        Set<Integer> workdaySet = workdayConfigs.stream()
                .filter(w -> w.getIsWorkday() == 1)
                .map(WorkdayConfig::getDayOfWeek)
                .collect(Collectors.toSet());

        // 加载法定节假日
        List<HolidayCalendar> holidays = holidayCalendarMapper.selectList(null);
        Set<LocalDate> holidayDates = holidays.stream()
                .map(HolidayCalendar::getHolidayDate)
                .collect(Collectors.toSet());

        BigDecimal days = BigDecimal.ZERO;
        LocalDate current = start.toLocalDate();
        LocalDate endDate = end.toLocalDate();
        // 结束时间为午夜 00:00 的处理：实际表示前一天的最后一刻
        boolean endIsMidnight = end.toLocalTime().equals(java.time.LocalTime.MIDNIGHT);
        if (endIsMidnight) {
            endDate = endDate.minusDays(1);
        }

        while (!current.isAfter(endDate)) {
            DayOfWeek dow = current.getDayOfWeek();
            int dayOfWeek = dow.getValue(); // 1=Mon..7=Sun

            boolean isWorkday = workdaySet.contains(dayOfWeek);
            boolean isHoliday = holidayDates.contains(current);

            // 只有工作日才计为请假天数，周末和法定节假日不计
            if (isWorkday && !isHoliday) {
                boolean isFirstDay = current.equals(start.toLocalDate());
                boolean isLastDay = current.equals(endDate);

                if (isFirstDay && isLastDay && start.toLocalTime().isAfter(end.toLocalTime())) {
                    // 同一天且开始时间晚于结束时间，无效区间（不计天数）
                } else if (isFirstDay || isLastDay) {
                    // 首日或末日：按小时折算
                    long partialHours;
                    if (isFirstDay) {
                        partialHours = java.time.temporal.ChronoUnit.HOURS.between(
                                start.toLocalTime(), java.time.LocalTime.MAX);
                    } else {
                        if (endIsMidnight) {
                            partialHours = 24L; // 结束时间为午夜，末日视为全天
                        } else {
                            partialHours = java.time.temporal.ChronoUnit.HOURS.between(
                                    java.time.LocalTime.MIN, end.toLocalTime());
                        }
                    }
                    if (partialHours >= 4) {
                        days = days.add(BigDecimal.ONE);
                    } else if (partialHours > 0) {
                        days = days.add(BigDecimal.valueOf(0.5));
                    }
                } else {
                    // 中间完整工作日计 1 天
                    days = days.add(BigDecimal.ONE);
                }
            }
            current = current.plusDays(1);
        }

        // 同一天：单独按小时折算（覆盖遍历逻辑中未处理的同一天场景）
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
     * 预览请假天数（供前端提交前展示）
     *
     * 不提交申请，只计算起止时间对应的实际请假天数，
     * 排除周末和节假日，支持 0.5 天。
     *
     * @param startTimeStr 开始时间（ISO 8601 格式）
     * @param endTimeStr   结束时间（ISO 8601 格式）
     * @return 计算后的天数结果
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
