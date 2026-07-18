package com.company.hrms.common.approval;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateApprovalResult {

    private Long instanceId;
    private String status;
}
