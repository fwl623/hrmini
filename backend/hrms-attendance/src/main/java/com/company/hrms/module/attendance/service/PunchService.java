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
            throw new BusinessException(ErrorCode.PARAM_INVALID, "您已打卡，请勿重复操作");
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

        IPage<AttendanceRecord> page = attendanceRecordMapper.selectPage(
                new Page<>(pageParam.getPage(), pageParam.getPageSize()), wrapper);

        // 按 employeeId + punchDate 分组，合并 IN/OUT
        Map<String, List<AttendanceRecord>> grouped = page.getRecords().stream()
                .collect(Collectors.groupingBy(r -> r.getEmployeeId() + "_" + r.getPunchDate()));

        List<PunchRecordVO> voList = new ArrayList<>();
        for (Map.Entry<String, List<AttendanceRecord>> entry : grouped.entrySet()) {
            List<AttendanceRecord> recs = entry.getValue();

            PunchRecordVO vo = new PunchRecordVO();
            AttendanceRecord first = recs.get(0);
            vo.setEmployeeId(first.getEmployeeId());
            vo.setPunchDate(first.getPunchDate() != null ? first.getPunchDate().toString() : null);
            vo.setSource(first.getSource());
            vo.setClientIp(first.getClientIp());
            vo.setGpsJson(first.getGpsJson());

            // TODO: 通过 Feign 调用员工服务获取 employeeName / departmentName
            // 当前返回 ID 作为占位
            vo.setEmployeeName(String.valueOf(first.getEmployeeId()));
            vo.setDepartmentName("");

            for (AttendanceRecord r : recs) {
                if ("IN".equals(r.getPunchType())) {
                    vo.setClockInTime(r.getPunchTime() != null
                            ? r.getPunchTime().format(DateTimeFormatter.ofPattern("HH:mm")) : null);
                    vo.setClockInStatus(r.getPunchStatus());
                } else if ("OUT".equals(r.getPunchType())) {
                    vo.setClockOutTime(r.getPunchTime() != null
                            ? r.getPunchTime().format(DateTimeFormatter.ofPattern("HH:mm")) : null);
                    vo.setClockOutStatus(r.getPunchStatus());
                }
            }

            // 关键字过滤（简单实现，完整需员工服务）
            if (keyword != null && !keyword.isEmpty()) {
                if (!vo.getEmployeeName().contains(keyword)) {
                    continue;
                }
            }

            voList.add(vo);
        }

        return PageResult.of(voList, page.getTotal(), pageParam);
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

        // 2. 校验补卡配额
        String quotaKey = SUPPLEMENT_KEY + employeeId + ":" + ym;
        String usedStr = stringRedisTemplate.opsForValue().get(quotaKey);
        int usedQuota = 0;
        if (usedStr != null) {
            usedQuota = Integer.parseInt(usedStr);
        } else {
            // Redis 无缓存，从 DB 查询
            usedQuota = attendanceSupplementMapper.countByEmployeeAndMonth(employeeId, ym);
            // 写入 Redis 缓存
            stringRedisTemplate.opsForValue().set(quotaKey, String.valueOf(usedQuota),
                     getSecondsUntilEndOfMonth(fixDate), TimeUnit.SECONDS);
        }

        if (usedQuota >= MAX_SUPPLEMENT_QUOTA) {
            throw new BusinessException(ErrorCode.MAKEUP_LIMIT_EXCEEDED, "每月最多补卡 " + MAX_SUPPLEMENT_QUOTA + " 次");
        }

        // 3. 补卡类型转换
        String punchType = dto.getType() != null ? dto.getType().toUpperCase() : "IN";

        // 4. 插入补卡申请
        AttendanceSupplement supplement = new AttendanceSupplement();
        supplement.setEmployeeId(employeeId);
        supplement.setMakeupDate(dto.getPunchDate());
        supplement.setPunchType(punchType);
        supplement.setMakeupTime(LocalDateTime.parse(dto.getPunchTime(), DateTimeFormatter.ISO_DATE_TIME));
        supplement.setReason(dto.getReason());
        supplement.setStatus("PENDING");
        attendanceSupplementMapper.insert(supplement);

        // 5. Redis 原子自增
        stringRedisTemplate.opsForValue().increment(quotaKey);
        // 确保 TTL
        stringRedisTemplate.expire(quotaKey, getSecondsUntilEndOfMonth(fixDate), TimeUnit.SECONDS);

        log.info("补卡申请: empId={}, date={}, type={}, id={}", employeeId, dto.getPunchDate(), punchType, supplement.getId());

        Map<String, Object> result = new HashMap<>();
        result.put("id", supplement.getId());
        result.put("status", "PENDING");
        return result;
    }

    /**
     * 补卡剩余次数
     */
    public QuotaVO getFixQuota(Long employeeId) {
        String ym = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        String quotaKey = SUPPLEMENT_KEY + employeeId + ":" + ym;

        String usedStr = stringRedisTemplate.opsForValue().get(quotaKey);
        int usedQuota = 0;
        if (usedStr != null) {
            usedQuota = Integer.parseInt(usedStr);
        } else {
            usedQuota = attendanceSupplementMapper.countByEmployeeAndMonth(employeeId, ym);
            stringRedisTemplate.opsForValue().set(quotaKey, String.valueOf(usedQuota),
                    getSecondsUntilEndOfMonth(LocalDate.now()), TimeUnit.SECONDS);
        }

        return new QuotaVO(MAX_SUPPLEMENT_QUOTA, usedQuota, Math.max(0, MAX_SUPPLEMENT_QUOTA - usedQuota));
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
        return Duration.between(LocalDateTime.now(), endOfDay).getSeconds();
    }

    /**
     * 获取到月末的秒数
     */
    private long getSecondsUntilEndOfMonth(LocalDate date) {
        LocalDate lastDay = date.withDayOfMonth(date.lengthOfMonth());
        LocalDateTime endOfMonth = lastDay.atTime(LocalTime.MAX);
        return Duration.between(LocalDateTime.now(), endOfMonth).getSeconds();
    }
}
