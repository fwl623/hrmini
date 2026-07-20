package com.company.hrms.workflow.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

public final class LifecycleDtos {
    private LifecycleDtos() {
    }

    @Data
    public static class RegularizationCreateRequest {
        private Long employeeId;
        private String performanceEvaluation;
        private BigDecimal salaryAdjustment;
        /** PASS / EXTEND / FAIL */
        private String approvalResult;
        /** EXTEND 时必填 */
        private Integer extendMonths;
    }

    @Data
    public static class RegularizationVO {
        private Long id;
        private Long employeeId;
        private String employeeName;
        private String empNo;
        private String status;
        private String approvalResult;
        private Integer extendMonths;
        private String performanceEvaluation;
        private BigDecimal salaryAdjustment;
        private String probationStartDate;
        private String probationEndDate;
        private Long instanceId;
        private String createdAt;
        /** FAIL 完成后引导：START_RESIGNATION */
        private String nextAction;
    }

    @Data
    public static class TransferCreateRequest {
        private Long employeeId;
        private Long newDepartmentId;
        private Long newPositionId;
        private String newJobLevel;
        private Long newManagerId;
        private BigDecimal salaryAdjustment;
        private String effectiveDate;
        private String reason;
    }

    @Data
    public static class TransferVO {
        private Long id;
        private Long employeeId;
        private String employeeName;
        private String status;
        private Long fromDepartmentId;
        private Long newDepartmentId;
        private Long newPositionId;
        private String newJobLevel;
        private Long newManagerId;
        private BigDecimal salaryAdjustment;
        private String effectiveDate;
        private String reason;
        private Long instanceId;
        private String currentNodeLabel;
        private List<NodeProgressVO> nodes;
        private String createdAt;
    }

    @Data
    public static class NodeProgressVO {
        private int order;
        private String label;
        private String status;
    }

    @Data
    public static class ResignationRequestCreate {
        private String expectedResignDate;
        private String reasonCategory;
        private String resignationType;
        private String reasonDetail;
    }

    @Data
    public static class ResignationRequestVO {
        private Long id;
        private Long employeeId;
        private String employeeName;
        private String status;
        private String expectedResignDate;
        private String reasonCategory;
        private String resignationType;
        private String reasonDetail;
        private Long instanceId;
        private String createdAt;
    }

    @Data
    public static class ResignationCreateRequest {
        private Long employeeId;
        private Long requestId;
        private String resignationDate;
        private String reasonCategory;
        private String resignationType;
        private String reasonDetail;
        private Long handoverEmployeeId;
    }

    @Data
    public static class ResignationVO {
        private Long id;
        private Long requestId;
        private Long employeeId;
        private String employeeName;
        private String status;
        private String resignationDate;
        private String reasonCategory;
        private String resignationType;
        private String reasonDetail;
        private Long handoverEmployeeId;
        private Long instanceId;
        private String createdAt;
    }

    @Data
    public static class ResignationStatsVO {
        private long pendingRequest;
        private long approving;
        private long pendingResign;
        private long resignedThisMonth;
    }
}
