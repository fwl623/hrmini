package com.company.hrms.module.ai.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 当前登录用户可见的 AI 能力清单：快捷提问 + 可跳转业务动作。
 */
@Data
public class AiCapabilitiesVO {
    private List<QuickPrompt> quickPrompts = new ArrayList<>();
    private List<ActionItem> actions = new ArrayList<>();

    /** 快捷提问项（前端一键填入输入框）。 */
    @Data
    public static class QuickPrompt {
        private String intent;
        private String label;
        private String prompt;
    }

    /** 可跳转业务页 / 办事动作。 */
    @Data
    public static class ActionItem {
        /** NAVIGATE | FORM_SUBMIT */
        private String type;
        private String label;
        private String route;
        private String intent;
    }
}
