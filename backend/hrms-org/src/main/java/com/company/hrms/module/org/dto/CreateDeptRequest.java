package com.company.hrms.module.org.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateDeptRequest {

    @NotBlank(message = "部门名称不能为空")
    @Size(max = 64, message = "部门名称不超过 64 字符")
    private String name;

    @NotBlank(message = "部门编码不能为空")
    @Pattern(regexp = "^[A-Za-z0-9]{2}$", message = "部门编码须为 2 位字母或数字")
    private String deptCode;

    private Long parentId;

    private Long headEmployeeId;

    @NotNull(message = "排序序号不能为空")
    private Integer sortOrder;

    @Size(max = 256, message = "部门描述不超过 256 字符")
    private String description;
}
