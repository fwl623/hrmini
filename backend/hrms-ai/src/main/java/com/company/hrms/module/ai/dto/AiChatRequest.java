package com.company.hrms.module.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 流式对话请求体：用户问题；conversationId 预留，V1 不持久化多轮。
 */
@Data
public class AiChatRequest {
    @NotBlank
    private String message;
    /** 可选会话 id，V1 仅透传不计持久化 */
    private String conversationId;
}
