package com.company.hrms.common.org;

import lombok.Data;

/**
 * 跨模块只读部门摘要（AI 查数 / 人数卡片）。
 */
@Data
public class DeptBriefDTO {
    private Long departmentId;
    private String name;
    private Integer headcount;
    private Integer headcountIncludingSub;
}
