package com.company.hrms.module.org.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreatePositionRequest {

    @NotBlank(message = "职位名称不能为空")
    @Size(max = 64, message = "职位名称不超过 64 字符")
    private String name;

    @NotBlank(message = "职位序列不能为空")
    @Pattern(regexp = "^[MPS]$", message = "职位序列须为 M/P/S")
    private String sequenceCode;

    private Long departmentId;

    @NotBlank(message = "职级下限不能为空")
    private String gradeMin;

    @NotBlank(message = "职级上限不能为空")
    private String gradeMax;

    @NotNull(message = "默认试用期不能为空")
    @Min(value = 1, message = "默认试用期至少 1 个月")
    @Max(value = 12, message = "默认试用期不超过 12 个月")
    private Integer defaultProbationMonths;

    @NotNull(message = "是否标准职位不能为空")
    private Boolean isStandard;

    @Size(max = 512, message = "职位描述不超过 512 字符")
    private String description;
}
