package com.company.hrms.common.roster;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class RosterQueryResult {
    private List<EmployeeBriefDTO> items = new ArrayList<>();
    private long total;
}
