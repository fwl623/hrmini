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
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final ApprovalEngineService approvalEngineService;
    private final com.company.hrms.employee.mapper.EmployeeMapper employeeMapper;

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
        LocalDate punchDate = LocalDate.now();
        LocalDateTime punchTime = LocalDateTime.now();
        String type = dto.getType() != null ? dto.getType().toUpperCase() : "IN";

        // 如果请求携带了打卡时间，使用请求时间
        if (dto.getPunchTime() != null) {
            try {
                punchTime = LocalDateTime.parse(dto.getPunchTime(), DateTimeFormatter.ISO_DATE_TIME);
                punchDate = punchTime.toLocalDate();
            } catch (DateTimeParseException e) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "打卡时间格式错误");
            }
        }

        // 1. Redis 幂等校验
        String idempKey = PUNCH_IDEMP_KEY + employeeId + ":" + punchDate.toString() + ":" + type;
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(idempKey, "1", getSecondsUntilEndOfDay(punchDate), TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(success)) {
            throw new BusinessException(ErrorCode.PUNCH_DUPLICATE, "您已打卡，请勿重复操作");
        }

        try {
            // 2. 查询员工所属考勤组
            AttendanceGroup group = getEmployeeGroup(employeeId);

            // 3. GPS 校验
            if (group != null && group.getGpsRangeJson() != null && dto.getLatitude() != null && dto.getLongitude() != null) {
                validateGps(group.getGpsRangeJson(), dto.getLatitude(), dto.getLongitude());
            }

            // 4. 判定打卡状态
            String punchStatus = judgePunchStatus(group, punchTime.toLocalTime(), type);

            // 5. 写入打卡记录
            AttendanceRecord record = new AttendanceRecord();
            record.setEmployeeId(employeeId);
            record.setPunchDate(punchDate);
            record.setPunchTime(punchTime);
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
            log.info("员工打卡: empId={}, type={}, status={}, time={}", employeeId, type, punchStatus, punchTime);
            return punchStatus;

        } catch (Exception e) {
            // 打卡失败时删除幂等键（允许重试）
            stringRedisTemplate.delete(idempKey);
            throw e;
        }
    }

    // ========== 今日状态 ==========

    /**
     * 获取今日打卡状态
     */
    public TodayPunchVO getTodayStatus(Long employeeId) {
        LocalDate today = LocalDate.now();

        // 查今日所有打卡记录
        List<AttendanceRecord> records = attendanceRecordMapper.selectByEmployeeAndDate(employeeId, today);

        long totalCount = 2; // 每日应打 2 次（IN + OUT）
        long clockedCount = records.size();
        long lateCount = records.stream().filter(r -> "LATE".equals(r.getPunchStatus())).count();
        long earlyLeaveCount = records.stream().filter(r -> "EARLY_LEAVE".equals(r.getPunchStatus())).count();
        long absentCount = records.stream().filter(r -> "ABSENT_HALF".equals(r.getPunchStatus())).count();

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

        // 按 employeeId + punchDate 分组，合并 IN/OUT
        Map<String, List<AttendanceRecord>> grouped = allRecords.stream()
                .collect(Collectors.groupingBy(r -> r.getEmployeeId() + "_" + r.getPunchDate()));

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
                    // JVM 默认 UTC，转换到 +08:00 显示
                    timeStr = r.getPunchTime()
                            .atZone(java.time.ZoneOffset.UTC)
                            .withZoneSameInstant(java.time.ZoneId.of("Asia/Shanghai"))
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
        req.setApplicantId(employeeId);
        req.setTitle("补卡申请#" + supplement.getId());
        req.setBusinessSummary(dto.getPunchDate() + " " + punchType);
        req.setBusinessNo("MAKEUP-" + supplement.getId());
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
            // 无考勤组或未配置时间，默认正常
            return "NORMAL";
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
        LocalDateTime endOfDay = date.atTime(LocalTime.MAX);
        long seconds = Duration.between(LocalDateTime.now(), endOfDay).getSeconds();
        return Math.max(seconds, 60); // 至少保留 60 秒，避免跨天或午夜后精度问题
    }

    /**
     * 获取到月末的秒数
     */
    private long getSecondsUntilEndOfMonth(LocalDate date) {
        LocalDate lastDay = date.withDayOfMonth(date.lengthOfMonth());
        LocalDateTime endOfMonth = lastDay.atTime(LocalTime.MAX);
        long seconds = Duration.between(LocalDateTime.now(), endOfMonth).getSeconds();
        return Math.max(seconds, 60); // 至少保留 60 秒，避免跨天或午夜后精度问题
    }
}
