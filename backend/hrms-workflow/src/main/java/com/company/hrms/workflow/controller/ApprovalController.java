package com.company.hrms.workflow.controller;

import com.company.hrms.common.web.PageResult;
import com.company.hrms.common.web.Result;
import com.company.hrms.workflow.dto.ApprovalDtos;
import com.company.hrms.workflow.service.ApprovalEngine;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/approvals")
public class ApprovalController {

    private final ApprovalEngine approvalEngine;
    private final DbApprovalService dbApprovalService;
    private final DelegationService delegationService;
    private final CurrentUserProvider currentUserProvider;

    public ApprovalController(ApprovalEngine approvalEngine,
                              DbApprovalService dbApprovalService,
                              DelegationService delegationService,
                              CurrentUserProvider currentUserProvider) {
        this.approvalEngine = approvalEngine;
        this.dbApprovalService = dbApprovalService;
        this.delegationService = delegationService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/tasks/stats")
    public Result<ApprovalDtos.TaskStatsVO> taskStats() {
        long userId = currentUserProvider.requireUserId();
        ApprovalDtos.TaskStatsVO mem = approvalEngine.taskStats(userId);
        ApprovalDtos.TaskStatsVO db = dbApprovalService.taskStats(userId);
        ApprovalDtos.TaskStatsVO merged = new ApprovalDtos.TaskStatsVO();
        merged.setPending(mem.getPending() + db.getPending());
        merged.setApprovedToday(mem.getApprovedToday() + db.getApprovedToday());
        merged.setOverdueCount(mem.getOverdueCount() + db.getOverdueCount());
        return Result.success(merged);
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
        PageResult<ApprovalDtos.TaskListItemVO> mem =
                approvalEngine.listTasks(userId, status, pt, keyword, 1, 100);
        PageResult<ApprovalDtos.TaskListItemVO> db =
                dbApprovalService.listTasks(userId, status, pt, keyword, 1, 100);
        List<ApprovalDtos.TaskListItemVO> all = new ArrayList<>();
        if (mem.getList() != null) {
            all.addAll(mem.getList());
        }
        if (db.getList() != null) {
            all.addAll(db.getList());
        }
        all.sort(Comparator.comparing(ApprovalDtos.TaskListItemVO::getTaskId,
                Comparator.nullsLast(Comparator.reverseOrder())));
        int p = page <= 0 ? 1 : page;
        int size = pageSize <= 0 ? 20 : Math.min(pageSize, 100);
        int from = Math.min((p - 1) * size, all.size());
        int to = Math.min(from + size, all.size());
        return Result.success(PageResult.of(all.subList(from, to), all.size(), p, size));
    }

    @GetMapping("/tasks/{id}")
    public Result<ApprovalDtos.TaskDetailVO> taskDetail(@PathVariable("id") long id) {
        long userId = currentUserProvider.requireUserId();
        if (dbApprovalService.ownsTask(id)) {
            return Result.success(dbApprovalService.getTaskDetail(id, userId));
        }
        return Result.success(approvalEngine.getTaskDetail(id, userId));
    }

    @PostMapping("/tasks/{id}/action")
    public Result<Void> action(@PathVariable("id") long id, @RequestBody ApprovalDtos.ActionRequest body) {
        long userId = currentUserProvider.requireUserId();
        if (dbApprovalService.ownsTask(id)) {
            dbApprovalService.action(id, userId, body);
        } else {
            approvalEngine.action(id, userId, body);
        }
        return Result.success();
    }

    @PostMapping("/tasks/{id}/remind")
    public Result<Void> remind(@PathVariable("id") long id) {
        long userId = currentUserProvider.requireUserId();
        if (dbApprovalService.ownsTask(id)) {
            dbApprovalService.remind(id, userId);
        } else {
            approvalEngine.remind(id, userId);
        }
        return Result.success();
    }

    @PostMapping("/instances/{id}/withdraw")
    public Result<Void> withdraw(@PathVariable("id") long id) {
        long userId = currentUserProvider.requireUserId();
        if (dbApprovalService.ownsInstance(id)) {
            dbApprovalService.withdrawInstance(id, userId);
        } else {
            approvalEngine.withdrawInstance(id, userId);
        }
        return Result.success();
    }

    @GetMapping("/instances")
    public Result<PageResult<ApprovalDtos.InstanceListItemVO>> myInstances(
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "20") int pageSize) {
        long userId = currentUserProvider.requireUserId();
        PageResult<ApprovalDtos.InstanceListItemVO> mem = approvalEngine.listMyInstances(userId, 1, 100);
        PageResult<ApprovalDtos.InstanceListItemVO> db = dbApprovalService.listMyInstances(userId, 1, 100);
        List<ApprovalDtos.InstanceListItemVO> all = new ArrayList<>();
        if (mem.getList() != null) {
            all.addAll(mem.getList());
        }
        if (db.getList() != null) {
            all.addAll(db.getList());
        }
        int p = page <= 0 ? 1 : page;
        int size = pageSize <= 0 ? 20 : Math.min(pageSize, 100);
        int from = Math.min((p - 1) * size, all.size());
        int to = Math.min(from + size, all.size());
        return Result.success(PageResult.of(all.subList(from, to), all.size(), p, size));
    }

    @GetMapping("/delegations")
    public Result<PageResult<ApprovalDtos.DelegationVO>> listDelegations(
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "20") int pageSize) {
        long userId = currentUserProvider.requireUserId();
        return Result.success(delegationService.list(userId, page, pageSize));
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
