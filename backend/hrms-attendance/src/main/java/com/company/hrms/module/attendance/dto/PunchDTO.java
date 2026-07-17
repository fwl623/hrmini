package com.company.hrms.module.attendance.dto;

import lombok.Data;

/**
 * 打卡请求 DTO
 */
@Data
public class PunchDTO {
    /** in / out */
    private String type;
    /** 打卡时间 ISO 8601，为空则默认当前时间 */
    private String punchTime;
    /** GPS 纬度 */
    private Double latitude;
    /** GPS 经度 */
    private Double longitude;
}
