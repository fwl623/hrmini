package com.company.hrms.module.attendance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.hrms.attendance.entity.HolidayCalendar;
import com.company.hrms.attendance.entity.OvertimeApplication;
import com.company.hrms.attendance.entity.OvertimeLedger;
import com.company.hrms.attendance.entity.WorkdayConfig;
import com.company.hrms.attendance.mapper.HolidayCalendarMapper;
import com.company.hrms.attendance.mapper.OvertimeApplicationMapper;
import com.company.hrms.attendance.mapper.OvertimeLedgerMapper;
import com.company.hrms.attendance.mapper.WorkdayConfigMapper;
import com.company.hrms.common.approval.ApprovalEngineService;
import com.company.hrms.common.approval.CreateApprovalRequest;
import com.company.hrms.common.approval.CreateApprovalResult;
import com.company.hrms.common.exception.BusinessException;
import com.company.hrms.common.exception.ErrorCode;
import com.company.hrms.common.web.PageParam;
import com.company.hrms.common.web.PageResult;
import com.company.hrms.module.attendance.dto.OvertimeApplicationDTO;
import com.company.hrms.module.attendance.dto.OvertimeApplicationVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 加班管理 Service
 *
 * 涵盖三大块：
 * 1. 加班申请（提交、列表分页）
 * 2. 加班倍率计算（工作日 1.5 / 休息日 2.0 / 法定节假日 3.0）
 * 3. 加班台账查询（审批通过后生成，供薪资核算使用）
 *
 * 加班审批通过后由 ApprovalEventListener 自动创建 OvertimeLedger 台账，
 * 并折算为调休余额（COMP_OFF）存入 leave_balance 表。
 * 加班 >=4 小时触发 HR 二审流程（needsSecondReview）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OvertimeService {

    private final OvertimeApplicationMapper overtimeApplicationMapper;
    private final OvertimeLedgerMapper overtimeLedgerMapper;
    private final WorkdayConfigMapper workdayConfigMapper;
    private final HolidayCalendarMapper holidayCalendarMapper;
    private final ApprovalEngineService approvalEngineService;
    private final com.company.hrms.employee.mapper.EmployeeMapper employeeMapper;

    /**
     * 加班列表（分页）
     *
     * 管理端查看所有员工加班记录，门户端查看本人记录。
     * 自动关联员工姓名和部门名称用于展示。
     *
     * @param pageParam  分页参数
     * @param employeeId 员工 ID（null=查全部）
     * @return 分页结果
     */
    public PageResult<OvertimeApplicationVO> pageApplications(PageParam pageParam, Long employeeId) {
        return pageApplications(pageParam, employeeId, null);
    }

    public PageResult<OvertimeApplicationVO> pageApplications(PageParam pageParam, Long employeeId, String status) {
        LambdaQueryWrapper<OvertimeApplication> wrapper = new LambdaQueryWrapper<OvertimeApplication>()
                .orderByDesc(OvertimeApplication::getCreatedAt);
        if (employeeId != null) {
            wrapper.eq(OvertimeApplication::getEmployeeId, employeeId);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(OvertimeApplication::getStatus, status);
        }

        IPage<OvertimeApplication> page = overtimeApplicationMapper.selectPage(
                new Page<>(pageParam.getPage(), pageParam.getPageSize()), wrapper);

        // 收集所有员工 ID，用 search() 批量查询（含部门 JOIN）
        // 批量查询避免 N+1 问题
        java.util.Set<Long> empIds = page.getRecords().stream()
                .map(OvertimeApplication::getEmployeeId)
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

        List<OvertimeApplicationVO> voList = new ArrayList<>();
        for (OvertimeApplication oa : page.getRecords()) {
            OvertimeApplicationVO vo = new OvertimeApplicationVO();
            vo.setId(oa.getId());
            vo.setEmployeeId(oa.getEmployeeId());
            vo.setOvertimeDate(oa.getOvertimeDate());
            vo.setHours(oa.getHours());
            vo.setStatus(oa.getStatus());
            vo.setReason(oa.getReason());
            vo.setInstanceId(oa.getInstanceId());
            vo.setStartTime(oa.getStartTime() != null
                    ? oa.getStartTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) : null);
            vo.setEndTime(oa.getEndTime() != null
                    ? oa.getEndTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) : null);
            vo.setEmployeeName(nameMap.getOrDefault(oa.getEmployeeId(), String.valueOf(oa.getEmployeeId())));
            vo.setDepartment(deptMap.get(oa.getEmployeeId()));
            voList.add(vo);
        }
        return PageResult.of(voList, page.getTotal(), pageParam);
    }

    /**
     * 提交加班申请
     *
     * 流程：
     *   1. 校验加班日期不能是过去日期
     *   2. 校验结束时间须晚于开始时间
     *   3. 计算加班时长（分钟→小时，保留 2 位小数）
     *   4. 计算加班倍率类型（工作日/休息日/节假日）
     *   5. 判断是否触发二审（>=4 小时需额外审批节点）
     *   6. 插入申请记录
     *   7. 触发审批流程
     *
     * 加班时长的倍数判定由 @see #calculateRateType(LocalDate, String, String) 实现，
     * 最终加班费计算在薪资核算模块中进行。
     *
     * @param employeeId 员工 ID
     * @param dto        加班申请参数
     * @return 创建的加班申请实体
     */
    @Transactional(rollbackFor = Exception.class)
    public OvertimeApplication submit(Long employeeId, OvertimeApplicationDTO dto) {
        // 校验：加班日期不能是过去日期（PRD 要求：加班需提前申请）
        LocalDate overtimeDate = LocalDate.parse(dto.getOvertimeDate());
        if (overtimeDate.isBefore(LocalDate.now())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "加班日期不能是过去日期，请选择今天或未来的日期");
        }

        // 解析起止时间（HH:mm 格式）
        LocalTime startTime = LocalTime.parse(dto.getStartTime(), DateTimeFormatter.ofPattern("HH:mm"));
        LocalTime endTime = LocalTime.parse(dto.getEndTime(), DateTimeFormatter.ofPattern("HH:mm"));
        if (!endTime.isAfter(startTime)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "结束时间须晚于开始时间");
        }

        // 计算时长（小时）：分钟差值 / 60，保留 2 位小数
        BigDecimal hours = BigDecimal.valueOf(java.time.Duration.between(startTime, endTime).toMinutes())
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);

        // 计算倍率：工作日 1.5 / 休息日 2.0 / 法定节假日 3.0
        int rateType = calculateRateType(overtimeDate, dto.getStartTime(), dto.getEndTime());

        // 是否触发二审（>=4 小时需 HR 二审确认）
        boolean needsSecondReview = hours.compareTo(BigDecimal.valueOf(4)) >= 0;

        OvertimeApplication app = new OvertimeApplication();
        app.setEmployeeId(employeeId);
        app.setOvertimeDate(dto.getOvertimeDate());
        app.setStartTime(LocalDateTime.of(overtimeDate, startTime));
        app.setEndTime(LocalDateTime.of(overtimeDate, endTime));
        app.setHours(hours);
        app.setReason(dto.getReason());
        app.setStatus("PENDING");
        overtimeApplicationMapper.insert(app);

        // 创建审批实例，二审标记通过 formData 传递给审批引擎
        CreateApprovalRequest req = new CreateApprovalRequest();
        req.setProcessType("OVERTIME");
        req.setBusinessId(app.getId());
        req.setApplicantId(com.company.hrms.common.security.SecurityUtils.getCurrentUser().getUserId());
        req.setTitle("加班申请#" + app.getId());
        req.setBusinessSummary(dto.getOvertimeDate() + " " + hours + "h");
        req.setBusinessNo("OT-" + app.getId());
        Map<String, Object> form = new HashMap<>();
        form.put("dailyTotalHours", hours);
        form.put("needsSecondReview", needsSecondReview);
        form.put("employeeId", employeeId);
        req.setFormData(form);
        CreateApprovalResult result = approvalEngineService.createInstance(req);
        app.setInstanceId(result.getInstanceId());
        overtimeApplicationMapper.updateById(app);

        if (needsSecondReview) {
            log.info("加班申请触发二审: id={}, hours={}, rateType={}", app.getId(), hours, rateType);
        }
        log.info("提交加班: empId={}, date={}, hours={}, rateType={}, instanceId={}",
                employeeId, dto.getOvertimeDate(), hours, rateType, result.getInstanceId());

        return app;
    }

    /**
     * 加班倍率计算
     *
     * 判定优先级：
     *   1. 法定节假日 → 3.0 倍（rateType=30）
     *   2. 工作日     → 1.5 倍（rateType=15）
     *   3. 休息日     → 2.0 倍（rateType=20）
     *
     * 工作日/休息日判定依据 WorkdayConfig 配置表（支持调休上班等特殊场景），
     * 而非简单按周几判断。法定节假日从 HolidayCalendar 表读取。
     *
     * @param overtimeDate 加班日期
     * @param startTime    开始时间（HH:mm，未使用，保留参数兼容性）
     * @param endTime      结束时间（HH:mm，未使用，保留参数兼容性）
     * @return 倍率类型：15=1.5倍, 20=2.0倍, 30=3.0倍
     */
    private int calculateRateType(LocalDate overtimeDate, String startTime, String endTime) {
        DayOfWeek dow = overtimeDate.getDayOfWeek();

        // 查询工作日配置
        List<WorkdayConfig> configs = workdayConfigMapper.selectList(null);
        Set<Integer> workdaySet = configs.stream()
                .filter(w -> w.getIsWorkday() == 1)
                .map(WorkdayConfig::getDayOfWeek)
                .collect(Collectors.toSet());

        // 查询法定节假日
        List<HolidayCalendar> holidays = holidayCalendarMapper.selectList(null);
        Set<LocalDate> holidayDates = holidays.stream()
                .map(HolidayCalendar::getHolidayDate)
                .collect(Collectors.toSet());

        // 法定节假日 → 3.0 倍（最高优先级）
        if (holidayDates.contains(overtimeDate)) {
            return 30;
        }

        // 工作日 → 1.5 倍
        if (workdaySet.contains(dow.getValue())) {
            return 15;
        }

        // 休息日 → 2.0 倍
        return 20;
    }

    // ========================================================================
    //  加班台账查询
    // ========================================================================

    /**
     * 加班台账分页查询
     *
     * 台账由 ApprovalEventListener 在加班审批通过后自动生成。
     * 台账记录包含：员工、账期、总时长、倍率类型，供薪资核算模块（CalculateService）使用。
     * 薪资核算时按 rateType 分组计算加班费（1.5倍/2.0倍/3.0倍）。
     *
     * @param pageParam 分页参数
     * @param period    账期（格式 YYYY-MM）
     * @return 分页后的台账列表
     */
    public PageResult<com.company.hrms.module.attendance.dto.OvertimeLedgerVO> pageLedger(
            PageParam pageParam, String period) {
        List<OvertimeLedger> allRecords = overtimeLedgerMapper.selectByPeriod(period);

        // 查询员工姓名和部门（台账只存 employee_id，需关联 employee 表）
        Map<Long, String> nameMap = new HashMap<>();
        Map<Long, String> deptMap = new HashMap<>();
        for (OvertimeLedger l : allRecords) {
            Long empId = l.getEmployeeId();
            if (!nameMap.containsKey(empId)) {
                try {
                    com.company.hrms.employee.entity.Employee emp = employeeMapper.selectById(empId);
                    if (emp != null) {
                        nameMap.put(empId, emp.getName());
                        deptMap.put(empId, emp.getDepartmentName());
                    } else {
                        nameMap.put(empId, String.valueOf(empId));
                    }
                } catch (Exception e) {
                    nameMap.put(empId, String.valueOf(empId));
                }
            }
        }

        List<com.company.hrms.module.attendance.dto.OvertimeLedgerVO> voList = allRecords.stream()
                .map(l -> {
                    com.company.hrms.module.attendance.dto.OvertimeLedgerVO vo =
                            new com.company.hrms.module.attendance.dto.OvertimeLedgerVO();
                    vo.setId(l.getId());
                    vo.setEmployeeId(l.getEmployeeId());
                    vo.setEmployeeName(nameMap.getOrDefault(l.getEmployeeId(), String.valueOf(l.getEmployeeId())));
                    vo.setDepartmentName(deptMap.get(l.getEmployeeId()));
                    vo.setPeriod(l.getPeriod());
                    vo.setTotalHours(l.getTotalHours());
                    vo.setRateType(l.getRateType());
                    vo.setLedgerDate(l.getLedgerDate() != null ? l.getLedgerDate().toString() : null);
                    vo.setCreatedAt(l.getCreatedAt() != null
                            ? l.getCreatedAt().toString().replace("T", " ") : null);
                    return vo;
                })
                .collect(Collectors.toList());

        // 手动分页（台账数据量通常不大，直接全量查询后截取）
        int page = Math.max(pageParam.getPage(), 1);
        int pageSize = pageParam.getPageSize() > 0 ? pageParam.getPageSize() : 20;
        int from = (page - 1) * pageSize;
        int to = Math.min(from + pageSize, voList.size());
        List<com.company.hrms.module.attendance.dto.OvertimeLedgerVO> paged = from >= voList.size()
                ? Collections.emptyList()
                : voList.subList(from, to);

        return PageResult.of(paged, voList.size(), pageParam);
    }
}
