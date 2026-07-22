package com.company.hrms.workflow.controller;

import com.company.hrms.common.approval.CreateApprovalRequest;
import com.company.hrms.common.approval.CreateApprovalResult;
import com.company.hrms.common.web.PageResult;
import com.company.hrms.common.web.Result;
import com.company.hrms.workflow.dto.ApprovalDtos;
import com.company.hrms.workflow.service.DbApprovalService;
import com.company.hrms.workflow.service.DelegationService;
import com.company.hrms.workflow.support.CurrentUserProvider;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 【审批中心 REST 接口】路径前缀 {@code /api/v1/approvals}。
 * <b>职责</b>：统一审批引擎的待办/已办/我发起、审批操作（同意/驳回/转交/催办/撤回）、
 * 实例进度查询、离职交接人选人、审批委托 CRUD。本类只推进审批流程，终态经 {@code LifecycleApprovalHandler}
 * 回调各业务 Service。
 */
@RestController
@RequestMapping("/approvals")
public class ApprovalController {

    private final DbApprovalService dbApprovalService;
    private final DelegationService delegationService;
    private final CurrentUserProvider currentUserProvider;

    public ApprovalController(DbApprovalService dbApprovalService,
                              DelegationService delegationService,
                              CurrentUserProvider currentUserProvider) {
        this.dbApprovalService = dbApprovalService;
        this.delegationService = delegationService;
        this.currentUserProvider = currentUserProvider;
    }

    /**
     * POST /api/v1/approvals/instances
     * 跨模块统一创建审批实例（如手机号变更 MOBILE_CHANGE）；
     * 实现：{@code ApprovalController.startInstance} → {@code DbApprovalService.createInstance}
     * → {@code AssigneeResolver.resolveNodes} 解析节点链 → 落库 {@code approval_instance} →
     * 生成首节点 PENDING 任务 → 写 SUBMIT 日志 → 可选 scheduleRemind。
     */
    @PostMapping("/instances")
    public Result<CreateApprovalResult> startInstance(@RequestBody CreateApprovalRequest body) {
        if (body.getApplicantId() == null) {
            body.setApplicantId(currentUserProvider.requireUserId());
        }
        return Result.success(dbApprovalService.createInstance(body));
    }

    /**
     * GET /api/v1/approvals/tasks/stats
     *查询当前登录用户的待办统计（pending / approvedToday / overdueCount），
     * 供审批中心顶部 KPI 卡片展示。
     */
    @GetMapping("/tasks/stats")
    public Result<ApprovalDtos.TaskStatsVO> taskStats() {
        long userId = currentUserProvider.requireUserId();
        return Result.success(dbApprovalService.taskStats(userId));
    }

