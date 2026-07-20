package com.company.hrms.module.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * 请假申请 DTO
 */
@Data
public class LeaveApplicationDTO {
    @NotBlank(message = "请假类型不能为空")
    private String leaveType;       // ANNUAL/SICK/PERSONAL/MARRIAGE/MATERNITY/BEREAVEMENT/COMP_OFF
    @NotBlank(message = "开始时间不能为空")
    private String startTime;       // ISO datetime
    @NotBlank(message = "结束时间不能为空")
    private String endTime;         // ISO datetime
    /** 支持 0.5；缺省会 NPE→90001，须校验为 10001（BUG-026） */
    @NotNull(message = "请假天数不能为空")
    @Positive(message = "请假天数必须大于 0")
    private Double days;
    private String reason;
    private Long handoverEmployeeId;
    private String attachment;
}
