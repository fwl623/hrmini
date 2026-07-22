package com.company.hrms.workflow.controller;

import com.company.hrms.common.web.Result;
import com.company.hrms.workflow.dto.OnboardingDtos;
import com.company.hrms.workflow.service.OnboardingService;
import com.company.hrms.workflow.support.CurrentUserProvider;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 入职管理 REST 入口。完整前缀：{@code /api/v1/onboarding/applications}。
 * <b>职责</b>：只收参、取当前用户、委派 {@link OnboardingService}；状态机与建档不在本类。
 * <b>前端页面</b>：{@code frontend/src/pages/admin/onboarding/index.tsx}
 * <b>前端封装</b>：{@code frontend/src/services/workflow.ts}
 * <b>PRD</b>：禁止 {@code POST /employees} 直建在职员工，必须走本模块 → 审批 → confirm。
 */
@RestController
@RequestMapping("/onboarding/applications")
public class OnboardingController {

    private final OnboardingService onboardingService;
    private final CurrentUserProvider currentUserProvider;

    public OnboardingController(OnboardingService onboardingService, CurrentUserProvider currentUserProvider) {
        this.onboardingService = onboardingService;
        this.currentUserProvider = currentUserProvider;
    }

    /**
     * GET /api/v1/onboarding/applications
     * 分页查入职申请；可选 {@code status}；响应含 list + stats（统计卡片一次拿到）。
     * 实现：→ {@link OnboardingService#list}：校验 HR/管理员 → 按状态查表 → 可见性过滤 → 内存分页 → 附带 stats。
     */
    @GetMapping
    public Result<OnboardingDtos.OnboardingListResponse> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "20") int pageSize) {
        return Result.success(onboardingService.list(page, pageSize, status));
    }

    /**
     * GET /api/v1/onboarding/applications/stats
     * 各状态数量（draft/pending/approved_pending/onboarded/…）。
     * 实现：→ {@link OnboardingService#stats}：扫可见入职单按 status 累加。
     */
    @GetMapping("/stats")
    public Result<OnboardingDtos.OnboardingStatsVO> stats() {
        return Result.success(onboardingService.stats());
    }

    /**
     * GET /api/v1/onboarding/applications/{id}
     * 单条入职详情（表单回填 / 详情抽屉）。
     * 实现：→ {@link OnboardingService#detail}：按 id 查 → 可见性校验 → 转 VO。
     */
    @GetMapping("/{id}")
    public Result<OnboardingDtos.OnboardingVO> detail(@PathVariable("id") long id) {
        return Result.success(onboardingService.detail(id));
    }

    /**
     * POST /api/v1/onboarding/applications
     * 新建入职草稿（status=draft），尚未进审批中心。。
     * 实现：取当前 userId → {@link OnboardingService#create}：校验表单/手机号唯一/部门职位 → insert 草稿行。
     */
    @PostMapping
    public Result<OnboardingDtos.OnboardingVO> create(@RequestBody OnboardingDtos.OnboardingFormRequest body) {
        long userId = currentUserProvider.requireUserId();
        return Result.success(onboardingService.create(body, userId));
    }

    /**
     * PUT /api/v1/onboarding/applications/{id}
     * 改草稿或驳回单字段（姓名、部门、薪资、预计入职日等）。
     * 实现：→ {@link OnboardingService#update}：仅 draft/rejected 可改；校验后 updateById。
     */
    @PutMapping("/{id}")
    public Result<OnboardingDtos.OnboardingVO> update(@PathVariable("id") long id,
                                                      @RequestBody OnboardingDtos.OnboardingFormRequest body) {
        return Result.success(onboardingService.update(id, body));
    }

    /**
     * DELETE /api/v1/onboarding/applications/{id}
     * 删除草稿或驳回单。
     * 前端谁用：入职页行操作「删除」→ {@code deleteOnboardingApplication}。
     * 实现：→ {@link OnboardingService#delete}：状态校验后物理/逻辑删申请行。
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") long id) {
        onboardingService.delete(id);
        return Result.success();
    }

    /**
     * POST /api/v1/onboarding/applications/{id}/submit
     * 提交审批。业务单 draft/rejected→pending，并生成审批实例+首节点待办。
     */
    @PostMapping("/{id}/submit")
    public Result<OnboardingDtos.OnboardingVO> submit(@PathVariable("id") long id) {
        long userId = currentUserProvider.requireUserId();
        return Result.success(onboardingService.submit(id, userId));
    }

    /**
     * POST /api/v1/onboarding/applications/{id}/withdraw
     * 审批中撤回；实例结束，业务单回 draft。
     */
    @PostMapping("/{id}/withdraw")
    public Result<OnboardingDtos.OnboardingVO> withdraw(@PathVariable("id") long id) {
        long userId = currentUserProvider.requireUserId();
        return Result.success(onboardingService.withdraw(id, userId));
    }

    /**
     * POST /api/v1/onboarding/applications/{id}/confirm
     * 【主链路】审批已通过后，HR 确认入职：建员工档案 + 开登录账号 → onboarded。
     */
    @PostMapping("/{id}/confirm")
    public Result<OnboardingDtos.OnboardingVO> confirm(@PathVariable("id") long id) {
        return Result.success(onboardingService.confirm(id));
    }

    /**
     * POST /api/v1/onboarding/applications/{id}/abandon
     * 审批通过后放弃入职（不建档），status→abandoned。
    */
    @PostMapping("/{id}/abandon")
    public Result<OnboardingDtos.OnboardingVO> abandon(@PathVariable("id") long id) {
        return Result.success(onboardingService.abandon(id));
    }
}
