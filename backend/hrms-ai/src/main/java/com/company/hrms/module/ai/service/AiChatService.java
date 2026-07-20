package com.company.hrms.module.ai.service;

import com.company.hrms.common.security.LoginUser;
import com.company.hrms.common.security.SecurityUtils;
import com.company.hrms.module.ai.capability.CapabilityRegistry;
import com.company.hrms.module.ai.client.BailianClient;
import com.company.hrms.module.ai.client.QdrantClient;
import com.company.hrms.module.ai.config.AiProperties;
import com.company.hrms.module.ai.dto.AiCapabilitiesVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatService {

    private static final String SYSTEM_PROMPT = """
            你是 HRMS 智能助理。职责：
            1) 根据检索到的公司制度片段，用中文简明回答政策/制度问题；
            2) 可以指路到系统功能页，但禁止代用户提交请假、审批或其他业务操作；
            3) 若制度片段不足以回答，请明确说「未在知识库找到相关制度，请联系 HR」；
            4) 禁止编造路由 URL；禁止在对话中直接输出任何人工资数字/明细；
            5) 权限以「当前用户上下文」为准，不要仅凭知识库片段把用户误判为普通员工；
               - 若上下文写明可访问管理端薪资全量：应引导其前往账套/核算等管理页查看，不要拒绝；
               - 若上下文写明无薪资全量权限：礼貌说明原因（如 SYS_ADMIN 禁看薪资全量、员工仅可看本人工资条）；
            6) 不要输出 JSON，只输出对用户可见的自然语言回答。
            """;

    private static final String NO_API_KEY_TIP = """
            尚未配置阿里云百炼 API Key，智能问答暂时不可用。

            请在后端配置其一后重启：
            1) 环境变量 DASHSCOPE_API_KEY=你的Key
            2) 或 application-dev.yml 中 hrms.ai.api-key: 你的Key

            Key 在阿里云百炼控制台创建。配置完成后即可正常对话。
            """.stripIndent().trim();

    private final CapabilityRegistry capabilityRegistry;
    private final BailianClient bailianClient;
    private final QdrantClient qdrantClient;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    public AiCapabilitiesVO capabilities() {
        LoginUser user = SecurityUtils.requireLoginUser();
        AiCapabilitiesVO vo = new AiCapabilitiesVO();
        for (CapabilityRegistry.Capability c : capabilityRegistry.listAllowed(user)) {
            AiCapabilitiesVO.QuickPrompt qp = new AiCapabilitiesVO.QuickPrompt();
            qp.setIntent(c.getIntent());
            qp.setLabel(c.getLabel());
            qp.setPrompt(c.getQuickPrompt());
            vo.getQuickPrompts().add(qp);

            AiCapabilitiesVO.ActionItem action = new AiCapabilitiesVO.ActionItem();
            action.setIntent(c.getIntent());
            action.setLabel(c.getLabel());
            action.setRoute(c.getRoute());
            vo.getActions().add(action);
        }
        return vo;
    }

    public SseEmitter streamChat(String message) {
        LoginUser user = SecurityUtils.requireLoginUser();
        SseEmitter emitter = new SseEmitter(180_000L);
        executor.execute(() -> {
            try {
                List<CapabilityRegistry.Capability> detected = capabilityRegistry.detectIntents(message, user);
                List<CapabilityRegistry.Capability> allowedActions = new ArrayList<>();
                List<String> deniedLabels = new ArrayList<>();
                for (CapabilityRegistry.Capability c : detected) {
                    if (capabilityRegistry.hasAccess(user, c.getAccessKey())) {
                        allowedActions.add(c);
                    } else {
                        deniedLabels.add(c.getLabel());
                    }
                }

                // 未配 Key：立即给出明确提示，绝不去调百炼（避免长时间卡住）
                if (!aiProperties.hasApiKey()) {
                    StringBuilder tip = new StringBuilder(NO_API_KEY_TIP);
                    if (!allowedActions.isEmpty()) {
                        tip.append("\n\n不过根据你的问题，仍可直接前往：");
                        for (CapabilityRegistry.Capability c : allowedActions) {
                            tip.append(c.getLabel()).append("；");
                        }
                        tip.append("请点击下方按钮。");
                    } else if (!deniedLabels.isEmpty()) {
                        tip.append("\n\n另外：你提到的「")
                                .append(String.join("、", deniedLabels))
                                .append("」当前角色无权限，无法跳转。");
                    }
                    sendPlainReply(emitter, tip.toString(), allowedActions);
                    return;
                }

                List<QdrantClient.SearchHit> hits = List.of();
                try {
                    float[] qVec = bailianClient.embedOne(message);
                    hits = qdrantClient.search(qVec, 5);
                } catch (Exception e) {
                    log.warn("RAG retrieve skipped: {}", e.getMessage());
                }

                StringBuilder ctx = new StringBuilder();
                Map<Long, String> citations = new LinkedHashMap<>();
                for (QdrantClient.SearchHit hit : hits) {
                    citations.putIfAbsent(hit.docId(), hit.title());
                    ctx.append("【").append(hit.title()).append("】\n")
                            .append(hit.content()).append("\n\n");
                }

                boolean canPayroll = capabilityRegistry.hasAccess(user, "canViewPayroll");
                String roles = user.getRoles() == null || user.getRoles().isEmpty()
                        ? "未知"
                        : String.join(",", user.getRoles());

                StringBuilder userPrompt = new StringBuilder();
                userPrompt.append("当前用户上下文：\n")
                        .append("- 角色：").append(roles).append('\n')
                        .append("- 可访问管理端薪资全量（账套/核算）：")
                        .append(canPayroll ? "是" : "否").append('\n')
                        .append("- 说明：SYS_ADMIN 按制度不可见薪资全量；HR_STAFF/FINANCE/FINANCE_MANAGER 可以。\n\n");
                userPrompt.append("用户问题：").append(message).append("\n\n");
                if (!ctx.isEmpty()) {
                    userPrompt.append("相关制度片段（仅供参考，权限以用户上下文为准）：\n").append(ctx);
                } else {
                    userPrompt.append("（知识库未检索到相关片段）\n");
                }
                if (!deniedLabels.isEmpty() && allowedActions.isEmpty()) {
                    userPrompt.append("\n注意：用户可能想访问「")
                            .append(String.join("、", deniedLabels))
                            .append("」，但其当前角色无权限，请在回答中礼貌说明无权限，不要引导其越权。\n");
                }
                if (!allowedActions.isEmpty()) {
                    userPrompt.append("\n用户有权限，请在回答中明确告知可以前往，并提示点击下方按钮：");
                    for (CapabilityRegistry.Capability c : allowedActions) {
                        userPrompt.append(c.getLabel()).append("；");
                    }
                    userPrompt.append("（具体跳转按钮由系统附带，你不要编造 URL）\n");
                }

                bailianClient.chatStream(SYSTEM_PROMPT, userPrompt.toString(), delta -> {
                    try {
                        sendEvent(emitter, deltaEvent(delta));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });

                sendEvent(emitter, finalEvent(citations, allowedActions));
                sendEvent(emitter, doneEvent());
                emitter.complete();
            } catch (Exception e) {
                log.error("AI stream chat failed", e);
                try {
                    ObjectNode err = objectMapper.createObjectNode();
                    err.put("type", "error");
                    err.put("message", friendlyError(e));
                    sendEvent(emitter, err);
                    emitter.complete();
                } catch (Exception ex) {
                    emitter.completeWithError(e);
                }
            }
        });
        return emitter;
    }

    private void sendPlainReply(
            SseEmitter emitter,
            String text,
            List<CapabilityRegistry.Capability> actions) throws IOException {
        sendEvent(emitter, deltaEvent(text));
        sendEvent(emitter, finalEvent(Map.of(), actions));
        sendEvent(emitter, doneEvent());
        emitter.complete();
    }

    private String friendlyError(Exception e) {
        String msg = e.getMessage() == null ? "" : e.getMessage();
        if (msg.contains("API Key") || msg.contains("api-key") || msg.contains("DASHSCOPE")) {
            return NO_API_KEY_TIP;
        }
        return msg.isBlank() ? "对话失败，请稍后重试或检查百炼/Qdrant 配置。" : msg;
    }

    private ObjectNode deltaEvent(String content) {
        ObjectNode event = objectMapper.createObjectNode();
        event.put("type", "delta");
        event.put("content", content);
        return event;
    }

    private ObjectNode finalEvent(Map<Long, String> citations, List<CapabilityRegistry.Capability> actions) {
        ObjectNode finalEvent = objectMapper.createObjectNode();
        finalEvent.put("type", "final");
        ArrayNode citeArr = finalEvent.putArray("citations");
        citations.forEach((id, title) -> {
            ObjectNode c = citeArr.addObject();
            c.put("docId", id);
            c.put("title", title);
        });
        ArrayNode actionsArr = finalEvent.putArray("actions");
        for (CapabilityRegistry.Capability c : actions) {
            ObjectNode a = actionsArr.addObject();
            a.put("label", "前往" + c.getLabel());
            a.put("route", c.getRoute());
            a.put("intent", c.getIntent());
        }
        return finalEvent;
    }

    private ObjectNode doneEvent() {
        ObjectNode done = objectMapper.createObjectNode();
        done.put("type", "done");
        return done;
    }

    private void sendEvent(SseEmitter emitter, ObjectNode event) throws IOException {
        emitter.send(SseEmitter.event().name("message").data(event.toString()));
    }
}
