package com.company.hrms.module.org.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeptCanDeleteVO {

    private Boolean canDelete;
    private Boolean hasChildren;
    /**
     * 字段名沿用契约；实际为归属人数（含待离职 30，不含已离职 40），非 PRD「在职」口径。
     */
    private Integer activeEmployeeCount;
    private String reason;
}
