package com.company.hrms.module.ai.controller;

import com.company.hrms.common.web.Result;
import com.company.hrms.module.ai.entity.AiKnowledgeDoc;
import com.company.hrms.module.ai.service.AiKnowledgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * AI 知识库管理接口：制度文档列表、上传入库、启停、删除（需 ai:knowledge:manage）。
 */
@RestController
@RequestMapping("/ai/knowledge")
@RequiredArgsConstructor
public class AiKnowledgeController {

    private final AiKnowledgeService knowledgeService;

    @GetMapping("/docs")
    public Result<List<AiKnowledgeDoc>> list() {
        return Result.success(knowledgeService.listDocs());
    }

    @PostMapping("/docs")
    public Result<AiKnowledgeDoc> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title) {
        return Result.success(knowledgeService.upload(file, title));
    }

    @PutMapping("/docs/{id}/enabled")
    public Result<Void> enabled(@PathVariable Long id, @RequestParam("enabled") boolean enabled) {
        knowledgeService.setEnabled(id, enabled);
        return Result.success();
    }

    @DeleteMapping("/docs/{id}")
    public Result<Map<String, Object>> delete(@PathVariable Long id) {
        knowledgeService.delete(id);
        return Result.success(Map.of("deleted", true));
    }
}
