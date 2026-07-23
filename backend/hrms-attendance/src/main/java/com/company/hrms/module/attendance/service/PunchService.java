package com.company.hrms.module.attendance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.hrms.attendance.entity.AttendanceGroup;
import com.company.hrms.attendance.entity.AttendanceGroupMember;
import com.company.hrms.attendance.entity.AttendanceMonthLock;
import com.company.hrms.attendance.entity.AttendanceRecord;
import com.company.hrms.attendance.entity.AttendanceDailySummary;
import com.company.hrms.attendance.entity.AttendanceSupplement;
import com.company.hrms.attendance.entity.HolidayCalendar;
import com.company.hrms.attendance.entity.WorkdayConfig;
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
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 打卡管理 Service
 *
 * 核心功能：
 * 1. 员工打卡（含 Redis 幂等、打卡状态判定）
 * 2. 今日/本月打卡状态查询
 * 3. 补卡申请（月配额 2 次，Redis + DB 双重控制）
 * 4. 打卡记录分页查询
 * 5. 日汇总实时更新（v2.1 双槽位判定）
 *
 * 打卡状态判定支持固定班次（FIXED）和弹性班次（FLEXIBLE）两种考勤模式。
 * 判定以服务器 CST 时间为准，确保与考勤组配置的时间基准一致。
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
    private final com.company.hrms.attendance.mapper.AttendanceDailySummaryMapper attendanceDailySummaryMapper;
    private final com.company.hrms.attendance.mapper.AttendanceMonthlySummaryMapper monthlySummaryMapper;
    private final com.company.hrms.attendance.mapper.WorkdayConfigMapper workdayConfigMapper;
    private final com.company.hrms.attendance.mapper.HolidayCalendarMapper holidayCalendarMapper;
    private final com.company.hrms.attendance.mapper.LeaveApplicationMapper leaveApplicationMapper;
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
    /** 补卡月配额：每人每月最多补卡 2 次 */
    private static final int MAX_SUPPLEMENT_QUOTA = 2;

    // ========================================================================
    //  打卡核心
    // ========================================================================

    /**
     * 员工打卡
     *
     * 完整流程：
     *   1. Redis 幂等校验（key 含 employeeId + serverDate + type，当天有效）
     *   2. DB 层二次防重（同员工同日期同类型只允许一条）
     *   3. 查询员工所属考勤组配置
     *   4. 判定打卡状态（judgePunchStatus，分固定班/弹性班）
     *   5. 写入打卡流水记录
     *   6. 实时更新日汇总双槽位状态
     *
     * 打卡判定的所有时间比较基于服务器 CST 时间，
     * 前端传入的 punchTime 仅作为存储值保留，不影响判定结果。
     * 打卡失败时自动删除 Redis 幂等键，允许重试。
     *
     * @param employeeId 员工 ID
     * @param dto        打卡请求（包含打卡类型、时间、GPS 信息等）
     * @return 打卡状态: NORMAL / LATE / EARLY_LEAVE / ABSENT_HALF
     */
    @Transactional(rollbackFor = Exception.class)
    public String punch(Long employeeId, PunchDTO dto) {
        // 判定和幂等键始终以服务器 CST 时间为准
        LocalDate serverDate = LocalDate.now(CST);
        LocalTime serverTime = LocalDateTime.now(CST).toLocalTime();
        String type = dto.getType() != null ? dto.getType().toUpperCase() : "IN";

        // ====== 防重校验 ======
        // 上班卡(IN)：Redis 幂等 + DB 双防重，只允许第一次
        // 下班卡(OUT)：跳过防重，允许重复打卡覆盖，以最后一次为准
        if ("IN".equals(type)) {
            // 第一层防重：Redis 幂等校验
            String idempKey = PUNCH_IDEMP_KEY + employeeId + ":" + serverDate.toString() + ":" + type;
            Boolean success = stringRedisTemplate.opsForValue()
                    .setIfAbsent(idempKey, "1", getSecondsUntilEndOfDay(serverDate), TimeUnit.SECONDS);
            if (Boolean.FALSE.equals(success)) {
                throw new BusinessException(ErrorCode.PUNCH_DUPLICATE, "您已打卡，请勿重复操作");
            }

            // 第二层防重：DB 查询（防 Redis key 误删击穿）
            boolean alreadyPunched = attendanceRecordMapper.selectByEmployeeAndDate(employeeId, serverDate)
                    .stream().anyMatch(r -> type.equals(r.getPunchType()));
            if (alreadyPunched) {
                throw new BusinessException(ErrorCode.PUNCH_DUPLICATE, "您已打卡，请勿重复操作");
            }
        } else if ("OUT".equals(type)) {
            // 下班卡可重复打卡，删除当天旧 OUT 记录，以最后一次为准
            attendanceRecordMapper.delete(
                    new LambdaQueryWrapper<AttendanceRecord>()
                            .eq(AttendanceRecord::getEmployeeId, employeeId)
                            .eq(AttendanceRecord::getPunchDate, serverDate)
                            .eq(AttendanceRecord::getPunchType, "OUT"));
        }

        // 存储时间优先用前端传的值（保留客户端感知的实际时间），否则用服务端时间
        // 前端传的是 ISO 8601 UTC 时间（如 "2026-07-20T11:47:00.000Z"），转成 CST
        LocalDateTime storeTime = LocalDateTime.now(CST);
        LocalDate storeDate = serverDate;
        if (dto.getPunchTime() != null) {
            try {
                java.time.OffsetDateTime odt = java.time.OffsetDateTime.parse(
                        dto.getPunchTime(), DateTimeFormatter.ISO_DATE_TIME);
                storeTime = odt.atZoneSameInstant(CST).toLocalDateTime();
                storeDate = storeTime.toLocalDate();
            } catch (DateTimeParseException e) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "打卡时间格式错误");
            }
        }

        try {
            // 查询员工所属考勤组
            AttendanceGroup group = getEmployeeGroup(employeeId);

            // GPS 校验（Haversine 公式计算距离）
            if (group != null && group.getGpsRangeJson() != null
                    && dto.getLatitude() != null && dto.getLongitude() != null) {
                validateGps(group.getGpsRangeJson(), dto.getLatitude(), dto.getLongitude());
            }

            // IP 白名单校验（支持精确 IP 和 CIDR 网段）
            if (group != null && group.getIpWhitelistJson() != null && dto.getClientIp() != null) {
                validateIpWhitelist(group.getIpWhitelistJson(), dto.getClientIp());
            }

            // 判定打卡状态（用服务端 CST 时间，确保与考勤组工作时间比较正确）
            String punchStatus = judgePunchStatus(group, serverTime, type);

            // 写入打卡记录
            AttendanceRecord record = new AttendanceRecord();
            record.setEmployeeId(employeeId);
            record.setPunchDate(storeDate);
            record.setPunchTime(storeTime);
            record.setPunchType(type);
            record.setPunchStatus(punchStatus);
            record.setSource("WEB");

            // 记录 GPS 信息
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

            // 实时更新日汇总（v2.1 双槽位判定）
            // 打卡后立即刷新日汇总，无需等待凌晨批处理作业
            try {
                updateDailySummaryInMemory(employeeId, storeDate);
            } catch (Exception e) {
                log.warn("实时更新日汇总失败（不影响打卡）: empId={}, date={}", employeeId, storeDate, e);
            }

            log.info("员工打卡: empId={}, type={}, status={}, serverTime={}, storeTime={}",
                    employeeId, type, punchStatus, serverTime, storeTime);
            return punchStatus;

        } catch (Exception e) {
            // 上班卡失败时删除 Redis 幂等键，允许用户重试；下班卡无需处理
            if ("IN".equals(type)) {
                stringRedisTemplate.delete(PUNCH_IDEMP_KEY + employeeId + ":" + serverDate.toString() + ":" + type);
            }
            throw e;
        }
    }

    // ========================================================================
    //  今日/本月打卡状态
    // ========================================================================

    /**
     * 获取今日打卡状态（含打卡记录明细列表）
     *
     * 返回当天已打卡次数、迟到/早退/缺卡统计，
     * 以及 IN/OUT 各最新一条记录的明细。
     *
     * @param employeeId 员工 ID
     * @return 今日打卡状态视图
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

    /**
     * 获取本月打卡统计
     *
     * 动态计算当月工作日天数（排除周末、节假日），
     * 逐日检查打卡记录并从原始时间重算迟到/早退，
     * 不依赖日汇总表（避免汇总数据未生成导致的统计偏差）。
     *
     * 已审批请假日期不计入未打卡统计。
     *
     * @param employeeId 员工 ID
     * @return 本月打卡统计视图
     */
    public TodayPunchVO getMonthlyStatus(Long employeeId) {
        LocalDate today = LocalDate.now(CST);
        LocalDate monthStart = today.withDayOfMonth(1);

        // 1. 加载工作日配置和节假日
        List<com.company.hrms.attendance.entity.WorkdayConfig> wkConfigs = workdayConfigMapper.selectList(null);
        java.util.Set<Integer> workdaySet = wkConfigs.stream()
                .filter(w -> w.getIsWorkday() == 1)
                .map(com.company.hrms.attendance.entity.WorkdayConfig::getDayOfWeek)
                .collect(java.util.stream.Collectors.toSet());
        List<com.company.hrms.attendance.entity.HolidayCalendar> holidays = holidayCalendarMapper.selectList(null);
        java.util.Set<java.time.LocalDate> holidayDates = holidays.stream()
                .map(com.company.hrms.attendance.entity.HolidayCalendar::getHolidayDate)
                .collect(java.util.stream.Collectors.toSet());

        // 2. 读取员工考勤组，用于从原始打卡时间重算状态
        //    不依赖存储的 punch_status，防止种子数据伪状态影响统计
        java.time.LocalTime gWorkStart = java.time.LocalTime.of(9, 0);
        java.time.LocalTime gWorkEnd = java.time.LocalTime.of(18, 0);
        int gLateThreshold = 15;
        int gEarlyThreshold = 15;
        try {
            AttendanceGroupMember agm = attendanceGroupMemberMapper.selectById(employeeId);
            if (agm != null) {
                AttendanceGroup grp = attendanceGroupMapper.selectById(agm.getGroupId());
                if (grp != null) {
                    if (grp.getWorkStartTime() != null) gWorkStart = grp.getWorkStartTime();
                    if (grp.getWorkEndTime() != null) gWorkEnd = grp.getWorkEndTime();
                    if (grp.getLateThresholdMinutes() != null) gLateThreshold = grp.getLateThresholdMinutes();
                    if (grp.getEarlyLeaveThresholdMinutes() != null) gEarlyThreshold = grp.getEarlyLeaveThresholdMinutes();
                }
            }
        } catch (Exception e) { log.warn("读取考勤组配置失败", e); }

        // 收集本月打卡记录，按日期+类型去重
        List<AttendanceRecord> allRecords = attendanceRecordMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AttendanceRecord>()
                        .eq(AttendanceRecord::getEmployeeId, employeeId)
                        .ge(AttendanceRecord::getPunchDate, monthStart)
                        .le(AttendanceRecord::getPunchDate, today));
        java.util.Map<java.time.LocalDate, java.util.Set<String>> dateTypeMap = new java.util.HashMap<>();
        java.util.Map<java.time.LocalDate, java.time.LocalTime> earliestIn = new java.util.HashMap<>();
        java.util.Map<java.time.LocalDate, java.time.LocalTime> latestOut = new java.util.HashMap<>();
        java.util.Set<String> dedupKeys = new java.util.HashSet<>();
        for (AttendanceRecord r : allRecords) {
            String key = r.getEmployeeId() + "_" + r.getPunchDate() + "_" + r.getPunchType();
            if (dedupKeys.add(key)) {
                dateTypeMap.computeIfAbsent(r.getPunchDate(), k -> new java.util.HashSet<>()).add(r.getPunchType());
                if ("IN".equals(r.getPunchType())) {
                    java.time.LocalTime t = r.getPunchTime().toLocalTime();
                    if (!earliestIn.containsKey(r.getPunchDate()) || t.isBefore(earliestIn.get(r.getPunchDate()))) {
                        earliestIn.put(r.getPunchDate(), t);
                    }
                } else if ("OUT".equals(r.getPunchType())) {
                    java.time.LocalTime t = r.getPunchTime().toLocalTime();
                    if (!latestOut.containsKey(r.getPunchDate()) || t.isAfter(latestOut.get(r.getPunchDate()))) {
                        latestOut.put(r.getPunchDate(), t);
                    }
                }
            }
        }

        // 按考勤组时间重算迟到/早退（不依赖存储的 punch_status）
        long lateCount = 0, earlyLeaveCount = 0;
        for (java.util.Map.Entry<java.time.LocalDate, java.time.LocalTime> e : earliestIn.entrySet()) {
            java.time.LocalTime inTime = e.getValue();
            if (inTime.isAfter(gWorkStart) && !inTime.isAfter(gWorkStart.plusMinutes(gLateThreshold))) {
                lateCount++;
            }
        }
        for (java.util.Map.Entry<java.time.LocalDate, java.time.LocalTime> e : latestOut.entrySet()) {
            java.time.LocalTime outTime = e.getValue();
            if (outTime.isBefore(gWorkEnd) && !outTime.isBefore(gWorkEnd.minusMinutes(gEarlyThreshold))) {
                earlyLeaveCount++;
            }
        }

        // 3. 收集已审批请假日期（仅工作日、已过去）
        java.util.Set<java.time.LocalDate> approvedLeaveDates = new java.util.HashSet<>();
        List<com.company.hrms.attendance.entity.LeaveApplication> approvedLeaves =
                leaveApplicationMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.company.hrms.attendance.entity.LeaveApplication>()
                                .eq(com.company.hrms.attendance.entity.LeaveApplication::getEmployeeId, employeeId)
                                .eq(com.company.hrms.attendance.entity.LeaveApplication::getStatus, "APPROVED")
                                .ge(com.company.hrms.attendance.entity.LeaveApplication::getStartTime, monthStart.atStartOfDay())
                                .le(com.company.hrms.attendance.entity.LeaveApplication::getStartTime, today.plusDays(1).atStartOfDay()));
        for (com.company.hrms.attendance.entity.LeaveApplication la : approvedLeaves) {
            java.time.LocalDate laStart = la.getStartTime().toLocalDate();
            java.time.LocalDate laEnd = la.getEndTime().toLocalDate();
            if (la.getEndTime().toLocalTime().equals(java.time.LocalTime.MIDNIGHT)) {
                laEnd = laEnd.minusDays(1);
            }
            java.time.LocalDate d = laStart;
            while (!d.isAfter(laEnd) && !d.isAfter(today)) {
                if (workdaySet.contains(d.getDayOfWeek().getValue()) && !holidayDates.contains(d)) {
                    approvedLeaveDates.add(d);
                }
                d = d.plusDays(1);
            }
        }

        // 4. 遍历月初到今天每个工作日，统计打卡
        int shouldDays = 0, clockedCount = 0;
        int missingInCount = 0, missingOutCount = 0;

        LocalDate current = monthStart;
        while (!current.isAfter(today)) {
            // 非工作日跳过
            if (!workdaySet.contains(current.getDayOfWeek().getValue()) || holidayDates.contains(current)) {
                current = current.plusDays(1);
                continue;
            }
            shouldDays++;
            // 已审批请假且无打卡记录 → 不计为未打卡
            // 如果员工请假但实际来打了卡，应正常统计打卡
            if (approvedLeaveDates.contains(current) && dateTypeMap.get(current) == null) {
                current = current.plusDays(1);
                continue;
            }
            java.util.Set<String> types = dateTypeMap.get(current);
            if (types == null) {
                missingInCount++;
                missingOutCount++;
            } else {
                boolean hasIn = types.contains("IN");
                boolean hasOut = types.contains("OUT");
                if (hasIn && hasOut) { clockedCount += 2; }
                else if (hasIn) { clockedCount++; missingOutCount++; }
                else if (hasOut) { clockedCount++; missingInCount++; }
            }
            current = current.plusDays(1);
        }

        return new TodayPunchVO(
                clockedCount, shouldDays * 2L,
                lateCount, earlyLeaveCount,
                missingInCount + missingOutCount);
    }

    /**
     * 昨日打卡概览（全员工聚合，管理端使用）
     *
     * 统计所有在职员工昨日的打卡情况：
     * 已打卡人数、迟到/早退/缺勤人数。
     */
    public TodayPunchVO getYesterdayOverview() {
        LocalDate yesterday = LocalDate.now(CST).minusDays(1);

        // 所有在职员工（试用期+正式）
        List<com.company.hrms.employee.entity.Employee> employees = employeeMapper.search(
                null, null, null, java.util.List.of(10, 20), null, null, null, "");
        long total = employees.size();

        // 查昨天日汇总
        java.util.Map<Long, com.company.hrms.attendance.entity.AttendanceDailySummary> summaryMap = new java.util.HashMap<>();
        attendanceDailySummaryMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.company.hrms.attendance.entity.AttendanceDailySummary>()
                        .eq(com.company.hrms.attendance.entity.AttendanceDailySummary::getSummaryDate, yesterday))
                .forEach(ds -> summaryMap.put(ds.getEmployeeId(), ds));

        long clocked = 0, late = 0, early = 0, absent = 0;
        for (com.company.hrms.employee.entity.Employee emp : employees) {
            com.company.hrms.attendance.entity.AttendanceDailySummary ds = summaryMap.get(emp.getId());
            if (ds == null) {
                absent++;
                continue;
            }
            String raw = ds.getDayStatus();
            if (raw != null && raw.startsWith("am:")) {
                try {
                    String[] parts = raw.split(",");
                    int am = Integer.parseInt(parts[0].split(":")[1]);
                    int pm = Integer.parseInt(parts[1].split(":")[1]);
                    if (am == 4 || pm == 4) continue; // 请假不计入统计
                    boolean hasIn = (am != 5);
                    boolean hasOut = (pm != 5);
                    if (hasIn || hasOut) clocked++;
                    if (am == 1) late++;
                    if (pm == 2) early++;
                    if (am == 3 || pm == 3) absent++;
                    else if (am == 5 && pm == 5) absent++;
                } catch (Exception e) {}
            } else {
                // 旧格式兼容
                boolean hasRecord = ("NORMAL".equals(raw) || "LATE".equals(raw) || "EARLY_LEAVE".equals(raw)
                        || "ABSENT_HALF".equals(raw) || "MISSING_IN".equals(raw) || "MISSING_OUT".equals(raw));
                if (hasRecord) clocked++;
                if ("LATE".equals(raw)) late++;
                if ("EARLY_LEAVE".equals(raw)) early++;
                if ("ABSENT".equals(raw)) absent++;
            }
        }
        return new TodayPunchVO(clocked, total, late, early, absent);
    }

    // ========================================================================
    //  打卡记录分页
    // ========================================================================

    /**
     * 打卡记录分页查询
     *
     * 按日期范围搜索打卡记录，按 employee_id + punch_date 分组展示。
     * 支持关键字按员工姓名/工号过滤。
     *
     * 注意：先全量查询再手动分页，适合记录量可控的场景。
     * 数据量大的情况下需改为 SQL 层分页。
     *
     * @param pageParam 分页参数
     * @param keyword   搜索关键字（员工姓名/工号）
     * @param dateFrom  开始日期
     * @param dateTo    结束日期
     * @return 分页打卡记录
     */
    public PageResult<PunchRecordVO> pageRecords(PageParam pageParam, String keyword,
                                                  String dateFrom, String dateTo) {
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

        // 按 employeeId + punchDate 分组，合并 IN/OUT
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

            // 查询员工姓名和部门
            try {
                List<com.company.hrms.employee.entity.Employee> empList = employeeMapper.search(
                        null, null, null, null, null, null, null,
                        " AND e.id = " + first.getEmployeeId());
                if (!empList.isEmpty()) {
                    com.company.hrms.employee.entity.Employee emp = empList.get(0);
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

            // 关键字过滤
            if (keyword != null && !keyword.isEmpty()) {
                if (!vo.getEmployeeName().contains(keyword)) {
                    continue;
                }
            }

            allVoList.add(vo);
        }

        // 手动分页
        int page = Math.max(pageParam.getPage(), 1);
        int pageSize = pageParam.getPageSize() > 0 ? pageParam.getPageSize() : 20;
        int from = (page - 1) * pageSize;
        int to = Math.min(from + pageSize, allVoList.size());
        List<PunchRecordVO> voList = from >= allVoList.size()
                ? Collections.emptyList()
                : allVoList.subList(from, to);

        return PageResult.of(voList, allVoList.size(), pageParam);
    }

    // ========================================================================
    //  补卡管理
    // ========================================================================

    /**
     * 补卡申请
     *
     * 员工因漏打卡等异常情况申请补正。
     * 需经过审批流程，每月最多 2 次，月锁定后不可补卡。
     *
     * 流程：
     *   1. 校验月是否锁定（AttendanceMonthLock）
     *   2. 校验补卡配额（Redis + DB 双读，每月上限 2 次）
     *   3. 创建补卡申请并提交审批
     *   4. 审批通过后由 ApprovalEventListener 自动修正打卡记录
     *
     * @param employeeId 员工 ID
     * @param dto        补卡参数
     * @return 补卡申请结果（含 id、状态、审批实例 ID）
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> applyFix(Long employeeId, PunchFixDTO dto) {
        LocalDate fixDate = LocalDate.parse(dto.getPunchDate());
        String ym = fixDate.format(DateTimeFormatter.ofPattern("yyyy-MM"));

        // 1. 校验月锁定（锁定后考勤数据冻结，不可修改）
        AttendanceMonthLock lock = attendanceMonthLockMapper.selectOne(
                new LambdaQueryWrapper<AttendanceMonthLock>()
                        .eq(AttendanceMonthLock::getYearMonth, ym));
        if (lock != null && lock.getStatus() == 20) {
            throw new BusinessException(ErrorCode.ATTENDANCE_MONTH_LOCKED, "考勤月已锁定，请联系 HR 解锁");
        }

        // 2. 校验日期是否为工作日（依赖考勤日历/假期配置）
        if (!isWorkday(fixDate)) {
            throw new BusinessException(ErrorCode.SUPPLEMENT_NOT_WORKDAY, "选择的日期不是工作日，不可补卡");
        }

        // 3. 校验该日考勤是否已正常（日汇总 double-status = "am:0,pm:0" 表示双槽位正常，不可补卡）
        AttendanceDailySummary summary = attendanceDailySummaryMapper.selectByEmployeeAndDate(employeeId, fixDate);
        if (summary != null && "am:0,pm:0".equals(summary.getDayStatus())) {
            throw new BusinessException(ErrorCode.SUPPLEMENT_ALREADY_PUNCHED, "该日期考勤已正常，不可补卡");
        }

        // 4. 禁止补未来日期的卡
        if (fixDate.isAfter(LocalDate.now(CST))) {
            throw new BusinessException(ErrorCode.SUPPLEMENT_FUTURE_DATE, "不允许补未来日期的卡");
        }

        // 5. 校验补卡配额（Redis 不可用时回落 DB）
        int usedQuota = resolveUsedQuota(employeeId, ym, fixDate);
        if (usedQuota >= MAX_SUPPLEMENT_QUOTA) {
            throw new BusinessException(ErrorCode.MAKEUP_LIMIT_EXCEEDED, "每月最多补卡 " + MAX_SUPPLEMENT_QUOTA + " 次");
        }

        String punchType = dto.getType() != null ? dto.getType().toUpperCase() : "IN";

        // 解析补卡时间（兼容 HH:mm 和 ISO 格式）
        String punchTimeStr = dto.getPunchTime();
        LocalDateTime makeupTime;
        try {
            makeupTime = LocalDateTime.parse(punchTimeStr, DateTimeFormatter.ISO_DATE_TIME);
        } catch (DateTimeParseException e) {
            LocalTime time = LocalTime.parse(punchTimeStr, DateTimeFormatter.ofPattern("HH:mm"));
            makeupTime = LocalDateTime.of(LocalDate.parse(dto.getPunchDate()), time);
        }

        // 插入补卡申请
        AttendanceSupplement supplement = new AttendanceSupplement();
        supplement.setEmployeeId(employeeId);
        supplement.setMakeupDate(dto.getPunchDate());
        supplement.setPunchType(punchType);
        supplement.setMakeupTime(makeupTime);
        supplement.setReason(dto.getReason());
        supplement.setStatus("PENDING");
        attendanceSupplementMapper.insert(supplement);

        // 发起审批流程
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

        // Redis 原子自增补卡计数
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
     * 补卡剩余次数查询
     *
     * @param employeeId 员工 ID
     * @return 含总配额、已使用、剩余可用次数的 VO
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
     * 解析已使用的补卡次数
     *
     * 策略：Redis 优先（速度快、跨请求共享），
     * 未命中或 Redis 不可用时回退 DB 查询，
     * 并回填 Redis（带月截止 TTL），避免下次 DB 查询。
     *
     * @param employeeId 员工 ID
     * @param ym        年份-月份（如 "2026-07"）
     * @param refDate   参考日期（用于计算 TTL 截止到月末）
     * @return 已使用的补卡次数
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

    // ========================================================================
    //  工作日判定（考勤日历 + 节假日配置）
    // ========================================================================

    /**
     * 判断指定日期是否为工作日
     *
     * 规则（与 getMonthlyStatus 一致）：
     *   1. 取 workday_config 配置的每周工作日集合
     *   2. 若当天在 holiday_calendar 中 → 非工作日（法定节假日）
     *   3. 同时满足「是配置的工作日」且「非法定节假日」才视为工作日
     *
     * @param date 待判断日期
     * @return true = 工作日，false = 休息日/节假日
     */
    private boolean isWorkday(LocalDate date) {
        List<WorkdayConfig> configs = workdayConfigMapper.selectList(null);
        Set<Integer> workdaySet = configs.stream()
                .filter(w -> w.getIsWorkday() == 1)
                .map(WorkdayConfig::getDayOfWeek)
                .collect(Collectors.toSet());
        List<HolidayCalendar> holidays = holidayCalendarMapper.selectList(null);
        Set<LocalDate> holidayDates = holidays.stream()
                .map(HolidayCalendar::getHolidayDate)
                .collect(Collectors.toSet());
        int dayOfWeek = date.getDayOfWeek().getValue(); // 1=周一 … 7=周日，与 workday_config 一致
        return workdaySet.contains(dayOfWeek) && !holidayDates.contains(date);
    }

    // ========================================================================
    //  日汇总实时更新（v2.1 双槽位）
    // ========================================================================

    /**
     * 实时更新日汇总（v2.1 双槽位模式）
     *
     * 每次打卡后立即执行，无需等待凌晨批处理。
     * 根据当天全部打卡记录和已审批请假记录，重新判定双槽位状态：
     *   am:code,pm:code
     *   code: 0=正常, 1=迟到, 2=早退, 3=旷工, 4=请假, 5=缺卡
     */
    private void updateDailySummaryInMemory(Long employeeId, LocalDate date) {
        List<AttendanceRecord> dayRecords = attendanceRecordMapper.selectByEmployeeAndDate(employeeId, date);
        boolean amLeave = false, pmLeave = false;
        // 查询当天已审批的请假，判断是否覆盖 AM/PM 槽位
        try {
            List<com.company.hrms.attendance.entity.LeaveApplication> leaves = leaveApplicationMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.company.hrms.attendance.entity.LeaveApplication>()
                            .eq(com.company.hrms.attendance.entity.LeaveApplication::getEmployeeId, employeeId)
                            .eq(com.company.hrms.attendance.entity.LeaveApplication::getStatus, "APPROVED")
                            .le(com.company.hrms.attendance.entity.LeaveApplication::getStartTime, date.plusDays(1).atStartOfDay())
                            .ge(com.company.hrms.attendance.entity.LeaveApplication::getEndTime, date.atStartOfDay()));
            for (com.company.hrms.attendance.entity.LeaveApplication la : leaves) {
                java.time.LocalTime s = la.getStartTime().toLocalTime();
                java.time.LocalTime e = la.getEndTime().toLocalTime();
                if (s.isBefore(java.time.LocalTime.NOON) && e.isAfter(java.time.LocalTime.MIDNIGHT)) amLeave = true;
                if (e.isAfter(java.time.LocalTime.NOON) && s.isBefore(java.time.LocalTime.NOON)) pmLeave = true;
            }
        } catch (Exception e) { log.warn("查询请假覆盖失败", e); }

        // 读取员工考勤组配置
        java.time.LocalTime workStart = java.time.LocalTime.of(9, 0);
        java.time.LocalTime workEnd = java.time.LocalTime.of(18, 0);
        int threshold = 15;
        boolean isFlexible = false;
        java.time.LocalTime flexEarliest = null;
        java.time.LocalTime flexLatest = null;
        try {
            AttendanceGroupMember agm = attendanceGroupMemberMapper.selectById(employeeId);
            if (agm != null) {
                AttendanceGroup grp = attendanceGroupMapper.selectById(agm.getGroupId());
                if (grp != null) {
                    if (grp.getWorkStartTime() != null) workStart = grp.getWorkStartTime();
                    if (grp.getWorkEndTime() != null) workEnd = grp.getWorkEndTime();
                    if (grp.getLateThresholdMinutes() != null) threshold = grp.getLateThresholdMinutes();
                    if ("FLEXIBLE".equals(grp.getShiftType())) {
                        isFlexible = true;
                        flexEarliest = grp.getFlexStartEarliest();
                        flexLatest = grp.getFlexStartLatest();
                    }
                }
            }
        } catch (Exception e) { log.warn("读取考勤组配置失败", e); }

        // AM 槽位判定
        int amCode = 5, pmCode = 5;
        if (amLeave) amCode = 4;
        else {
            AttendanceRecord inRec = dayRecords.stream().filter(r -> "IN".equals(r.getPunchType()))
                    .min(java.util.Comparator.comparing(AttendanceRecord::getPunchTime)).orElse(null);
            if (inRec != null) {
                java.time.LocalTime t = inRec.getPunchTime().toLocalTime();
                if (isFlexible && flexEarliest != null && flexLatest != null) {
                    amCode = (!t.isBefore(flexEarliest) && !t.isAfter(flexLatest)) ? 0 : 1;
                } else {
                    if (!t.isAfter(workStart)) amCode = 0;
                    else if (!t.isAfter(workStart.plusMinutes(threshold))) amCode = 1;
                    else amCode = 3;
                }
            }
        }
        // PM 槽位判定
        if (pmLeave) pmCode = 4;
        else {
            AttendanceRecord outRec = dayRecords.stream().filter(r -> "OUT".equals(r.getPunchType()))
                    .max(java.util.Comparator.comparing(AttendanceRecord::getPunchTime)).orElse(null);
            if (outRec != null) {
                java.time.LocalTime t = outRec.getPunchTime().toLocalTime();
                if (!t.isBefore(workEnd)) pmCode = 0;
                else if (!t.isBefore(workEnd.minusMinutes(threshold))) pmCode = 2;
                else pmCode = 3;
            }
        }

        // 写入/更新日汇总
        com.company.hrms.attendance.entity.AttendanceDailySummary existing =
                attendanceDailySummaryMapper.selectByEmployeeAndDate(employeeId, date);
        com.company.hrms.attendance.entity.AttendanceDailySummary ds;
        if (existing == null) {
            ds = new com.company.hrms.attendance.entity.AttendanceDailySummary();
            ds.setEmployeeId(employeeId);
            ds.setSummaryDate(date);
        } else {
            ds = existing;
        }
        ds.setDayStatus("am:" + amCode + ",pm:" + pmCode);
        dayRecords.stream().filter(r -> "IN".equals(r.getPunchType()))
                .min(java.util.Comparator.comparing(AttendanceRecord::getPunchTime))
                .ifPresent(r -> ds.setClockInTime(r.getPunchTime()));
        dayRecords.stream().filter(r -> "OUT".equals(r.getPunchType()))
                .max(java.util.Comparator.comparing(AttendanceRecord::getPunchTime))
                .ifPresent(r -> ds.setClockOutTime(r.getPunchTime()));
        if (ds.getId() == null) attendanceDailySummaryMapper.insert(ds);
        else attendanceDailySummaryMapper.updateById(ds);
    }

    // ========================================================================
    //  打卡判定逻辑
    // ========================================================================

    /**
     * 打卡判定逻辑入口
     *
     * 根据考勤组班次类型（FIXED/FLEXIBLE）分发到对应的判定方法。
     * 未分配考勤组的员工无法打卡。
     *
     * 判定规则：
     *   上班卡：准时或早到 → NORMAL，迟到阈值内 → LATE，超过 → ABSENT_HALF
     *   下班卡：准时或加班 → NORMAL，早退阈值内 → EARLY_LEAVE，超过 → ABSENT_HALF
     */
    private String judgePunchStatus(AttendanceGroup group, LocalTime punchTime, String type) {
        if (group == null || group.getWorkStartTime() == null || group.getWorkEndTime() == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "您未分配考勤组，请联系HR配置后再打卡");
        }

        String shiftType = group.getShiftType();
        if ("FLEXIBLE".equals(shiftType)) {
            return judgeFlexiblePunch(group, punchTime, type);
        }

        // SCHEDULE（排班制）暂等同于 FIXED 处理
        return judgeFixedPunch(group, punchTime, type);
    }

    /**
     * 弹性班次（FLEXIBLE）打卡判定
     *
     * 上班：在弹性范围 [flexStartEarliest, flexStartLatest] 内打卡即 NORMAL，
     *       早于 earliest 或晚于 latest 标记 LATE
     * 下班：沿用固定班次的下班判定逻辑
     */
    private String judgeFlexiblePunch(AttendanceGroup group, LocalTime punchTime, String type) {
        if ("IN".equals(type)) {
            LocalTime earliest = group.getFlexStartEarliest();
            LocalTime latest = group.getFlexStartLatest();
            if (earliest != null && latest != null) {
                if (!punchTime.isBefore(earliest) && !punchTime.isAfter(latest)) {
                    return "NORMAL";
                }
                return "LATE";
            }
            // 未配置弹性范围，按固定班次逻辑
            return judgeFixedPunch(group, punchTime, type);
        } else if ("OUT".equals(type)) {
            return judgeFixedPunch(group, punchTime, type);
        }
        return "NORMAL";
    }

    /**
     * 固定班次（FIXED）打卡判定
     *
     * 上班判定：
     *   punchTime <= onDuty             → NORMAL
     *   punchTime <= onDuty+threshold   → LATE
     *   else                            → ABSENT_HALF
     *
     * 下班判定：
     *   punchTime >= offDuty                     → NORMAL
     *   punchTime >= offDuty-earlyThreshold      → EARLY_LEAVE
     *   else                                     → ABSENT_HALF
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

    // ========================================================================
    //  私有工具方法
    // ========================================================================

    /**
     * 查询员工所属考勤组
     *
     * 先查 attendance_group_member（employee_id → group_id），
     * 再查 attendance_group 获取完整配置。
     * 考勤组成员关系的主键是 employee_id（一对一）。
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
     *
     * 计算员工打卡位置与考勤组配置的中心点之间的距离，
     * 超过 radiusM 则拒绝打卡。
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
     *
     * 配置示例：["192.168.1.100", "10.0.0.0/8"]
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

    /** CIDR 网段匹配 */
    private boolean isIpInCidr(String ip, String network, int prefix) {
        try {
            long ipLong = ipToLong(ip);
            long mask = prefix == 0 ? 0 : (0xFFFFFFFFL << (32 - prefix));
            return (ipLong & mask) == (ipToLong(network) & mask);
        } catch (Exception e) { return false; }
    }

    /** IPv4 地址转 long */
    private long ipToLong(String ip) {
        String[] octets = ip.split("\\.");
        long result = 0;
        for (int i = 0; i < 4; i++) result = (result << 8) | (Integer.parseInt(octets[i]) & 0xFF);
        return result;
    }

    /**
     * Haversine 距离计算（单位：米）
     *
     * 用于 GPS 打卡范围校验。
     * 地球半径取 6371km，返回球面两点之间的弧线距离。
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

    /** 获取到当天结束的秒数，最少保留 60 秒 */
    private long getSecondsUntilEndOfDay(LocalDate date) {
        LocalDateTime now = LocalDateTime.now(CST);
        LocalDateTime endOfDay = date.atTime(LocalTime.MAX);
        long seconds = Duration.between(now, endOfDay).getSeconds();
        return Math.max(seconds, 60);
    }

    /** 获取到月末的秒数，最少保留 60 秒 */
    private long getSecondsUntilEndOfMonth(LocalDate date) {
        LocalDateTime now = LocalDateTime.now(CST);
        LocalDate lastDay = date.withDayOfMonth(date.lengthOfMonth());
        LocalDateTime endOfMonth = lastDay.atTime(LocalTime.MAX);
        long seconds = Duration.between(now, endOfMonth).getSeconds();
        return Math.max(seconds, 60);
    }
}
