package com.company.hrms.module.org.dto;

import lombok.Data;

/** 部门在职人数聚合行 */
@Data
public class DeptHeadcountRow {
    private Long deptId;
    private Integer cnt;
}
