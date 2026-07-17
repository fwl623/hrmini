package com.company.hrms.workflow.core;

import com.company.hrms.workflow.enums.ApprovalAction;
import com.company.hrms.workflow.enums.ApprovalStatus;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 入转调离业务状态机（纯 Java，无 Spring 注解）。
 * <p>
 * Day1：仅校验合法迁移并返回下一状态码；持久化由上层 Service 负责。
 */
public final class ApprovalStateMachine {

    public enum ProcessType {
        ONBOARDING,
        REGULARIZATION,
        TRANSFER,
        RESIGNATION
    }

    private ApprovalStateMachine() {
    }

    /**
     * @param processType 流程类型
     * @param currentStatus 当前状态码（任务示例风格）
     * @param action 操作
     * @return 迁移后的状态码
     */
    public static String transit(ProcessType processType, String currentStatus, ApprovalAction action) {
        Objects.requireNonNull(processType, "processType");
        Objects.requireNonNull(currentStatus, "currentStatus");
        Objects.requireNonNull(action, "action");

        return switch (processType) {
            case ONBOARDING -> transitOnboarding(currentStatus, action);
            case REGULARIZATION -> transitRegularization(currentStatus, action);
            case TRANSFER -> transitTransfer(currentStatus, action);
            case RESIGNATION -> transitResignation(currentStatus, action);
        };
    }

    public static boolean canTransit(ProcessType processType, String currentStatus, ApprovalAction action) {
        try {
            transit(processType, currentStatus, action);
            return true;
        } catch (IllegalStateException | IllegalArgumentException ex) {
            return false;
        }
    }

    // —— 入职：draft→pending→approved_pending→onboarded / rejected / abandoned ——

    private static String transitOnboarding(String current, ApprovalAction action) {
        ApprovalStatus.Onboarding from = ApprovalStatus.Onboarding.fromCode(current);
        Map<ApprovalStatus.Onboarding, Map<ApprovalAction, ApprovalStatus.Onboarding>> graph = onboardingGraph();
        ApprovalStatus.Onboarding next = requireEdge(graph, from, action, "ONBOARDING");
        return next.code();
    }

    private static Map<ApprovalStatus.Onboarding, Map<ApprovalAction, ApprovalStatus.Onboarding>> onboardingGraph() {
        Map<ApprovalStatus.Onboarding, Map<ApprovalAction, ApprovalStatus.Onboarding>> g = new EnumMap<>(ApprovalStatus.Onboarding.class);
        g.put(ApprovalStatus.Onboarding.DRAFT, Map.of(
                ApprovalAction.SUBMIT, ApprovalStatus.Onboarding.PENDING,
                ApprovalAction.ABANDON, ApprovalStatus.Onboarding.ABANDONED
        ));
        g.put(ApprovalStatus.Onboarding.PENDING, Map.of(
                ApprovalAction.APPROVE, ApprovalStatus.Onboarding.APPROVED_PENDING,
                ApprovalAction.REJECT, ApprovalStatus.Onboarding.REJECTED,
                ApprovalAction.WITHDRAW, ApprovalStatus.Onboarding.DRAFT,
                ApprovalAction.FORWARD, ApprovalStatus.Onboarding.PENDING,
                ApprovalAction.ABANDON, ApprovalStatus.Onboarding.ABANDONED
        ));
        g.put(ApprovalStatus.Onboarding.APPROVED_PENDING, Map.of(
                ApprovalAction.APPROVE, ApprovalStatus.Onboarding.ONBOARDED, // confirm 语义
                ApprovalAction.ABANDON, ApprovalStatus.Onboarding.ABANDONED
        ));
        g.put(ApprovalStatus.Onboarding.REJECTED, Map.of(
                ApprovalAction.SUBMIT, ApprovalStatus.Onboarding.PENDING,
                ApprovalAction.ABANDON, ApprovalStatus.Onboarding.ABANDONED
        ));
        // terminal: ONBOARDED / ABANDONED
        g.put(ApprovalStatus.Onboarding.ONBOARDED, Map.of());
        g.put(ApprovalStatus.Onboarding.ABANDONED, Map.of());
        return g;
    }

    // —— 转正：pending→PASS/EXTEND/FAIL ——

