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

    @PostMapping("/instances")
    public Result<CreateApprovalResult> startInstance(@RequestBody CreateApprovalRequest body) {
        if (body.getApplicantId() == null) {
            body.setApplicantId(currentUserProvider.requireUserId());
        }
        return Result.success(dbApprovalService.createInstance(body));
    }

    @GetMapping("/tasks/stats")
    public Result<ApprovalDtos.TaskStatsVO> taskStats() {
        long userId = currentUserProvider.requireUserId();
        return Result.success(dbApprovalService.taskStats(userId));
    }

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

    @GetMapping("/tasks/{id}")
    public Result<ApprovalDtos.TaskDetailVO> taskDetail(@PathVariable("id") long id) {
        long userId = currentUserProvider.requireUserId();
        return Result.success(dbApprovalService.getTaskDetail(id, userId));
    }

    @PostMapping("/tasks/{id}/action")
    public Result<Void> action(@PathVariable("id") long id, @RequestBody ApprovalDtos.ActionRequest body) {
        long userId = currentUserProvider.requireUserId();
        dbApprovalService.action(id, userId, body);
        return Result.success();
    }

    @PostMapping("/tasks/{id}/remind")
    public Result<Void> remind(@PathVariable("id") long id) {
        long userId = currentUserProvider.requireUserId();
        dbApprovalService.remind(id, userId);
        return Result.success();
    }

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

    @GetMapping("/instances")
    public Result<PageResult<ApprovalDtos.InstanceListItemVO>> myInstances(
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "20") int pageSize) {
        long userId = currentUserProvider.requireUserId();
        return Result.success(dbApprovalService.listMyInstances(userId, page, pageSize));
    }

    /**
     * 正式离职交接人选人（轻量搜索）。
     * 允许 HR / 部门主管 / 财务经理 / 管理员；不走花名册接口，避免财务经理作为部门负责人时报无权限。
     */
    @GetMapping("/handover-candidates")
    public Result<List<ApprovalDtos.HandoverCandidateVO>> handoverCandidates(
            @RequestParam(required = false) String keyword) {
        return Result.success(dbApprovalService.searchHandoverCandidates(keyword));
    }

    /** 发起人查看审批进度 */
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

    @GetMapping("/delegations")
    public Result<PageResult<ApprovalDtos.DelegationVO>> listDelegations(
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "20") int pageSize) {
        long userId = currentUserProvider.requireUserId();
        return Result.success(delegationService.list(userId, page, pageSize));
    }

    /**
     * 审批委托候选人：仅返回有审批权限的启用用户（可按姓名/工号/用户名搜索）。
     */
    @GetMapping("/delegate-candidates")
    public Result<List<ApprovalDtos.DelegateCandidateVO>> delegateCandidates(
            @RequestParam(required = false) String keyword) {
        long userId = currentUserProvider.requireUserId();
        return Result.success(delegationService.searchDelegateCandidates(userId, keyword));
    }

    @PostMapping("/delegations")
    public Result<ApprovalDtos.DelegationVO> createDelegation(
            @RequestBody ApprovalDtos.DelegationFormRequest body) {
        long userId = currentUserProvider.requireUserId();
        return Result.success(delegationService.create(userId, body));
    }

    @DeleteMapping("/delegations/{id}")
    public Result<Void> cancelDelegation(@PathVariable("id") long id) {
        long userId = currentUserProvider.requireUserId();
        delegationService.cancel(id, userId);
        return Result.success();
    }
}
