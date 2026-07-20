package com.company.hrms.module.ai.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AiCapabilitiesVO {
    private List<QuickPrompt> quickPrompts = new ArrayList<>();
    private List<ActionItem> actions = new ArrayList<>();

    @Data
    public static class QuickPrompt {
        private String intent;
        private String label;
        private String prompt;
    }

    @Data
    public static class ActionItem {
        private String label;
        private String route;
        private String intent;
    }
}
