package com.company.hrms.workflow.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

public final class OnboardingDtos {
    private OnboardingDtos() {
    }

    @Data
    public static class OnboardingFormRequest {
        private String name;
        private String gender;
        private String mobile;
        private String email;
        private String idNumber;
        private LocalDate expectedOnboardDate;
        private Long departmentId;
        private Long positionId;
        private String employmentType;
        private Integer probationMonths;
        private BigDecimal probationSalaryRatio;
        private Long managerId;
        private BigDecimal baseSalary;
        /** 是否标准职位；false 触发二审 */
        private Boolean positionStandard;
        /** 职级薪资上限；缺省 20000 */
        private BigDecimal gradeMaxSalary;
    }

    @Data
    public static class OnboardingVO {
        private Long id;
        private Long instanceId;
        private String status;
        private String name;
        private String gender;
        private String mobile;
        private String email;
        private String idNumber;
        private LocalDate expectedOnboardDate;
        private Long departmentId;
        private Long positionId;
        private String employmentType;
        private Integer probationMonths;
        private BigDecimal probationSalaryRatio;
        private Long managerId;
        private BigDecimal baseSalary;
        private LocalDate actualOnboardDate;
        private Long employeeId;
        private Long createdBy;
        private String createdAt;
        private String updatedAt;
        /** 是否标准职位（影响是否二审） */
        private Boolean positionStandard;
        /** 职级薪资上限（二审阈值） */
        private BigDecimal gradeMaxSalary;
        /** 驳回原因（来自审批日志） */
        private String rejectReason;
    }

    @Data
    public static class OnboardingStatsVO {
        private long draft;
        private long pending;
        private long approvedPending;
        private long onboarded;
        private long rejected;
        private long abandoned;
    }

    @Data
    public static class OnboardingListResponse {
        private java.util.List<OnboardingVO> list;
        private long total;
        private int page;
        private int pageSize;
        private OnboardingStatsVO stats;
    }

    @Data
    public static class IdResponse {
        private Long id;
        private Map<String, Object> extra;
    }
}
