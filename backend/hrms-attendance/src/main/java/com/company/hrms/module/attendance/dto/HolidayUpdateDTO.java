package com.company.hrms.module.attendance.dto;

import lombok.Data;

/**
 * 更新节假日 DTO
 */
@Data
public class HolidayUpdateDTO {
    private String holidayDate;  // YYYY-MM-DD
    private String name;
}
