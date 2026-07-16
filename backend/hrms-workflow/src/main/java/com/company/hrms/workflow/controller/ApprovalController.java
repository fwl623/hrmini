package com.company.hrms.workflow.controller;

import com.company.hrms.common.web.PageResult;
import com.company.hrms.common.web.Result;
import com.company.hrms.workflow.dto.ApprovalDtos;
import com.company.hrms.workflow.service.ApprovalEngine;
import com.company.hrms.workflow.support.CurrentUserProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/approvals")
public class ApprovalController {

    private final ApprovalEngine approvalEngine;
    private final CurrentUserProvider currentUserProvider;

    public ApprovalController(ApprovalEngine approvalEngine, CurrentUserProvider currentUserProvider) {
        this.approvalEngine = approvalEngine;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/tasks/stats")
    public Result<ApprovalDtos.TaskStatsVO> taskStats() {
        long userId = currentUserProvider.requireUserId();
        return Result.success(approvalEngine.taskStats(userId));
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
        return Result.success(approvalEngine.listTasks(userId, status, pt, keyword, page, pageSize));
    }

    @GetMapping("/tasks/{id}")
    public Result<ApprovalDtos.TaskDetailVO> taskDetail(@PathVariable("id") long id) {
        long userId = currentUserProvider.requireUserId();
        return Result.success(approvalEngine.getTaskDetail(id, userId));
    }

    @PostMapping("/tasks/{id}/action")
    public Result<Void> action(@PathVariable("id") long id, @RequestBody ApprovalDtos.ActionRequest body) {
        long userId = currentUserProvider.requireUserId();
        approvalEngine.action(id, userId, body);
        return Result.success();
    }

    @PostMapping("/tasks/{id}/remind")
    public Result<Void> remind(@PathVariable("id") long id) {
        long userId = currentUserProvider.requireUserId();
        approvalEngine.remind(id, userId);
        return Result.success();
    }

    @PostMapping("/instances/{id}/withdraw")
    public Result<Void> withdraw(@PathVariable("id") long id) {
        long userId = currentUserProvider.requireUserId();
        approvalEngine.withdrawInstance(id, userId);
        return Result.success();
    }

    @GetMapping("/instances")
    public Result<PageResult<ApprovalDtos.InstanceListItemVO>> myInstances(
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "20") int pageSize) {
        long userId = currentUserProvider.requireUserId();
        return Result.success(approvalEngine.listMyInstances(userId, page, pageSize));
    }
}
