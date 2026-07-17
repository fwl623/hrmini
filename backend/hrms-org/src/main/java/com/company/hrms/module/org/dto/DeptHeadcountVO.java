package com.company.hrms.module.org.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeptHeadcountVO {

    private Long departmentId;
    private Integer headcount;
    private Integer headcountIncludingSub;
}
