package com.company.hrms.module.attendance.dto;

import lombok.Data;

/**
 * 补卡请求 DTO
 */
@Data
public class PunchFixDTO {
    private String punchDate;  // YYYY-MM-DD，当月及前月
    private String type;       // in / out
    private String punchTime;  // HH:mm 或 ISO datetime
    private String reason;     // ≤256 字符
}
