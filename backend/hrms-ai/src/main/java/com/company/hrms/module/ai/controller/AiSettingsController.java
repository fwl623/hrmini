package com.company.hrms.module.ai.controller;

import com.company.hrms.common.web.Result;
import com.company.hrms.module.ai.config.AiRuntimeSettings;
import com.company.hrms.module.ai.service.AiKnowledgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 运行时参数（知识库管理权限）：分块、RAG topK、温度、对话模型等。
 */
@RestController
@RequestMapping("/ai/settings")
@RequiredArgsConstructor
public class AiSettingsController {

    private final AiRuntimeSettings runtimeSettings;
    private final AiKnowledgeService knowledgeService;

    @GetMapping
    public Result<AiRuntimeSettings.Snapshot> get() {
        knowledgeService.requireKnowledgeManage();
        return Result.success(runtimeSettings.snapshot());
    }

    @PutMapping
    public Result<AiRuntimeSettings.Snapshot> update(@RequestBody AiRuntimeSettings.UpdateRequest body) {
        knowledgeService.requireKnowledgeManage();
        return Result.success(runtimeSettings.update(body == null ? new AiRuntimeSettings.UpdateRequest() : body));
    }
}
