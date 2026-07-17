package com.company.hrms.module.attendance.dto;

import lombok.Data;

/**
 * 创建节假日 DTO
 */
@Data
public class HolidayCreateDTO {
    private String holidayDate;  // YYYY-MM-DD
    private String name;
}
