package com.company.hrms.module.org.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MergeDeptRequest {

    @NotNull(message = "目标部门不能为空")
    private Long targetDepartmentId;
}
