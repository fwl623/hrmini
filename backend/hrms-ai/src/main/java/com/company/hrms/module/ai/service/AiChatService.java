package com.company.hrms.module.ai.service;

import com.company.hrms.common.approval.ApprovalEngineService;
import com.company.hrms.common.approval.PendingApprovalTaskDTO;
import com.company.hrms.common.leave.LeaveBalanceBriefDTO;
import com.company.hrms.common.leave.LeaveBalanceQueryService;
import com.company.hrms.common.org.DeptBriefDTO;
import com.company.hrms.common.org.OrgLookupService;
import com.company.hrms.common.roster.RosterQueryRequest;
import com.company.hrms.common.roster.RosterQueryResult;
import com.company.hrms.common.roster.RosterQueryService;
import com.company.hrms.common.security.LoginUser;
import com.company.hrms.common.security.SecurityUtils;
import com.company.hrms.module.ai.capability.AiDialogRouter;
import com.company.hrms.module.ai.capability.AiQuerySlotExtractor;
import com.company.hrms.module.ai.capability.ApprovalTodoActionFactory;
import com.company.hrms.module.ai.capability.CapabilityRegistry;
import com.company.hrms.module.ai.capability.DeptStatsActionFactory;
import com.company.hrms.module.ai.capability.EmployeeRosterActionFactory;
import com.company.hrms.module.ai.capability.LeaveBalanceActionFactory;
import com.company.hrms.module.ai.capability.LeaveBizActionFactory;
import com.company.hrms.module.ai.capability.OvertimeBizActionFactory;
import com.company.hrms.module.ai.client.BailianClient;
import com.company.hrms.module.ai.client.QdrantClient;
import com.company.hrms.module.ai.config.AiProperties;
import com.company.hrms.module.ai.config.AiRuntimeSettings;
import com.company.hrms.module.ai.dto.AiCapabilitiesVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AI 对话编排：意图检测 → RAG → 流式回答 → SSE 推送引用与办事/跳转/待办卡片。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatService {

    private static final String SYSTEM_PROMPT = """
            你是 HRMS 智能助理「小R」。
            - 语气自然、简短、有人情味；先回应用户，再办事或答制度。
            - 制度问答才依据「相关制度片段」；片段无关或未提供时不要硬套，更不要复读权限矩阵。
            - 有办事/跳转卡片时，引导用户看下方操作；未确认前禁止声称已提交。
            - 禁止编造 URL；禁止输出任何人工资数字/明细；不要输出 JSON。
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
    private final AiRuntimeSettings runtimeSettings;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<ApprovalEngineService> approvalEngineService;
    private final ObjectProvider<RosterQueryService> rosterQueryService;
    private final ObjectProvider<OrgLookupService> orgLookupService;
    private final ObjectProvider<LeaveBalanceQueryService> leaveBalanceQueryService;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    /** 短轮次补槽：用户刚问名单但未给清部门名时，记下意图，下次只回「后端开发部」也能查。 */
    private final ConcurrentHashMap<Long, PendingSlot> pendingSlots = new ConcurrentHashMap<>();

    private record PendingSlot(String intent, long expireAtMs) {
        boolean alive() {
            return System.currentTimeMillis() < expireAtMs;
        }
    }

    private void rememberPending(LoginUser user, String intent) {
        if (user == null || user.getUserId() == null || intent == null) {
            return;
        }
        pendingSlots.put(user.getUserId(), new PendingSlot(intent, System.currentTimeMillis() + 10 * 60_000L));
    }

    private void clearPending(LoginUser user) {
        if (user != null && user.getUserId() != null) {
            pendingSlots.remove(user.getUserId());
        }
    }

    /**
     * 补全意图：1) 上一轮待补部门槽；2) 整句像部门名且能解析到部门 → 当作查名单。
     */
    private void enrichActionsWithContext(
            List<CapabilityRegistry.Capability> allowedActions,
            String message,
            LoginUser user) {
        if (user == null || !capabilityRegistry.hasAccess(user, "canViewEmployee")) {
            return;
        }
        if (!allowedActions.isEmpty()) {
            return;
        }

        Long uid = user.getUserId();
        PendingSlot pending = uid == null ? null : pendingSlots.get(uid);
        if (pending != null && !pending.alive()) {
            pendingSlots.remove(uid);
            pending = null;
        }

        boolean bareDept = AiQuerySlotExtractor.isBareDeptUtterance(message);
        if (pending != null && bareDept) {
            CapabilityRegistry.Capability cap = capabilityRegistry.findByIntent(pending.intent());
            if (cap != null && capabilityRegistry.hasAccess(user, cap.getAccessKey())) {
                allowedActions.add(cap);
                return;
            }
        }

        if (!bareDept) {
            return;
        }
        OrgLookupService org = orgLookupService.getIfAvailable();
        if (org == null) {
            return;
        }
        try {
            List<DeptBriefDTO> matched = org.resolveDepartmentsByNameHint(message.trim(), 3);
            if (!matched.isEmpty()) {
                CapabilityRegistry.Capability cap = capabilityRegistry.findByIntent("employee_list");
                if (cap != null) {
                    allowedActions.add(cap);
                }
            }
        } catch (Exception e) {
            log.debug("bare dept resolve skipped: {}", e.getMessage());
        }
    }

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
            action.setType(c.getActionType());
            vo.getActions().add(action);
        }
        return vo;
    }

    public SseEmitter streamChat(String message) {
        LoginUser user = SecurityUtils.requireLoginUser();
        SseEmitter emitter = new SseEmitter(180_000L);
        // SecurityUtils 基于 ThreadLocal，异步线程必须重新写入，否则花名册/组织 SPI 鉴权会空指针式失败
        executor.execute(() -> {
            SecurityUtils.setLoginUser(user);
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
                enrichActionsWithContext(allowedActions, message, user);

                if (!aiProperties.hasApiKey()) {
                    sendPlainReply(emitter, offlineTip(allowedActions, deniedLabels, false),
                            allowedActions, message, user);
                    return;
                }

                // 纯办事意图：本地卡片，不调百炼
                if (isLocalBizOnly(allowedActions, user)) {
                    sendPlainReply(emitter, localBizTip(allowedActions, user, message),
                            allowedActions, message, user);
                    return;
                }

                boolean canForm = capabilityRegistry.canSubmitBizForm(user);
                AiDialogRouter.Mode mode = AiDialogRouter.classify(message, allowedActions, canForm);

                List<QdrantClient.SearchHit> hits = List.of();
                // 闲聊不灌知识库；指路/制度才检索，并过滤弱相关
                if (mode == AiDialogRouter.Mode.POLICY || mode == AiDialogRouter.Mode.GUIDE) {
                    try {
                        float[] qVec = bailianClient.embedOne(message);
                        hits = AiDialogRouter.filterHits(
                                qdrantClient.search(qVec, runtimeSettings.getTopK()));
                    } catch (Exception e) {
                        log.warn("RAG retrieve skipped: {}", e.getMessage());
                    }
                    // 检索全是弱相关 → 按闲聊处理，避免硬套权限文档
                    if (mode == AiDialogRouter.Mode.POLICY && hits.isEmpty()) {
                        mode = AiDialogRouter.Mode.CHITCHAT;
                    }
                }

                StringBuilder ctx = new StringBuilder();
                Map<Long, String> citations = new LinkedHashMap<>();
                if (mode == AiDialogRouter.Mode.POLICY || mode == AiDialogRouter.Mode.GUIDE) {
                    for (QdrantClient.SearchHit hit : hits) {
                        citations.putIfAbsent(hit.docId(), hit.title());
                        ctx.append("【").append(hit.title()).append("】\n")
                                .append(hit.content()).append("\n\n");
                    }
                }

                StringBuilder userPrompt = new StringBuilder();
                userPrompt.append(AiDialogRouter.styleHint(mode)).append('\n');
                // 闲聊少塞角色权限上下文，减少模型去「讲权限」
                if (mode != AiDialogRouter.Mode.CHITCHAT) {
                    boolean canPayroll = capabilityRegistry.hasAccess(user, "canViewPayroll");
                    String roles = user.getRoles() == null || user.getRoles().isEmpty()
                            ? "未知"
                            : String.join(",", user.getRoles());
                    userPrompt.append("当前用户上下文：\n")
                            .append("- 角色：").append(roles).append('\n')
                            .append("- 员工档案：")
                            .append(user.getEmployeeId() != null ? "已绑定" : "未绑定").append('\n')
                            .append("- 可审批：")
                            .append(capabilityRegistry.hasAccess(user, "canApprove") ? "是" : "否").append('\n')
                            .append("- 可访问管理端薪资全量：")
                            .append(canPayroll ? "是" : "否").append('\n');
                } else {
                    userPrompt.append("用户已登录 HRMS；回答时不要展开其角色权限说明。\n");
                }
                userPrompt.append("用户问题：").append(message).append("\n\n");
                if (!ctx.isEmpty()) {
                    userPrompt.append("相关制度片段（仅供参考，无关请忽略）：\n").append(ctx);
                } else if (mode == AiDialogRouter.Mode.POLICY) {
                    userPrompt.append("（知识库无高相关片段）\n");
                }
                if (!deniedLabels.isEmpty() && allowedActions.isEmpty()) {
                    userPrompt.append("\n注意：用户可能想访问「")
                            .append(String.join("、", deniedLabels))
                            .append("」，但无权限，请礼貌简短说明。\n");
                }
                if (!allowedActions.isEmpty()) {
                    boolean hasForm = allowedActions.stream().anyMatch(CapabilityRegistry.Capability::isFormSubmit)
                            && canForm;
                    boolean hasTodo = allowedActions.stream().anyMatch(CapabilityRegistry.Capability::isTaskList);
                    boolean hasInfo = allowedActions.stream().anyMatch(CapabilityRegistry.Capability::isInfoList);
                    boolean hasStats = allowedActions.stream().anyMatch(CapabilityRegistry.Capability::isDataCard);
                    if (hasTodo) {
                        userPrompt.append("\n系统将附带「我的待审批」列表卡片，请引导用户在卡片内通过或驳回；");
                    } else if (hasInfo) {
                        userPrompt.append("\n系统将附带员工名单卡片，请引导用户查看下方列表，勿编造人员；");
                    } else if (hasStats) {
                        boolean leaveBal = allowedActions.stream()
                                .anyMatch(c -> "leave_balance".equals(c.getIntent()));
                        if (leaveBal) {
                            userPrompt.append("\n系统将附带假期余额卡片，请直接告知剩余天数并引导看下方卡片，勿编造额度；");
                        } else {
                            userPrompt.append("\n系统将附带人数统计卡片，请引导用户查看下方数据，勿编造人数；");
                        }
                    } else if (hasForm) {
                        userPrompt.append("\n系统将附带办事表单：先简短回应用户情绪/诉求，再引导填下方卡片；不要讲权限长文。可办理：");
                    } else {
                        userPrompt.append("\n请一两句提示用户点下方按钮前往：");
                    }
                    for (CapabilityRegistry.Capability c : allowedActions) {
                        userPrompt.append(c.getLabel()).append("；");
                    }
                    userPrompt.append('\n');
                } else if (mode == AiDialogRouter.Mode.CHITCHAT && canForm) {
                    userPrompt.append("\n若用户流露身体不适、家里有事、太累等，可温和问一句要不要请假，"
                            + "并提示可说「我要请假」；不要主动讲权限。\n");
                }

                try {
                    bailianClient.chatStream(SYSTEM_PROMPT, userPrompt.toString(), delta -> {
                        try {
                            sendEvent(emitter, deltaEvent(delta));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
                    sendEvent(emitter, finalEvent(citations, allowedActions, message, user));
                    sendEvent(emitter, doneEvent());
                    emitter.complete();
                } catch (Exception chatEx) {
                    log.warn("Bailian chat failed, degrade with actions if any: {}", chatEx.getMessage());
                    if (!allowedActions.isEmpty()) {
                        sendPlainReply(emitter,
                                offlineTip(allowedActions, deniedLabels, true),
                                allowedActions, message, user);
                    } else {
                        throw chatEx;
                    }
                }
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
            } finally {
                SecurityUtils.clear();
            }
        });
        return emitter;
    }

    /**
     * 是否仅命中「本地可完成」的办事/查数卡片，无需调用百炼。
     */
    private boolean isLocalBizOnly(List<CapabilityRegistry.Capability> actions, LoginUser user) {
        if (actions == null || actions.isEmpty()) {
            return false;
        }
        boolean canForm = capabilityRegistry.canSubmitBizForm(user);
        for (CapabilityRegistry.Capability c : actions) {
            if (c.isTaskList() || c.isInfoList() || c.isDataCard()) {
                continue;
            }
            if (c.isFormSubmit() && canForm
                    && ("leave".equals(c.getIntent()) || "overtime".equals(c.getIntent()))) {
                continue;
            }
            return false;
        }
        return true;
    }

    private String localBizTip(List<CapabilityRegistry.Capability> actions, LoginUser user, String message) {
        boolean hasTodo = actions.stream().anyMatch(CapabilityRegistry.Capability::isTaskList);
        boolean hasInfo = actions.stream().anyMatch(CapabilityRegistry.Capability::isInfoList);
        boolean hasStats = actions.stream().anyMatch(CapabilityRegistry.Capability::isDataCard);
        boolean hasForm = actions.stream().anyMatch(CapabilityRegistry.Capability::isFormSubmit)
                && capabilityRegistry.canSubmitBizForm(user);
        if (hasTodo) {
            return "已为你列出「我的待审批」。可在下方卡片直接通过或驳回（驳回须填写意见）。";
        }
        if (hasInfo) {
            return "已按你的权限查询花名册，结果见下方名单卡片（只读）。";
        }
        if (hasStats) {
            boolean leaveBal = actions.stream().anyMatch(c -> "leave_balance".equals(c.getIntent()));
            if (leaveBal) {
                return "已查询你的假期余额，见下方卡片（只读）。若要请假请说「我要请假」。";
            }
            return "人数统计见下方卡片（只读，以系统花名册口径为准）。";
        }
        if (hasForm) {
            boolean leave = actions.stream().anyMatch(c -> "leave".equals(c.getIntent()));
            String q = message == null ? "" : message;
            if (leave && (q.contains("生病") || q.contains("不舒服") || q.contains("感冒")
                    || q.contains("发烧") || q.contains("身体不适"))) {
                return "先好好休息，别硬扛。需要请假的话，我已帮你打开病假申请卡片，填好确认即可提交；"
                        + "病假通常要上传证明材料，按卡片提示补充就行。";
            }
            String labels = actions.stream()
                    .filter(CapabilityRegistry.Capability::isFormSubmit)
                    .map(CapabilityRegistry.Capability::getLabel)
                    .reduce((a, b) -> a + "、" + b)
                    .orElse("办事");
            return "请在下方填写「" + labels + "」并确认提交；未确认前不会真正发起申请。";
        }
        return "请使用下方入口继续办理。";
    }

    private String offlineTip(
            List<CapabilityRegistry.Capability> allowedActions,
            List<String> deniedLabels,
            boolean modelUnavailable) {
        StringBuilder tip = new StringBuilder();
        if (modelUnavailable) {
            tip.append("智能问答暂时连不上模型服务，制度问答可能不可用。");
        } else {
            tip.append(NO_API_KEY_TIP);
        }
        if (!allowedActions.isEmpty()) {
            tip.append("\n\n根据你的问题，仍可使用下方入口办理：");
            for (CapabilityRegistry.Capability c : allowedActions) {
                tip.append(c.getLabel()).append("；");
            }
        } else if (!deniedLabels.isEmpty()) {
            tip.append("\n\n另外：你提到的「")
                    .append(String.join("、", deniedLabels))
                    .append("」当前角色无权限，无法办理。");
        }
        return tip.toString();
    }

    private void sendPlainReply(
            SseEmitter emitter,
            String text,
            List<CapabilityRegistry.Capability> actions,
            String userMessage,
            LoginUser user) throws IOException {
        sendEvent(emitter, deltaEvent(text));
        sendEvent(emitter, finalEvent(Map.of(), actions, userMessage, user));
        sendEvent(emitter, doneEvent());
        emitter.complete();
    }

    private String friendlyError(Exception e) {
        String msg = e.getMessage() == null ? "" : e.getMessage();
        if (msg.contains("API Key") || msg.contains("api-key") || msg.contains("DASHSCOPE")) {
            return NO_API_KEY_TIP;
        }
        if (msg.contains("timed out") || msg.contains("Timeout") || msg.contains("connect")) {
            return "模型服务连接超时，请稍后重试。若要办理审批/请假/加班，可直接点下方快捷词，办事卡片不依赖模型。";
        }
        return msg.isBlank() ? "对话失败，请稍后重试或检查百炼/Qdrant 配置。" : msg;
    }

    private ObjectNode deltaEvent(String content) {
        ObjectNode event = objectMapper.createObjectNode();
        event.put("type", "delta");
        event.put("content", content);
        return event;
    }

    private ObjectNode finalEvent(
            Map<Long, String> citations,
            List<CapabilityRegistry.Capability> actions,
            String userMessage,
            LoginUser user) {
        ObjectNode finalEvent = objectMapper.createObjectNode();
        finalEvent.put("type", "final");
        ArrayNode citeArr = finalEvent.putArray("citations");
        citations.forEach((id, title) -> {
            ObjectNode c = citeArr.addObject();
            c.put("docId", id);
            c.put("title", title);
        });
        ArrayNode actionsArr = finalEvent.putArray("actions");
        boolean canForm = capabilityRegistry.canSubmitBizForm(user);
        for (CapabilityRegistry.Capability c : actions) {
            appendAction(actionsArr, c, userMessage, user, canForm);
        }
        return finalEvent;
    }

    private void appendAction(
            ArrayNode actionsArr,
            CapabilityRegistry.Capability c,
            String userMessage,
            LoginUser user,
            boolean canForm) {
        if (c.isTaskList() && "approval_todo".equals(c.getIntent())) {
            List<PendingApprovalTaskDTO> tasks = List.of();
            ApprovalEngineService engine = approvalEngineService.getIfAvailable();
            if (engine != null && user.getUserId() != null) {
                try {
                    tasks = engine.listPendingTasksForAssignee(user.getUserId(), 10);
                } catch (Exception e) {
                    log.warn("list pending tasks for AI failed: {}", e.getMessage());
                }
            }
            actionsArr.add(ApprovalTodoActionFactory.buildAction(objectMapper, tasks));
            ObjectNode nav = actionsArr.addObject();
            nav.put("type", CapabilityRegistry.ACTION_NAVIGATE);
            nav.put("label", "打开审批中心");
            nav.put("route", c.getRoute());
            nav.put("intent", "approval");
            return;
        }

        if (c.isFormSubmit() && "leave".equals(c.getIntent()) && canForm) {
            actionsArr.add(LeaveBizActionFactory.buildAction(objectMapper, userMessage, c.getRoute()));
            ObjectNode nav = actionsArr.addObject();
            nav.put("type", CapabilityRegistry.ACTION_NAVIGATE);
            nav.put("label", "前往请假页");
            nav.put("route", c.getRoute());
            nav.put("intent", c.getIntent());
            return;
        }

        if (c.isFormSubmit() && "overtime".equals(c.getIntent()) && canForm) {
            actionsArr.add(OvertimeBizActionFactory.buildAction(objectMapper, userMessage, c.getRoute()));
            ObjectNode nav = actionsArr.addObject();
            nav.put("type", CapabilityRegistry.ACTION_NAVIGATE);
            nav.put("label", "前往加班页");
            nav.put("route", c.getRoute());
            nav.put("intent", c.getIntent());
            return;
        }

        if (c.isInfoList()) {
            appendInfoListAction(actionsArr, c, userMessage, user);
            return;
        }

        if (c.isDataCard()) {
            appendDataCardAction(actionsArr, c, userMessage);
            return;
        }

        ObjectNode a = actionsArr.addObject();
        a.put("type", CapabilityRegistry.ACTION_NAVIGATE);
        a.put("label", "前往" + c.getLabel());
        a.put("route", c.getRoute());
        a.put("intent", c.getIntent());
    }

    private void appendInfoListAction(
            ArrayNode actionsArr,
            CapabilityRegistry.Capability c,
            String userMessage,
            LoginUser user) {
        RosterQueryService roster = rosterQueryService.getIfAvailable();
        OrgLookupService org = orgLookupService.getIfAvailable();
        if (roster == null) {
            addNavigate(actionsArr, "打开花名册", EmployeeRosterActionFactory.ROUTE, c.getIntent());
            return;
        }

        try {
            if ("my_team".equals(c.getIntent())) {
                Long deptId = user.getDeptId();
                if (deptId == null) {
                    actionsArr.add(EmployeeRosterActionFactory.buildAction(
                            objectMapper, c.getIntent(), "我的团队", List.of(), 0,
                            null, "当前账号未绑定部门，无法列出本部门名单", null));
                    return;
                }
                RosterQueryRequest req = new RosterQueryRequest();
                req.setDepartmentIds(List.of(deptId));
                req.setLimit(20);
                RosterQueryResult result = roster.search(req);
                String deptName = null;
                if (org != null) {
                    deptName = org.getDepartmentHeadcount(deptId).map(DeptBriefDTO::getName).orElse(null);
                }
                actionsArr.add(EmployeeRosterActionFactory.buildAction(
                        objectMapper, c.getIntent(), "我的团队",
                        result.getItems(), result.getTotal(), deptName,
                        result.getTotal() == 0 ? "本部门暂无可见员工" : null, deptId));
                return;
            }

            if ("employee_search".equals(c.getIntent())) {
                String keyword = AiQuerySlotExtractor.extractPersonKeyword(userMessage);
                if (keyword == null || keyword.isBlank()) {
                    actionsArr.add(EmployeeRosterActionFactory.buildAction(
                            objectMapper, c.getIntent(), "查员工", List.of(), 0,
                            null, "请说明姓名或工号，例如「查一下张三」或「工号 E001」", null));
                    return;
                }
                RosterQueryRequest req = new RosterQueryRequest();
                req.setKeyword(keyword);
                req.setLimit(20);
                RosterQueryResult result = roster.search(req);
                actionsArr.add(EmployeeRosterActionFactory.buildAction(
                        objectMapper, c.getIntent(), "查员工：" + keyword,
                        result.getItems(), result.getTotal(), null,
                        result.getTotal() == 0 ? "未找到匹配员工" : null, null));
                return;
            }

            // employee_list：按部门名解析
            String hint = AiQuerySlotExtractor.extractDeptHint(userMessage);
            if (hint == null || org == null) {
                rememberPending(user, "employee_list");
                actionsArr.add(EmployeeRosterActionFactory.buildAction(
                        objectMapper, c.getIntent(), "部门员工名单", List.of(), 0,
                        null, "请直接回复部门全名，例如「后端开发部」", null));
                return;
            }
            List<DeptBriefDTO> matched = org.resolveDepartmentsByNameHint(hint, 5);
            if (matched.isEmpty()) {
                rememberPending(user, "employee_list");
                actionsArr.add(EmployeeRosterActionFactory.buildAction(
                        objectMapper, c.getIntent(), "部门员工名单", List.of(), 0,
                        hint, "未找到名为「" + hint + "」的部门，请换个说法（如「后端开发部」）或打开花名册筛选", null));
                return;
            }
            if (matched.size() > 1) {
                rememberPending(user, "employee_list");
                actionsArr.add(DeptStatsActionFactory.buildAmbiguousDepts(objectMapper, matched));
                return;
            }
            DeptBriefDTO dept = matched.get(0);
            RosterQueryRequest req = new RosterQueryRequest();
            req.setDepartmentIds(List.of(dept.getDepartmentId()));
            req.setLimit(20);
            RosterQueryResult result = roster.search(req);
            clearPending(user);
            actionsArr.add(EmployeeRosterActionFactory.buildAction(
                    objectMapper, c.getIntent(), dept.getName() + "员工",
                    result.getItems(), result.getTotal(), dept.getName(),
                    result.getTotal() == 0 ? "该部门暂无可见员工" : null, dept.getDepartmentId()));
        } catch (Exception e) {
            log.warn("AI roster query failed: {}", e.getMessage());
            rememberPending(user, c.getIntent());
            String tip = friendlyRosterFailTip(e);
            actionsArr.add(EmployeeRosterActionFactory.buildAction(
                    objectMapper, c.getIntent(), c.getLabel(), List.of(), 0,
                    null, tip, null));
        }
    }

    private static String friendlyRosterFailTip(Exception e) {
        String msg = e == null || e.getMessage() == null ? "" : e.getMessage();
        String name = e == null ? "" : e.getClass().getSimpleName();
        if (name.contains("Forbidden") || msg.contains("403") || msg.contains("无权限")) {
            return "当前账号暂无花名册查看权限，可改用「打开花名册」或联系管理员开通。";
        }
        if (name.contains("Unauthorized") || msg.contains("未登录") || msg.contains("401")) {
            return "登录状态已失效，请刷新页面后重试；也可先打开花名册查看。";
        }
        return "暂时没能拉到名单（多半是权限或部门匹配问题，不是人太多）。可换完整部门名再试，或点卡片内「打开花名册」。";
    }

    private void appendDataCardAction(
            ArrayNode actionsArr,
            CapabilityRegistry.Capability c,
            String userMessage) {
        if ("leave_balance".equals(c.getIntent())) {
            appendLeaveBalanceCard(actionsArr);
            return;
        }
        OrgLookupService org = orgLookupService.getIfAvailable();
        if (org == null) {
            addNavigate(actionsArr, "打开组织架构", DeptStatsActionFactory.ROUTE_ORG, c.getIntent());
            return;
        }
        try {
            if ("org_overview".equals(c.getIntent())) {
                List<DeptBriefDTO> top = org.listTopDepartmentHeadcounts(12);
                actionsArr.add(DeptStatsActionFactory.buildOrgOverview(objectMapper, top));
                return;
            }
            // dept_headcount
            String hint = AiQuerySlotExtractor.extractDeptHint(userMessage);
            if (hint == null) {
                ObjectNode empty = objectMapper.createObjectNode();
                empty.put("type", CapabilityRegistry.ACTION_DATA_CARD);
                empty.put("intent", c.getIntent());
                empty.put("formId", DeptStatsActionFactory.FORM_ID);
                empty.put("label", "部门人数");
                empty.put("hint", "请说明部门，例如「后端有多少人」");
                empty.put("route", DeptStatsActionFactory.ROUTE_ORG);
                empty.putArray("stats");
                actionsArr.add(empty);
                return;
            }
            List<DeptBriefDTO> matched = org.resolveDepartmentsByNameHint(hint, 5);
            if (matched.isEmpty()) {
                ObjectNode empty = objectMapper.createObjectNode();
                empty.put("type", CapabilityRegistry.ACTION_DATA_CARD);
                empty.put("intent", c.getIntent());
                empty.put("formId", DeptStatsActionFactory.FORM_ID);
                empty.put("label", "部门人数");
                empty.put("hint", "未找到部门「" + hint + "」");
                empty.put("route", DeptStatsActionFactory.ROUTE_ORG);
                empty.putArray("stats");
                actionsArr.add(empty);
                return;
            }
            if (matched.size() > 1) {
                actionsArr.add(DeptStatsActionFactory.buildAmbiguousDepts(objectMapper, matched));
                return;
            }
            DeptBriefDTO dept = matched.get(0);
            DeptBriefDTO full = org.getDepartmentHeadcount(dept.getDepartmentId()).orElse(dept);
            if (full.getName() == null) {
                full.setName(dept.getName());
            }
            actionsArr.add(DeptStatsActionFactory.buildDeptHeadcount(objectMapper, c.getIntent(), full));
        } catch (Exception e) {
            log.warn("AI dept stats failed: {}", e.getMessage());
            addNavigate(actionsArr, "打开组织架构", DeptStatsActionFactory.ROUTE_ORG, c.getIntent());
        }
    }

    private void appendLeaveBalanceCard(ArrayNode actionsArr) {
        LoginUser user = SecurityUtils.getLoginUser();
        if (user == null || user.getEmployeeId() == null) {
            actionsArr.add(LeaveBalanceActionFactory.buildNeedBindEmployee(objectMapper));
            addNavigate(actionsArr, "前往请假页", LeaveBalanceActionFactory.ROUTE, "leave_balance");
            return;
        }
        LeaveBalanceQueryService svc = leaveBalanceQueryService.getIfAvailable();
        if (svc == null) {
            addNavigate(actionsArr, "前往请假页查看余额", LeaveBalanceActionFactory.ROUTE, "leave_balance");
            return;
        }
        try {
            List<LeaveBalanceBriefDTO> balances = svc.listBalances(user.getEmployeeId());
            actionsArr.add(LeaveBalanceActionFactory.buildAction(objectMapper, balances));
            addNavigate(actionsArr, "前往请假页", LeaveBalanceActionFactory.ROUTE, "leave_balance");
        } catch (Exception e) {
            log.warn("AI leave balance failed: {}", e.getMessage());
            addNavigate(actionsArr, "前往请假页查看余额", LeaveBalanceActionFactory.ROUTE, "leave_balance");
        }
    }

    private void addNavigate(ArrayNode actionsArr, String label, String route, String intent) {
        ObjectNode nav = actionsArr.addObject();
        nav.put("type", CapabilityRegistry.ACTION_NAVIGATE);
        nav.put("label", label);
        nav.put("route", route);
        nav.put("intent", intent);
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
