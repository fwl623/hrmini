package com.company.hrms.module.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiChatRequest {
    @NotBlank
    private String message;
    /** 可选会话 id，V1 仅透传不计持久化 */
    private String conversationId;
}
