package com.company.hrms.module.attendance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.hrms.attendance.entity.AttendanceGroup;
import com.company.hrms.attendance.entity.AttendanceGroupMember;
import com.company.hrms.attendance.entity.AttendanceMonthLock;
import com.company.hrms.attendance.entity.AttendanceRecord;
import com.company.hrms.attendance.entity.AttendanceSupplement;
import com.company.hrms.attendance.mapper.AttendanceGroupMapper;
import com.company.hrms.attendance.mapper.AttendanceGroupMemberMapper;
import com.company.hrms.attendance.mapper.AttendanceMonthLockMapper;
import com.company.hrms.attendance.mapper.AttendanceRecordMapper;
import com.company.hrms.attendance.mapper.AttendanceSupplementMapper;
import com.company.hrms.common.approval.ApprovalEngineService;
import com.company.hrms.common.approval.CreateApprovalRequest;
import com.company.hrms.common.approval.CreateApprovalResult;
import com.company.hrms.common.exception.BusinessException;
import com.company.hrms.common.exception.ErrorCode;
import com.company.hrms.common.web.PageParam;
import com.company.hrms.common.web.PageResult;
import com.company.hrms.module.attendance.dto.PunchDTO;
import com.company.hrms.module.attendance.dto.PunchFixDTO;
import com.company.hrms.module.attendance.dto.PunchRecordVO;
import com.company.hrms.module.attendance.dto.QuotaVO;
import com.company.hrms.module.attendance.dto.TodayPunchVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 打卡管理 Service
 * 涵盖打卡判定、Redis 幂等、GPS 校验、补卡申请与配额管理
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PunchService {

    private final AttendanceRecordMapper attendanceRecordMapper;
    private final AttendanceGroupMapper attendanceGroupMapper;
    private final AttendanceGroupMemberMapper attendanceGroupMemberMapper;
    private final AttendanceSupplementMapper attendanceSupplementMapper;
    private final AttendanceMonthLockMapper attendanceMonthLockMapper;
    private final com.company.hrms.attendance.mapper.AttendanceMonthlySummaryMapper monthlySummaryMapper;
    private final com.company.hrms.attendance.mapper.WorkdayConfigMapper workdayConfigMapper;
    private final com.company.hrms.attendance.mapper.HolidayCalendarMapper holidayCalendarMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final ApprovalEngineService approvalEngineService;
    private final com.company.hrms.employee.mapper.EmployeeMapper employeeMapper;

    /** 时区偏移 +08:00（北京时间） */
    private static final java.time.ZoneId CST = java.time.ZoneId.of("Asia/Shanghai");
    /** Redis key 前缀：打卡幂等 */
    private static final String PUNCH_IDEMP_KEY = "hrms:punch:";
    /** Redis key 前缀：补卡计数 */
    private static final String SUPPLEMENT_KEY = "supplement:";
    /** 补卡月配额 */
    private static final int MAX_SUPPLEMENT_QUOTA = 2;

    // ========== 打卡核心 ==========

    /**
     * 员工打卡
     *
     * @param employeeId 员工 ID
     * @param dto        打卡请求
     * @return 打卡状态: NORMAL / LATE / EARLY_LEAVE / ABSENT_HALF
     */
    @Transactional(rollbackFor = Exception.class)
    public String punch(Long employeeId, PunchDTO dto) {
        // 判定和幂等键始终以服务器 CST 时间为准
        LocalDate serverDate = LocalDate.now(CST);
        LocalTime serverTime = LocalDateTime.now(CST).toLocalTime();
        String type = dto.getType() != null ? dto.getType().toUpperCase() : "IN";

        // Redis 幂等校验（基于服务端日期，不受前端时间影响）
        String idempKey = PUNCH_IDEMP_KEY + employeeId + ":" + serverDate.toString() + ":" + type;
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(idempKey, "1", getSecondsUntilEndOfDay(serverDate), TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(success)) {
            throw new BusinessException(ErrorCode.PUNCH_DUPLICATE, "您已打卡，请勿重复操作");
        }

        // 存储时间优先用前端传的值（保留给用户看的原始时间），否则用服务端时间
        LocalDateTime storeTime = LocalDateTime.now(CST);
        LocalDate storeDate = serverDate;
        if (dto.getPunchTime() != null) {
            try {
                storeTime = LocalDateTime.parse(dto.getPunchTime(), DateTimeFormatter.ISO_DATE_TIME);
                storeDate = storeTime.toLocalDate();
            } catch (DateTimeParseException e) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "打卡时间格式错误");
            }
        }

        try {
            // 2. 查询员工所属考勤组
            AttendanceGroup group = getEmployeeGroup(employeeId);

            // 3. GPS 校验
            if (group != null && group.getGpsRangeJson() != null && dto.getLatitude() != null && dto.getLongitude() != null) {
                validateGps(group.getGpsRangeJson(), dto.getLatitude(), dto.getLongitude());
            }

            // 3b. IP 白名单校验
            if (group != null && group.getIpWhitelistJson() != null && dto.getClientIp() != null) {
                validateIpWhitelist(group.getIpWhitelistJson(), dto.getClientIp());
            }

            // 4. 判定打卡状态（用服务端 CST 时间，确保与考勤组工作时间比较正确）
            String punchStatus = judgePunchStatus(group, serverTime, type);

            // 5. 写入打卡记录（存储时间用 storeTime/storeDate，保留前端传入值）
            AttendanceRecord record = new AttendanceRecord();
            record.setEmployeeId(employeeId);
            record.setPunchDate(storeDate);
            record.setPunchTime(storeTime);
            record.setPunchType(type);
            record.setPunchStatus(punchStatus);
            record.setSource("WEB");

            // GPS 信息
            if (dto.getLatitude() != null && dto.getLongitude() != null) {
                Map<String, Object> gps = new HashMap<>();
                gps.put("lat", dto.getLatitude());
                gps.put("lng", dto.getLongitude());
                try {
                    record.setGpsJson(objectMapper.writeValueAsString(gps));
                } catch (JsonProcessingException e) {
                    log.warn("GPS 序列化失败", e);
                }
            }

            attendanceRecordMapper.insert(record);
            log.info("员工打卡: empId={}, type={}, status={}, serverTime={}, storeTime={}",
                    employeeId, type, punchStatus, serverTime, storeTime);
            return punchStatus;

        } catch (Exception e) {
            // 打卡失败时删除幂等键（允许重试）
            stringRedisTemplate.delete(idempKey);
            throw e;
        }
    }

    // ========== 今日状态 ==========

    /**
     * 获取今日打卡状态（含记录明细）
     */
    public TodayPunchVO getTodayStatus(Long employeeId) {
        LocalDate today = LocalDate.now(CST);

        // 查今日所有打卡记录
        List<AttendanceRecord> records = attendanceRecordMapper.selectByEmployeeAndDate(employeeId, today);

        long totalCount = 2; // 每日应打 2 次（IN + OUT）
        long clockedCount = records.size();
        long lateCount = records.stream().filter(r -> "LATE".equals(r.getPunchStatus())).count();
        long earlyLeaveCount = records.stream().filter(r -> "EARLY_LEAVE".equals(r.getPunchStatus())).count();
        long absentCount = records.stream().filter(r -> "ABSENT_HALF".equals(r.getPunchStatus())).count();

        TodayPunchVO vo = new TodayPunchVO(clockedCount, totalCount, lateCount, earlyLeaveCount, absentCount);

        // 填充今日打卡记录明细（去重：每种类型取最新一条）
        java.util.Map<String, AttendanceRecord> latest = new java.util.HashMap<>();
        for (AttendanceRecord r : records) {
            latest.put(r.getPunchType(), r);
        }
        java.util.List<TodayPunchVO.PunchRecordItem> items = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String, AttendanceRecord> entry : latest.entrySet()) {
            AttendanceRecord r = entry.getValue();
            TodayPunchVO.PunchRecordItem item = new TodayPunchVO.PunchRecordItem();
            item.setType(r.getPunchType());
            item.setTime(r.getPunchTime() != null
                    ? r.getPunchTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) : null);
            item.setStatus(r.getPunchStatus());
            items.add(item);
        }
        // 按类型排序：IN 在前
        items.sort(java.util.Comparator.comparing(i -> "IN".equals(i.getType()) ? 0 : 1));
        vo.setRecords(items);

        return vo;
    }

    // ========== 本月打卡统计 ==========

    /**
     * 获取本月打卡统计
     */
    public TodayPunchVO getMonthlyStatus(Long employeeId) {
        LocalDate today = LocalDate.now(CST);
        String period = today.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
        LocalDate monthStart = today.withDayOfMonth(1);

        // 查本月所有打卡记录
        List<AttendanceRecord> allRecords = attendanceRecordMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AttendanceRecord>()
                        .eq(AttendanceRecord::getEmployeeId, employeeId)
                        .ge(AttendanceRecord::getPunchDate, monthStart)
                        .le(AttendanceRecord::getPunchDate, today));

        // 计算本月应打卡天数（工作日 × 2）
        List<com.company.hrms.attendance.entity.WorkdayConfig> workdayConfigs = workdayConfigMapper.selectList(null);
        java.util.Set<Integer> workdaySet = workdayConfigs.stream()
                .filter(w -> w.getIsWorkday() == 1)
                .map(com.company.hrms.attendance.entity.WorkdayConfig::getDayOfWeek)
                .collect(java.util.stream.Collectors.toSet());
        List<com.company.hrms.attendance.entity.HolidayCalendar> holidays = holidayCalendarMapper.selectList(null);
        java.util.Set<java.time.LocalDate> holidayDates = holidays.stream()
                .map(com.company.hrms.attendance.entity.HolidayCalendar::getHolidayDate)
                .collect(java.util.stream.Collectors.toSet());

        int shouldDays = 0;
        LocalDate current = monthStart;
        while (!current.isAfter(today)) {
            java.time.DayOfWeek dow = current.getDayOfWeek();
            int dowVal = dow.getValue();
            if (workdaySet.contains(dowVal) && !holidayDates.contains(current)) {
                shouldDays++;
            }
            current = current.plusDays(1);
        }
        long totalCount = shouldDays * 2L; // 每天 IN + OUT

        // 统计本月数据
        long clockedCount = allRecords.size();
        long lateCount = allRecords.stream().filter(r -> "LATE".equals(r.getPunchStatus())).count();
        long earlyLeaveCount = allRecords.stream().filter(r -> "EARLY_LEAVE".equals(r.getPunchStatus())).count();
        long absentCount = allRecords.stream().filter(r -> "ABSENT_HALF".equals(r.getPunchStatus())).count();

        return new TodayPunchVO(clockedCount, totalCount, lateCount, earlyLeaveCount, absentCount);
    }

    // ========== 打卡记录分页 ==========

    /**
     * 打卡记录分页查询
     *
     * @param pageParam 分页参数
     * @param keyword   搜索关键字（员工姓名/工号）
     * @param dateFrom  开始日期
     * @param dateTo    结束日期
     * @return 分页打卡记录
     */
    public PageResult<PunchRecordVO> pageRecords(PageParam pageParam, String keyword,
                                                  String dateFrom, String dateTo) {
        // 由于需要跨表关联，使用 MyBatis-Plus 查询后组装
        // 查询打卡记录 + 按 employee_id + punch_date 分组
        LambdaQueryWrapper<AttendanceRecord> wrapper = new LambdaQueryWrapper<AttendanceRecord>()
                .orderByDesc(AttendanceRecord::getPunchDate)
                .orderByAsc(AttendanceRecord::getEmployeeId);

        if (dateFrom != null) {
            wrapper.ge(AttendanceRecord::getPunchDate, LocalDate.parse(dateFrom));
        }
        if (dateTo != null) {
            wrapper.le(AttendanceRecord::getPunchDate, LocalDate.parse(dateTo));
        }

        // 先查所有匹配记录（不分页），分组合并后再手动分页
        List<AttendanceRecord> allRecords = attendanceRecordMapper.selectList(wrapper);

        // 按 employeeId + punchDate 分组，合并 IN/OUT（LinkedHashMap 保留 SQL 排序）
        Map<String, List<AttendanceRecord>> grouped = allRecords.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getEmployeeId() + "_" + r.getPunchDate(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<PunchRecordVO> allVoList = new ArrayList<>();
        for (Map.Entry<String, List<AttendanceRecord>> entry : grouped.entrySet()) {
            List<AttendanceRecord> recs = entry.getValue();

            PunchRecordVO vo = new PunchRecordVO();
            AttendanceRecord first = recs.get(0);
            vo.setEmployeeId(first.getEmployeeId());
            vo.setPunchDate(first.getPunchDate() != null ? first.getPunchDate().toString() : null);
            vo.setSource(first.getSource());
            vo.setClientIp(first.getClientIp());
            vo.setGpsJson(first.getGpsJson());

            // 从 employee 表查询员工姓名和部门
            try {
                com.company.hrms.employee.entity.Employee emp = employeeMapper.selectById(first.getEmployeeId());
                if (emp != null) {
                    vo.setEmployeeName(emp.getName());
                    vo.setDepartmentName(emp.getDepartmentName());
                } else {
                    vo.setEmployeeName(String.valueOf(first.getEmployeeId()));
                    vo.setDepartmentName("");
                }
            } catch (Exception e) {
                log.warn("查询员工信息失败: employeeId={}", first.getEmployeeId(), e);
                vo.setEmployeeName(String.valueOf(first.getEmployeeId()));
                vo.setDepartmentName("");
            }

            for (AttendanceRecord r : recs) {
                String timeStr = null;
                if (r.getPunchTime() != null) {
                    // 数据库已存 CST 时间，直接格式化
                    timeStr = r.getPunchTime()
                            .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
                }
                if ("IN".equals(r.getPunchType())) {
                    vo.setClockInTime(timeStr);
                    vo.setClockInStatus(r.getPunchStatus());
                } else if ("OUT".equals(r.getPunchType())) {
                    vo.setClockOutTime(timeStr);
                    vo.setClockOutStatus(r.getPunchStatus());
                }
            }

            // 关键字过滤（简单实现，完整需员工服务）
            if (keyword != null && !keyword.isEmpty()) {
                if (!vo.getEmployeeName().contains(keyword)) {
                    continue;
                }
            }

            allVoList.add(vo);
        }

        // 手动分页：按 page 和 pageSize 截取
        int page = Math.max(pageParam.getPage(), 1);
        int pageSize = pageParam.getPageSize() > 0 ? pageParam.getPageSize() : 20;
        int from = (page - 1) * pageSize;
        int to = Math.min(from + pageSize, allVoList.size());
        List<PunchRecordVO> voList = from >= allVoList.size()
                ? Collections.emptyList()
                : allVoList.subList(from, to);

        return PageResult.of(voList, allVoList.size(), pageParam);
    }

    // ========== 补卡管理 ==========

    /**
     * 补卡申请
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> applyFix(Long employeeId, PunchFixDTO dto) {
        LocalDate fixDate = LocalDate.parse(dto.getPunchDate());
        String ym = fixDate.format(DateTimeFormatter.ofPattern("yyyy-MM"));

        // 1. 校验月锁定
        AttendanceMonthLock lock = attendanceMonthLockMapper.selectOne(
                new LambdaQueryWrapper<AttendanceMonthLock>()
                        .eq(AttendanceMonthLock::getYearMonth, ym));
        if (lock != null && lock.getStatus() == 20) {
            throw new BusinessException(ErrorCode.ATTENDANCE_MONTH_LOCKED, "考勤月已锁定，请联系 HR 解锁");
        }

        // 2. 校验补卡配额（Redis 不可用时回落 DB）
        int usedQuota = resolveUsedQuota(employeeId, ym, fixDate);

        if (usedQuota >= MAX_SUPPLEMENT_QUOTA) {
            throw new BusinessException(ErrorCode.MAKEUP_LIMIT_EXCEEDED, "每月最多补卡 " + MAX_SUPPLEMENT_QUOTA + " 次");
        }

        // 3. 补卡类型转换
        String punchType = dto.getType() != null ? dto.getType().toUpperCase() : "IN";

        // 4. 解析补卡时间（兼容 HH:mm 和 ISO 格式）
        String punchTimeStr = dto.getPunchTime();
        LocalDateTime makeupTime;
        try {
            makeupTime = LocalDateTime.parse(punchTimeStr, DateTimeFormatter.ISO_DATE_TIME);
        } catch (DateTimeParseException e) {
            // 前端可能只传 HH:mm，拼上 punchDate 转成 LocalDateTime
            LocalTime time = LocalTime.parse(punchTimeStr, DateTimeFormatter.ofPattern("HH:mm"));
            makeupTime = LocalDateTime.of(LocalDate.parse(dto.getPunchDate()), time);
        }

        // 5. 插入补卡申请
        AttendanceSupplement supplement = new AttendanceSupplement();
        supplement.setEmployeeId(employeeId);
        supplement.setMakeupDate(dto.getPunchDate());
        supplement.setPunchType(punchType);
        supplement.setMakeupTime(makeupTime);
        supplement.setReason(dto.getReason());
        supplement.setStatus("PENDING");
        attendanceSupplementMapper.insert(supplement);

        CreateApprovalRequest req = new CreateApprovalRequest();
        req.setProcessType("MAKEUP");
        req.setBusinessId(supplement.getId());
        req.setApplicantId(com.company.hrms.common.security.SecurityUtils.getCurrentUser().getUserId());
        req.setTitle("补卡申请#" + supplement.getId());
        req.setBusinessSummary(dto.getPunchDate() + " " + punchType);
        req.setBusinessNo("MAKEUP-" + supplement.getId());
        java.util.Map<String, Object> form = new java.util.HashMap<>();
        form.put("employeeId", employeeId);
        req.setFormData(form);
        CreateApprovalResult approval = approvalEngineService.createInstance(req);
        supplement.setInstanceId(approval.getInstanceId());
        attendanceSupplementMapper.updateById(supplement);

        // 5. Redis 原子自增（失败仅记日志，以 DB 计数为准）
        String quotaKey = SUPPLEMENT_KEY + employeeId + ":" + ym;
        try {
            stringRedisTemplate.opsForValue().increment(quotaKey);
            stringRedisTemplate.expire(quotaKey, getSecondsUntilEndOfMonth(fixDate), TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("补卡配额 Redis 自增失败（已落库）: empId={}, err={}", employeeId, e.getMessage());
        }

        log.info("补卡申请: empId={}, date={}, type={}, id={}, instanceId={}",
                employeeId, dto.getPunchDate(), punchType, supplement.getId(), approval.getInstanceId());

        Map<String, Object> result = new HashMap<>();
        result.put("id", supplement.getId());
        result.put("status", "PENDING");
        result.put("instanceId", approval.getInstanceId());
        return result;
    }

    /**
     * 补卡剩余次数
     */
    public QuotaVO getFixQuota(Long employeeId) {
        if (employeeId == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "当前账号未关联员工档案");
        }
        String ym = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        int usedQuota = resolveUsedQuota(employeeId, ym, LocalDate.now());
        return new QuotaVO(MAX_SUPPLEMENT_QUOTA, usedQuota, Math.max(0, MAX_SUPPLEMENT_QUOTA - usedQuota));
    }

    /**
     * 读配额：Redis 优先，失败或未命中则回落 DB，避免 Redis 不可用直接 90001。
     */
    private int resolveUsedQuota(Long employeeId, String ym, LocalDate refDate) {
        String quotaKey = SUPPLEMENT_KEY + employeeId + ":" + ym;
        try {
            String usedStr = stringRedisTemplate.opsForValue().get(quotaKey);
            if (usedStr != null) {
                return Integer.parseInt(usedStr);
            }
            int usedQuota = attendanceSupplementMapper.countByEmployeeAndMonth(employeeId, ym);
            stringRedisTemplate.opsForValue().set(quotaKey, String.valueOf(usedQuota),
                    getSecondsUntilEndOfMonth(refDate), TimeUnit.SECONDS);
            return usedQuota;
        } catch (Exception e) {
            log.warn("补卡配额 Redis 不可用，回落 DB: empId={}, ym={}, err={}", employeeId, ym, e.getMessage());
            return attendanceSupplementMapper.countByEmployeeAndMonth(employeeId, ym);
        }
    }

    // ========== 私有方法 ==========

    /**
     * 打卡判定逻辑
     *
     * 上班判定：
     *   punchTime ≤ onDuty             → NORMAL
     *   punchTime ≤ onDuty+lateThreshold → LATE
     *   else                            → ABSENT_HALF
     *
     * 下班判定：
     *   punchTime ≥ offDuty                      → NORMAL
     *   punchTime ≥ offDuty-earlyLeaveThreshold  → EARLY_LEAVE
     *   else                                      → ABSENT_HALF
     */
    private String judgePunchStatus(AttendanceGroup group, LocalTime punchTime, String type) {
        if (group == null || group.getWorkStartTime() == null || group.getWorkEndTime() == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "您未分配考勤组，请联系HR配置后再打卡");
        }

        // 根据班次类型分发
        String shiftType = group.getShiftType();
        if ("FLEXIBLE".equals(shiftType)) {
            return judgeFlexiblePunch(group, punchTime, type);
        }

        // SCHEDULE（排班制）暂等同于 FIXED 处理
        // FIXED：固定班次判定逻辑
        return judgeFixedPunch(group, punchTime, type);
    }

    /**
     * 弹性班次（FLEXIBLE）打卡判定
     * 上班：在弹性范围内打卡即 NORMAL，早于 earliest 或晚于 latest 标记异常
     * 下班：沿用固定班次的下班判定逻辑
     */
    private String judgeFlexiblePunch(AttendanceGroup group, LocalTime punchTime, String type) {
        if ("IN".equals(type)) {
            LocalTime earliest = group.getFlexStartEarliest();
            LocalTime latest = group.getFlexStartLatest();
            if (earliest != null && latest != null) {
                // 在弹性范围内打卡即 NORMAL
                if (!punchTime.isBefore(earliest) && !punchTime.isAfter(latest)) {
                    return "NORMAL";
                }
                // 早于 earliest 或晚于 latest 标记异常
                return "LATE";
            }
            // 未配置弹性范围，按固定班次逻辑
            return judgeFixedPunch(group, punchTime, type);
        } else if ("OUT".equals(type)) {
            // 下班沿用固定班次逻辑
            return judgeFixedPunch(group, punchTime, type);
        }
        return "NORMAL";
    }

    /**
     * 固定班次（FIXED）打卡判定
     * punchTime 已是 CST（调用前已转换），直接与考勤组时间比较
     * 上班：准时或早到 NORMAL，迟到阈值内 LATE，超过 ABSENT_HALF
     * 下班：准时或加班 NORMAL，早退阈值内 EARLY_LEAVE，超过 ABSENT_HALF
     */
    private String judgeFixedPunch(AttendanceGroup group, LocalTime punchTime, String type) {
        if ("IN".equals(type)) {
            LocalTime onDuty = group.getWorkStartTime();
            int lateThreshold = group.getLateThresholdMinutes() != null ? group.getLateThresholdMinutes() : 15;

            if (!punchTime.isAfter(onDuty)) {
                return "NORMAL";
            }
            if (!punchTime.isAfter(onDuty.plusMinutes(lateThreshold))) {
                return "LATE";
            }
            return "ABSENT_HALF";

        } else if ("OUT".equals(type)) {
            LocalTime offDuty = group.getWorkEndTime();
            int earlyThreshold = group.getEarlyLeaveThresholdMinutes() != null ? group.getEarlyLeaveThresholdMinutes() : 15;

            if (!punchTime.isBefore(offDuty)) {
                return "NORMAL";
            }
            if (!punchTime.isBefore(offDuty.minusMinutes(earlyThreshold))) {
                return "EARLY_LEAVE";
            }
            return "ABSENT_HALF";
        }

        return "NORMAL";
    }

    /**
     * 查询员工所属考勤组
     */
    private AttendanceGroup getEmployeeGroup(Long employeeId) {
        AttendanceGroupMember member = attendanceGroupMemberMapper.selectById(employeeId);
        if (member == null) {
            return null;
        }
        return attendanceGroupMapper.selectById(member.getGroupId());
    }

    /**
     * GPS 距离校验（Haversine 公式）
     */
    private void validateGps(String gpsRangeJson, Double latitude, Double longitude) {
        try {
            Map<String, Object> range = objectMapper.readValue(gpsRangeJson, Map.class);
            double centerLat = ((Number) range.get("lat")).doubleValue();
            double centerLng = ((Number) range.get("lng")).doubleValue();
            int radiusM = ((Number) range.get("radiusM")).intValue();

            double distance = haversine(centerLat, centerLng, latitude, longitude);
            if (distance > radiusM) {
                throw new BusinessException(ErrorCode.PUNCH_OUT_OF_RANGE,
                        "不在打卡有效范围（距离 " + (int) distance + "m，限制 " + radiusM + "m）");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("GPS 校验解析失败: {}", gpsRangeJson, e);
        }
    }

    /**
     * IP 白名单校验（支持精确 IP 和 CIDR 网段）
     */
    private void validateIpWhitelist(String whitelistJson, String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            throw new BusinessException(ErrorCode.PUNCH_OUT_OF_RANGE, "无法获取客户端 IP");
        }
        try {
            java.util.List<String> whitelist = objectMapper.readValue(whitelistJson,
                    new com.fasterxml.jackson.core.type.TypeReference<java.util.List<String>>() {});
            if (whitelist == null || whitelist.isEmpty()) return;
            boolean matched = false;
            for (String rule : whitelist) {
                if (rule == null || rule.isBlank()) continue;
                if (rule.contains("/")) {
                    String[] parts = rule.split("/");
                    if (isIpInCidr(clientIp, parts[0], Integer.parseInt(parts[1]))) {
                        matched = true; break;
                    }
                } else if (rule.equals(clientIp)) {
                    matched = true; break;
                }
            }
            if (!matched) {
                throw new BusinessException(ErrorCode.PUNCH_OUT_OF_RANGE,
                        "不在打卡有效范围（IP " + clientIp + " 不在白名单）");
            }
        } catch (BusinessException e) { throw e;
        } catch (Exception e) { log.warn("IP 白名单校验失败: {}", whitelistJson, e); }
    }

    private boolean isIpInCidr(String ip, String network, int prefix) {
        try {
            long ipLong = ipToLong(ip);
            long mask = prefix == 0 ? 0 : (0xFFFFFFFFL << (32 - prefix));
            return (ipLong & mask) == (ipToLong(network) & mask);
        } catch (Exception e) { return false; }
    }

    private long ipToLong(String ip) {
        String[] octets = ip.split("\\.");
        long result = 0;
        for (int i = 0; i < 4; i++) result = (result << 8) | (Integer.parseInt(octets[i]) & 0xFF);
        return result;
    }

    /**
     * Haversine 距离计算（单位：米）
     */
    private double haversine(double lat1, double lng1, double lat2, double lng2) {
        double R = 6371000; // 地球半径（米）
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    /**
     * 获取到当天结束的秒数
     */
    private long getSecondsUntilEndOfDay(LocalDate date) {
        LocalDateTime now = LocalDateTime.now(CST);
        LocalDateTime endOfDay = date.atTime(LocalTime.MAX);
        long seconds = Duration.between(now, endOfDay).getSeconds();
        return Math.max(seconds, 60); // 至少保留 60 秒，避免跨天或午夜后精度问题
    }

    /**
     * 获取到月末的秒数
     */
    private long getSecondsUntilEndOfMonth(LocalDate date) {
        LocalDateTime now = LocalDateTime.now(CST);
        LocalDate lastDay = date.withDayOfMonth(date.lengthOfMonth());
        LocalDateTime endOfMonth = lastDay.atTime(LocalTime.MAX);
        long seconds = Duration.between(now, endOfMonth).getSeconds();
        return Math.max(seconds, 60); // 至少保留 60 秒，避免跨天或午夜后精度问题
    }
}
