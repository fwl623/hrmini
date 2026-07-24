package com.company.hrms.common.approval;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建审批实例的出参：实例 ID 与初始状态（如 PENDING）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateApprovalResult {

    private Long instanceId;
    private String status;
}
