package com.company.hrms.common.approval;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

/**
 * 跨模块创建审批实例的入参（processType + businessId + 申请人 + 路由变量 formData）。
 */
@Data
public class CreateApprovalRequest {

    @NotNull
    private String processType;

    @NotNull
    private Long businessId;

    @NotNull
    private Long applicantId;

    @NotBlank
    private String title;

    private String businessSummary;

    private String businessUrl;

    private String applicantName;

    private String applicantDept;

    private String businessNo;

    /** SpEL / 路由变量，如 needSecondApproval、dailyTotalHours、leaveType、days */
    private Map<String, Object> formData;
}
