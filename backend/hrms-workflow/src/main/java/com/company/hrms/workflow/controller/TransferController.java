package com.company.hrms.workflow.controller;

import com.company.hrms.common.web.PageResult;
import com.company.hrms.common.web.Result;
import com.company.hrms.workflow.dto.LifecycleDtos;
import com.company.hrms.workflow.service.TransferService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 【调岗 REST 接口】路径前缀 {@code /api/v1/transfers}。
 * 职责：HR 发起调岗申请、列表/详情查询、到期生效补跑。发起时校验
 * {@code newDepartmentId ≠ 原部门}（否则 30004），创建多节点审批实例：
 * 原部门负责人 → 新部门负责人 →（含调薪时）财务 → HR 备案。
 * 审批通过后若生效日未到则 status=PENDING_EFFECT，到期由 Job / {@code effect-due}
 * 调用 {@code EmployeeLifecycleService.applyTransfer} 变更部门/职位/薪资并写调岗历史。
 * 审批操作在 {@code pages/admin/approval/index.tsx} 通过 {@code services/workflow.ts} 完成。
 */
@RestController
@RequestMapping("/transfers")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    /**
     * POST /api/v1/transfers
     * HR 为员工发起调岗申请（新部门、职位、生效日、原因及可选调薪），并创建三/四节点审批实例进入审批中心。
     */
    @PostMapping
    public Result<LifecycleDtos.TransferVO> create(@RequestBody LifecycleDtos.TransferCreateRequest body) {
        return Result.success(transferService.create(body));
    }

    /**
     * GET /api/v1/transfers
     * 分页查询调岗申请列表，支持按 status 筛选，供管理台 ProTable 展示。
     */
    @GetMapping
    public Result<PageResult<LifecycleDtos.TransferVO>> list(
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status) {
        return Result.success(transferService.list(page, pageSize, status));
    }

    /**
     * GET /api/v1/transfers/{id}
     * 获取单条调岗详情，含三/四节点审批进度（nodes 数组），供详情 Drawer Steps 展示。
     * {@code pages/admin/transfers/index.tsx}（点击行打开 Drawer）→
    */
    @GetMapping("/{id}")
    public Result<LifecycleDtos.TransferVO> detail(@PathVariable("id") Long id) {
        return Result.success(transferService.detail(id));
    }

    /**
     * POST /api/v1/transfers/effect-due
     * 手动触发到期调岗生效（联调/补跑）：status=PENDING_EFFECT 且
     * {@code effectiveDate ≤ 今天} 的单据调用 {@code applyTransfer} 变更员工档案。
     */
    @PostMapping("/effect-due")
    public Result<Integer> effectDue() {
        return Result.success(transferService.effectDueTransfers(java.time.LocalDate.now()));
    }
}
