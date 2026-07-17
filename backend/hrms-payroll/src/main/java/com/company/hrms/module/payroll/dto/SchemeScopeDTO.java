package com.company.hrms.module.payroll.dto;

import lombok.Data;
import java.util.List;

/* SchemeScopeDTO */
@Data
public class SchemeScopeDTO {
    private List<Long> departmentIds;
    private List<Long> positionIds;
    private List<String> jobLevels;
}
