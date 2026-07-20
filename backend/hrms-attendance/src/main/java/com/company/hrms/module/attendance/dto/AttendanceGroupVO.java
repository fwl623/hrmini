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

    /** 适用范围（DTO 格式，用于前端回显） */
    private ApplicableScopeDTO applicableScope;

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

        // 从 scopes 构建 applicableScope（前端回显用）
        if (scopes != null && !scopes.isEmpty()) {
            ApplicableScopeDTO scope = new ApplicableScopeDTO();
            List<Long> deptIds = new java.util.ArrayList<>();
            List<Long> posIds = new java.util.ArrayList<>();
            List<Long> empIds = new java.util.ArrayList<>();
            for (AttendanceGroupScope s : scopes) {
                switch (s.getScopeType()) {
                    case "DEPARTMENT" -> deptIds.add(s.getScopeId());
                    case "POSITION" -> posIds.add(s.getScopeId());
                    case "EMPLOYEE" -> empIds.add(s.getScopeId());
                }
            }
            if (!deptIds.isEmpty()) scope.setDepartmentIds(deptIds);
            if (!posIds.isEmpty()) scope.setPositionIds(posIds);
            if (!empIds.isEmpty()) scope.setEmployeeIds(empIds);
            vo.setApplicableScope(scope);
        }

        return vo;
    }
}
