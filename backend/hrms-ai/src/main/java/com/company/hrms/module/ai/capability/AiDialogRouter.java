package com.company.hrms.module.ai.capability;

import com.company.hrms.module.ai.client.QdrantClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 对话分流：避免「句句硬套知识库」导致死板复读权限/制度文档。
 */
public final class AiDialogRouter {

    /** Cosine 相似度低于此值的片段视为弱相关，不注入 Prompt、不展示来源。 */
    public static final double MIN_RAG_SCORE = 0.52;

    public enum Mode {
        /** 已有本地办事卡片（请假/待办/查数等） */
        BIZ_CARD,
        /** 有跳转入口，轻量指路 */
        GUIDE,
        /** 制度/政策问答，可用强相关 RAG */
        POLICY,
        /** 闲聊/情绪/生活表达，先人情再可选办事 */
        CHITCHAT
    }

    private AiDialogRouter() {
    }

    public static Mode classify(
            String message,
            List<CapabilityRegistry.Capability> allowedActions,
            boolean canSubmitForm) {
        if (allowedActions != null && !allowedActions.isEmpty()) {
            boolean onlyBizCards = true;
            boolean hasGuide = false;
            for (CapabilityRegistry.Capability c : allowedActions) {
                if (c.isTaskList() || c.isInfoList() || c.isDataCard()) {
                    continue;
                }
                if (c.isFormSubmit() && canSubmitForm) {
                    continue;
                }
                if (CapabilityRegistry.ACTION_NAVIGATE.equals(c.getActionType())) {
                    hasGuide = true;
                    onlyBizCards = false;
                    continue;
                }
                onlyBizCards = false;
            }
            if (onlyBizCards) {
                return Mode.BIZ_CARD;
            }
            if (hasGuide || !allowedActions.isEmpty()) {
                // 有表单但无档案时也会落到 NAVIGATE
                boolean anyFormish = allowedActions.stream().anyMatch(CapabilityRegistry.Capability::isFormSubmit);
                if (anyFormish && canSubmitForm) {
                    return Mode.BIZ_CARD;
                }
                return Mode.GUIDE;
            }
        }
        if (looksLikeChitchat(message)) {
            return Mode.CHITCHAT;
        }
        return Mode.POLICY;
    }

    /** 过滤弱相关检索结果。 */
    public static List<QdrantClient.SearchHit> filterHits(List<QdrantClient.SearchHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }
        List<QdrantClient.SearchHit> out = new ArrayList<>();
        for (QdrantClient.SearchHit h : hits) {
            if (h != null && h.score() >= MIN_RAG_SCORE) {
                out.add(h);
            }
        }
        return out;
    }

    public static double maxScore(List<QdrantClient.SearchHit> hits) {
        double max = 0;
        if (hits == null) {
            return max;
        }
        for (QdrantClient.SearchHit h : hits) {
            if (h != null && h.score() > max) {
                max = h.score();
            }
        }
        return max;
    }

    /**
     * 短句、情绪/生活表达、非明确制度问法 → 闲聊模式（不强制灌知识库）。
     */
    public static boolean looksLikeChitchat(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String q = message.trim();
        if (q.length() <= 2) {
            return true;
        }
        // 明确制度/操作问法 → 非闲聊
        if (q.contains("制度") || q.contains("规定") || q.contains("政策") || q.contains("流程")
                || q.contains("怎么") || q.contains("如何") || q.contains("能否") || q.contains("可以吗")
                || q.contains("多少天") || q.contains("标准") || q.contains("条例")) {
            return false;
        }
        String lower = q.toLowerCase(Locale.ROOT);
        String[] lifeHints = {
                "生病", "不舒服", "难受", "累", "加班到", "心情", "郁闷", "压力",
                "家里", "有事", "出事", "住院", "感冒", "发烧", "咳嗽",
                "你好", "在吗", "谢谢", "拜拜", "早上好", "晚安",
                "好烦", "崩溃", "想哭", "倒霉", "完蛋"
        };
        for (String h : lifeHints) {
            if (lower.contains(h.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        // 极短且无业务关键词
        return q.length() <= 8 && !q.contains("请假") && !q.contains("审批") && !q.contains("工资")
                && !q.contains("花名册") && !q.contains("打卡") && !q.contains("加班");
    }

    public static String styleHint(Mode mode) {
        return switch (mode) {
            case BIZ_CARD -> "本轮已附带办事卡片：口语短答，引导看下方卡片即可，勿复述权限文档。";
            case GUIDE -> "用户在找系统入口：一两句说明怎么去，点下方按钮即可；禁止展开权限矩阵或长篇制度。";
            case POLICY -> """
                    仅当下方制度片段与问题真正相关时才引用；无关则忽略片段并正常回答。
                    回答简洁，不要整段粘贴文档；不要主动讲解角色权限体系。
                    """;
            case CHITCHAT -> """
                    这是日常/情绪向表达，不是制度考试：
                    - 先共情或自然回应（一两句），再视情况温和询问是否需要请假/打卡等办事；
                    - 不要检索式复读权限、数据范围、角色矩阵；
                    - 没有把握时宁可简短关心，也不要硬套知识库。
                    """;
        };
    }
}
