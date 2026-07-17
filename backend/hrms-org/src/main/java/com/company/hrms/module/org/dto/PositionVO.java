package com.company.hrms.module.org.dto;

import lombok.Data;

@Data
public class PositionVO {

    private Long id;
    private String name;
    private String sequenceCode;
    private Long departmentId;
    private String gradeMin;
    private String gradeMax;
    private Integer defaultProbationMonths;
    private Boolean isStandard;
    private String description;
    /**
     * 归属本职位人数：试用+正式+待离职（10/20/30），不含已离职(40)。
     * 字段名沿用 activeCount（契约兼容），语义为「归属」而非 PRD 狭义在职。
     */
    private Integer activeCount;
}
