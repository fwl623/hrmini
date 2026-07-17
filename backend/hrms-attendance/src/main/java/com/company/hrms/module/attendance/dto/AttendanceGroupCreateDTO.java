package com.company.hrms.module.attendance.dto;

import lombok.Data;

import java.util.List;

/**
 * 创建/更新考勤组 DTO
 * 与前端 API 契约对齐：onDuty/offDuty, restStart/restEnd
 */
@Data
public class AttendanceGroupCreateDTO {

    private String name;

    /** FIXED / FLEXIBLE / SCHEDULE */
    private String shiftType;

    /** 上班时间 HH:mm */
    private String onDuty;

    /** 下班时间 HH:mm */
    private String offDuty;

    /** 午休开始 HH:mm，默认 12:00 */
    private String restStart;

    /** 午休结束 HH:mm，默认 13:00 */
    private String restEnd;

    /** 弹性班次范围 */
    private FlexibleRangeDTO flexibleRange;

    /** 迟到阈值（分钟），默认 15 */
    private Integer lateThreshold;

    /** 早退阈值（分钟），默认 15 */
    private Integer earlyLeaveThreshold;

    /** 适用范围 */
    private ApplicableScopeDTO applicableScope;

    /** IP 白名单 */
    private List<String> ipWhitelist;

    /** GPS 范围 */
    private GpsRangeDTO gpsRange;
}
