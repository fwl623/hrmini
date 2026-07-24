package com.company.hrms.module.ai.controller;

import com.company.hrms.common.web.Result;
import com.company.hrms.module.ai.dto.AiCapabilitiesVO;
import com.company.hrms.module.ai.dto.AiChatRequest;
import com.company.hrms.module.ai.service.AiChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI 对话接口：查询当前用户可用快捷能力，以及 SSE 流式聊天。
 */
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiChatController {

    private final AiChatService aiChatService;

    @GetMapping("/capabilities")
    public Result<AiCapabilitiesVO> capabilities() {
        return Result.success(aiChatService.capabilities());
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody AiChatRequest request) {
        return aiChatService.streamChat(request.getMessage());
    }
}
