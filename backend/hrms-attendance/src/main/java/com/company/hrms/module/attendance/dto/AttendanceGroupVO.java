package com.company.hrms.module.attendance.dto;

import com.company.hrms.attendance.entity.AttendanceGroup;
import com.company.hrms.attendance.entity.AttendanceGroupScope;
import lombok.Data;

import java.util.List;

/**
 * 考勤组详情 VO
 * 继承 AttendanceGroup 所有字段，额外携带适用范围列表和成员数
 */
@Data
public class AttendanceGroupVO {

    private Long id;
    private String name;
    private String shiftType;
    private String workStartTime;
    private String workEndTime;
    private String lunchStartTime;
    private String lunchEndTime;
    private String flexStartEarliest;
    private String flexStartLatest;
    private Integer lateThresholdMinutes;
    private Integer earlyLeaveThresholdMinutes;
    private String ipWhitelistJson;
    private String gpsRangeJson;
    private Integer deleted;
    private String createdAt;
    private String updatedAt;

    /** 适用范围列表 */
    private List<AttendanceGroupScope> scopes;

    /** 成员数量 */
    private Integer memberCount;

    /**
     * 从实体构造 VO
     */
    public static AttendanceGroupVO from(AttendanceGroup group, List<AttendanceGroupScope> scopes, Integer memberCount) {
        AttendanceGroupVO vo = new AttendanceGroupVO();
        vo.setId(group.getId());
        vo.setName(group.getName());
        vo.setShiftType(group.getShiftType());
        vo.setWorkStartTime(group.getWorkStartTime() != null ? group.getWorkStartTime().toString() : null);
        vo.setWorkEndTime(group.getWorkEndTime() != null ? group.getWorkEndTime().toString() : null);
        vo.setLunchStartTime(group.getLunchStartTime() != null ? group.getLunchStartTime().toString() : null);
        vo.setLunchEndTime(group.getLunchEndTime() != null ? group.getLunchEndTime().toString() : null);
        vo.setFlexStartEarliest(group.getFlexStartEarliest() != null ? group.getFlexStartEarliest().toString() : null);
        vo.setFlexStartLatest(group.getFlexStartLatest() != null ? group.getFlexStartLatest().toString() : null);
        vo.setLateThresholdMinutes(group.getLateThresholdMinutes());
        vo.setEarlyLeaveThresholdMinutes(group.getEarlyLeaveThresholdMinutes());
        vo.setIpWhitelistJson(group.getIpWhitelistJson());
        vo.setGpsRangeJson(group.getGpsRangeJson());
        vo.setDeleted(group.getDeleted());
        vo.setCreatedAt(group.getCreatedAt() != null ? group.getCreatedAt().toString().replace("T", " ") : null);
        vo.setUpdatedAt(group.getUpdatedAt() != null ? group.getUpdatedAt().toString().replace("T", " ") : null);
        vo.setScopes(scopes);
        vo.setMemberCount(memberCount);
        return vo;
    }
}
