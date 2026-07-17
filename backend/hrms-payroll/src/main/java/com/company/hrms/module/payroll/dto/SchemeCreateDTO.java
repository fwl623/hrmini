package com.company.hrms.module.payroll.dto;

import lombok.Data;
import java.util.List;

@Data
public class SchemeCreateDTO {
    private String name;
    private String description;
    private String effectiveDate;
    private String status;
    private SchemeScopeDTO scope;
    private List<SchemeItemDTO> items;
}