    private static String transitRegularization(String current, ApprovalAction action) {
        ApprovalStatus.Regularization from = ApprovalStatus.Regularization.fromCode(current);
        if (from != ApprovalStatus.Regularization.PENDING) {
            throw illegal("REGULARIZATION", current, action);
        }
        if (action == ApprovalAction.REJECT) {
            return ApprovalStatus.Regularization.FAIL.code();
        }
        if (action != ApprovalAction.APPROVE) {
            throw illegal("REGULARIZATION", current, action);
        }
        // APPROVE 需上层根据 approvalResult 指定终点；默认 PASS，也支持 current 已是目标时直返
        return ApprovalStatus.Regularization.PASS.code();
    }

    /**
     * 转正审批通过时，按表单结果落到 PASS / EXTEND / FAIL。
     */
    public static String finalizeRegularization(ApprovalStatus.Regularization result) {
        if (result == null || result == ApprovalStatus.Regularization.PENDING) {
            throw new IllegalArgumentException("Regularization result must be PASS/EXTEND/FAIL");
        }
        return result.code();
    }

    // —— 调岗：pending→三节点→APPROVED ——

    private static String transitTransfer(String current, ApprovalAction action) {
        ApprovalStatus.Transfer from = ApprovalStatus.Transfer.fromCode(current);
        if (action == ApprovalAction.REJECT) {
            if (Set.of(
                    ApprovalStatus.Transfer.PENDING,
                    ApprovalStatus.Transfer.NODE_1,
                    ApprovalStatus.Transfer.NODE_2,
                    ApprovalStatus.Transfer.NODE_3
            ).contains(from)) {
                return ApprovalStatus.Transfer.REJECTED.code();
            }
            throw illegal("TRANSFER", current, action);
        }
        if (action == ApprovalAction.FORWARD) {
            if (from == ApprovalStatus.Transfer.APPROVED || from == ApprovalStatus.Transfer.REJECTED) {
                throw illegal("TRANSFER", current, action);
            }
            return from.code();
        }
        if (action != ApprovalAction.APPROVE && action != ApprovalAction.SUBMIT) {
            throw illegal("TRANSFER", current, action);
        }
        return switch (from) {
            case PENDING -> ApprovalStatus.Transfer.NODE_1.code();
            case NODE_1 -> ApprovalStatus.Transfer.NODE_2.code();
            case NODE_2 -> ApprovalStatus.Transfer.NODE_3.code();
            case NODE_3 -> ApprovalStatus.Transfer.APPROVED.code();
            default -> throw illegal("TRANSFER", current, action);
        };
    }

    // —— 离职：pending→APPROVED→PENDING_RESIGN→RESIGNED ——

    private static String transitResignation(String current, ApprovalAction action) {
        ApprovalStatus.Resignation from = ApprovalStatus.Resignation.fromCode(current);
        return switch (from) {
            case PENDING -> {
                if (action == ApprovalAction.APPROVE) {
                    yield ApprovalStatus.Resignation.APPROVED.code();
                }
                if (action == ApprovalAction.REJECT) {
                    yield ApprovalStatus.Resignation.REJECTED.code();
                }
                if (action == ApprovalAction.FORWARD) {
                    yield from.code();
                }
                throw illegal("RESIGNATION", current, action);
            }
            case APPROVED -> {
                // 生效日未到：进入待离职
                if (action == ApprovalAction.APPROVE || action == ApprovalAction.SUBMIT) {
                    yield ApprovalStatus.Resignation.PENDING_RESIGN.code();
                }
                throw illegal("RESIGNATION", current, action);
            }
            case PENDING_RESIGN -> {
                if (action == ApprovalAction.APPROVE) {
                    yield ApprovalStatus.Resignation.RESIGNED.code();
                }
                throw illegal("RESIGNATION", current, action);
            }
            case REJECTED, RESIGNED -> throw illegal("RESIGNATION", current, action);
        };
    }

    private static <S extends Enum<S>> S requireEdge(
            Map<S, Map<ApprovalAction, S>> graph,
            S from,
            ApprovalAction action,
            String process
    ) {
        Map<ApprovalAction, S> edges = graph.getOrDefault(from, Map.of());
        S next = edges.get(action);
        if (next == null) {
            throw illegal(process, from.name(), action);
        }
        return next;
    }

    private static IllegalStateException illegal(String process, String current, ApprovalAction action) {
        return new IllegalStateException(
                "Illegal transition: process=" + process + ", status=" + current + ", action=" + action);
    }
}
