package com.company.hrms.module.org.dto;

import lombok.Data;

/** 职位在职人数聚合行 */
@Data
public class PositionHeadcountRow {
    private Long positionId;
    private Integer cnt;
}
