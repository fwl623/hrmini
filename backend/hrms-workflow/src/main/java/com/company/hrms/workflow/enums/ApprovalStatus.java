package com.company.hrms.workflow.enums;

import java.util.Arrays;

/**
 * 各业务状态枚举（Day1 按任务示例：API 风格小写字符串；
 * Entity 持久化仍为 String，后续可与 DB 大写码做映射）。
 */
public final class ApprovalStatus {

    private ApprovalStatus() {
    }

    public enum Onboarding {
        DRAFT("draft"),
        PENDING("pending"),
        APPROVED_PENDING("approved_pending"),
        ONBOARDED("onboarded"),
        REJECTED("rejected"),
        ABANDONED("abandoned");

        private final String code;

        Onboarding(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }

        public static Onboarding fromCode(String code) {
            return Arrays.stream(values())
                    .filter(v -> v.code.equalsIgnoreCase(code))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown onboarding status: " + code));
        }
    }

    /** 转正结果 / 流转终点（任务示例：pending → PASS/EXTEND/FAIL） */
    public enum Regularization {
        PENDING("pending"),
        PASS("PASS"),
        EXTEND("EXTEND"),
        FAIL("FAIL");

        private final String code;

        Regularization(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }

        public static Regularization fromCode(String code) {
            return Arrays.stream(values())
                    .filter(v -> v.code.equalsIgnoreCase(code))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown regularization status: " + code));
        }
    }

    /** 调岗：pending → 三节点推进 → APPROVED */
    public enum Transfer {
        PENDING("pending"),
        NODE_1("node_1"),
        NODE_2("node_2"),
        NODE_3("node_3"),
        APPROVED("APPROVED"),
        REJECTED("rejected");

        private final String code;

        Transfer(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }

        public static Transfer fromCode(String code) {
            return Arrays.stream(values())
                    .filter(v -> v.code.equalsIgnoreCase(code))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown transfer status: " + code));
        }
    }

    /** 离职：pending → APPROVED → PENDING_RESIGN → RESIGNED */
    public enum Resignation {
        PENDING("pending"),
        APPROVED("APPROVED"),
        PENDING_RESIGN("PENDING_RESIGN"),
        RESIGNED("RESIGNED"),
        REJECTED("rejected");

        private final String code;

        Resignation(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }

        public static Resignation fromCode(String code) {
            return Arrays.stream(values())
                    .filter(v -> v.code.equalsIgnoreCase(code))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown resignation status: " + code));
        }
    }

    /** 审批任务状态（表 approval_task.status） */
    public enum Task {
        PENDING("pending"),
        APPROVED("approved"),
        REJECTED("rejected"),
        FORWARDED("forwarded"),
        CANCELLED("cancelled");

        private final String code;

        Task(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }
    }
}