    /**
     * GET /api/v1/approvals/tasks
     * 分页查询当前用户的待办或已办审批任务，支持按流程类型、关键词筛选。
     * 【实现】{@code ApprovalController.listTasks} → {@code DbApprovalService.listTasks}
     * → 查 {@code approval_task}（含委托解析后的 effective assignee 过滤）→ 关联实例摘要 →
     * 内存分页返回 {@code TaskListItemVO}。
     */
    @GetMapping("/tasks")
    public Result<PageResult<ApprovalDtos.TaskListItemVO>> listTasks(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String processType,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "20") int pageSize) {
        long userId = currentUserProvider.requireUserId();
        String pt = processType != null ? processType : type;
        return Result.success(dbApprovalService.listTasks(userId, status, pt, keyword, page, pageSize));
    }

    /**
     * GET /api/v1/approvals/tasks/{id}
     * 获取单条待办详情：业务摘要、节点进度、审批时间线、可用操作按钮，供审批 Drawer 展示。
     */
    @GetMapping("/tasks/{id}")
    public Result<ApprovalDtos.TaskDetailVO> taskDetail(@PathVariable("id") long id) {
        long userId = currentUserProvider.requireUserId();
        return Result.success(dbApprovalService.getTaskDetail(id, userId));
    }

    /**
     * POST /api/v1/approvals/tasks/{id}/action
     * 对待办执行同意/驳回/转交；审批主链路入口。正式离职第 1 岗同意时可传
     * {@code handoverEmployeeId} 确认工作交接人。
     */
    @PostMapping("/tasks/{id}/action")
    public Result<Void> action(@PathVariable("id") long id, @RequestBody ApprovalDtos.ActionRequest body) {
        long userId = currentUserProvider.requireUserId();
        dbApprovalService.action(id, userId, body);
        return Result.success();
    }

    /**
     * POST /api/v1/approvals/tasks/{id}/remind
     *对指定待办立即催办当前审批人（MQ 通知或日志降级）。
     */
    @PostMapping("/tasks/{id}/remind")
    public Result<Void> remind(@PathVariable("id") long id) {
        long userId = currentUserProvider.requireUserId();
        dbApprovalService.remind(id, userId);
        return Result.success();
    }

    /**
     * POST /api/v1/approvals/instances/{id}/withdraw
     * 发起人（或 HR/管理员代撤）撤回尚未有人审批的 PENDING 实例；
     * 业务单同步回退（如入职回 draft、请假取消等）。
     */
    @PostMapping("/instances/{id}/withdraw")
    public Result<Void> withdraw(@PathVariable("id") long id) {
        long userId = currentUserProvider.requireUserId();
        Long employeeId = null;
        try {
            var login = com.company.hrms.common.security.SecurityUtils.getLoginUser();
            if (login != null) {
                employeeId = login.getEmployeeId();
            }
        } catch (Exception ignored) {
            // ignore
        }
        dbApprovalService.doWithdrawInstance(id, userId, employeeId);
        return Result.success();
    }

    /**
     * GET /api/v1/approvals/instances
     * 分页查询当前用户作为发起人创建的审批实例列表（「我发起的」第三 Tab）。
     */
    @GetMapping("/instances")
    public Result<PageResult<ApprovalDtos.InstanceListItemVO>> myInstances(
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "20") int pageSize) {
        long userId = currentUserProvider.requireUserId();
        return Result.success(dbApprovalService.listMyInstances(userId, page, pageSize));
    }

    /**
     * GET /api/v1/approvals/handover-candidates
     * 正式离职审批时搜索工作交接人候选（轻量搜索，非花名册接口），
     * 避免财务经理作为部门负责人时调花名册报无权限。
     */
    @GetMapping("/handover-candidates")
    public Result<List<ApprovalDtos.HandoverCandidateVO>> handoverCandidates(
            @RequestParam(required = false) String keyword) {
        return Result.success(dbApprovalService.searchHandoverCandidates(keyword));
    }

    /**
     * GET /api/v1/approvals/instances/{id}
     * 发起人或 HR 查看审批实例进度（节点状态 + 时间线 + 业务摘要），
     * 入职页「审批进度」等场景也复用此接口。
     */
    @GetMapping("/instances/{id}")
    public Result<ApprovalDtos.InstanceDetailVO> instanceDetail(@PathVariable("id") long id) {
        long userId = currentUserProvider.requireUserId();
        Long employeeId = null;
        try {
            var login = com.company.hrms.common.security.SecurityUtils.getLoginUser();
            if (login != null) {
                employeeId = login.getEmployeeId();
            }
        } catch (Exception ignored) {
            // ignore
        }
        return Result.success(dbApprovalService.getInstanceDetail(id, userId, employeeId));
    }

    /**
     * GET /api/v1/approvals/delegations
     *分页查询当前用户创建的审批委托规则列表。
     */
    @GetMapping("/delegations")
    public Result<PageResult<ApprovalDtos.DelegationVO>> listDelegations(
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "20") int pageSize) {
        long userId = currentUserProvider.requireUserId();
        return Result.success(delegationService.list(userId, page, pageSize));
    }

    /**
     * GET /api/v1/approvals/delegate-candidates
     * 搜索可被委托的审批人（仅有审批权限的启用用户，可按姓名/工号/用户名搜）。
     */
    @GetMapping("/delegate-candidates")
    public Result<List<ApprovalDtos.DelegateCandidateVO>> delegateCandidates(
            @RequestParam(required = false) String keyword) {
        long userId = currentUserProvider.requireUserId();
        return Result.success(delegationService.searchDelegateCandidates(userId, keyword));
    }

    /**
     * POST /api/v1/approvals/delegations
     * 新增一条审批委托规则；同一委托人同时仅允许一条 ACTIVE 规则（冲突 60003）。
     */
    @PostMapping("/delegations")
    public Result<ApprovalDtos.DelegationVO> createDelegation(
            @RequestBody ApprovalDtos.DelegationFormRequest body) {
        long userId = currentUserProvider.requireUserId();
        return Result.success(delegationService.create(userId, body));
    }

    /**
     * DELETE /api/v1/approvals/delegations/{id}
     * 取消指定委托规则，状态变更为 CANCELLED。
     */
    @DeleteMapping("/delegations/{id}")
    public Result<Void> cancelDelegation(@PathVariable("id") long id) {
        long userId = currentUserProvider.requireUserId();
        delegationService.cancel(id, userId);
        return Result.success();
    }
}
