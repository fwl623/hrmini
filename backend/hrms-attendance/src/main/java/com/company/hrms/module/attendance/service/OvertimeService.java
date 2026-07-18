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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 加班管理 Service
 * 涵盖加班申请、倍率计算
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

    /**
     * 加班列表（分页）
     */
    public PageResult<OvertimeApplicationVO> pageApplications(PageParam pageParam, Long employeeId) {
        LambdaQueryWrapper<OvertimeApplication> wrapper = new LambdaQueryWrapper<OvertimeApplication>()
                .orderByDesc(OvertimeApplication::getCreatedAt);
        if (employeeId != null) {
            wrapper.eq(OvertimeApplication::getEmployeeId, employeeId);
        }

        IPage<OvertimeApplication> page = overtimeApplicationMapper.selectPage(
                new Page<>(pageParam.getPage(), pageParam.getPageSize()), wrapper);

        List<OvertimeApplicationVO> voList = new ArrayList<>();
        for (OvertimeApplication oa : page.getRecords()) {
            OvertimeApplicationVO vo = new OvertimeApplicationVO();
            vo.setId(oa.getId());
            vo.setEmployeeName(String.valueOf(oa.getEmployeeId())); // TODO: Feign 调用获取姓名
            vo.setOvertimeDate(oa.getOvertimeDate());
            vo.setHours(oa.getHours());
            vo.setStatus(oa.getStatus());
            voList.add(vo);
        }
        return PageResult.of(voList, page.getTotal(), pageParam);
    }

    /**
     * 提交加班申请
     */
    @Transactional(rollbackFor = Exception.class)
    public OvertimeApplication submit(Long employeeId, OvertimeApplicationDTO dto) {
        // 校验：加班日期不能是未来日期
        LocalDate overtimeDate = LocalDate.parse(dto.getOvertimeDate());
        if (overtimeDate.isAfter(LocalDate.now())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "加班日期不能是未来日期");
        }

        // 解析起止时间
        LocalTime startTime = LocalTime.parse(dto.getStartTime(), DateTimeFormatter.ofPattern("HH:mm"));
        LocalTime endTime = LocalTime.parse(dto.getEndTime(), DateTimeFormatter.ofPattern("HH:mm"));
        if (!endTime.isAfter(startTime)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "结束时间须晚于开始时间");
        }

        // 计算时长（小时）
        BigDecimal hours = BigDecimal.valueOf(java.time.Duration.between(startTime, endTime).toMinutes())
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);

        // 计算倍率
        int rateType = calculateRateType(overtimeDate, dto.getStartTime(), dto.getEndTime());
        // 是否触发二审
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

        CreateApprovalRequest req = new CreateApprovalRequest();
        req.setProcessType("OVERTIME");
        req.setBusinessId(app.getId());
        req.setApplicantId(employeeId);
        req.setTitle("加班申请#" + app.getId());
        req.setBusinessSummary(dto.getOvertimeDate() + " " + hours + "h");
        req.setBusinessNo("OT-" + app.getId());
        Map<String, Object> form = new HashMap<>();
        form.put("dailyTotalHours", hours);
        form.put("needsSecondReview", needsSecondReview);
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
     * 工作日 1.5 / 休息日 2.0 / 法定节假日 3.0
     *
     * @return rateType: 15=1.5倍, 20=2.0倍, 30=3.0倍
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

        // 法定节假日 → 3.0 倍
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
}
