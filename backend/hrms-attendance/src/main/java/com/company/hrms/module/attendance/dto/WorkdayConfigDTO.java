package com.company.hrms.module.attendance.dto;

import lombok.Data;

/**
 * 工作日配置 DTO
 */
@Data
public class WorkdayConfigDTO {
    /** 1=周一..7=周日 */
    private Integer dayOfWeek;
    /** 是否工作日 */
    private Boolean isWorkday;
}
