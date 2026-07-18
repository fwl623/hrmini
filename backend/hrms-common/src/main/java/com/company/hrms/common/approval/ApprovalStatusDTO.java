package com.company.hrms.common.approval;

import lombok.Data;

@Data
public class ApprovalStatusDTO {

    private Long instanceId;
    private String status;
    private String currentNodeLabel;
}
