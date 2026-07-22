package com.company.hrms.workflow.controller;

import com.company.hrms.common.web.PageResult;
import com.company.hrms.common.web.Result;
import com.company.hrms.workflow.dto.LifecycleDtos;
import com.company.hrms.workflow.service.ResignationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 【离职双通道 REST 接口】门户路径 {@code /api/v1/profile/resignation-requests}，
 * HR 管理路径 {@code /api/v1/resignation-requests} 与 {@code /api/v1/resignations*}。
 * 职责：实现 PRD 离职双阶段——
 *   员工门户登记离职意向（仅落 {@code employee_resignation_request}，<b>不</b>建审批实例）</li>
 *   HR 发起正式离职（落 {@code resignation_application} + 创建审批实例：部门负责人 → HR）</li>
 * 审批终态经 {@code LifecycleApprovalHandler} 回调 {@code ResignationService.onResignationApproved}，
 * 员工进入待离职(30)；到期由 Job / {@code effect-due} 改状态(40)并禁账号、发 MQ。
 * 部门主管只进审批中心待办，不进本 Controller 的管理台。
 */
@RestController
public class ResignationController {

    private final ResignationService resignationService;

    public ResignationController(ResignationService resignationService) {
        this.resignationService = resignationService;
    }

    // ---------- 门户 SELF：双通道第一阶段 ----------

    /**
     * POST /api/v1/profile/resignation-requests
     * 员工本人提交离职意向登记（期望离职日、原因分类等），供 HR 在管理台受理后
     * 再发起正式离职；本阶段不进审批中心。
     */
    @PostMapping("/profile/resignation-requests")
    public Result<LifecycleDtos.ResignationRequestVO> createMyRequest(
            @RequestBody LifecycleDtos.ResignationRequestCreate body) {
        return Result.success(resignationService.createMyRequest(body));
    }

    /**
     * GET /api/v1/profile/resignation-requests
     * 分页查询当前登录员工本人的离职申请记录，供门户「我的申请」列表展示。
     */
    @GetMapping("/profile/resignation-requests")
    public Result<PageResult<LifecycleDtos.ResignationRequestVO>> myRequests(
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "20") int pageSize) {
        return Result.success(resignationService.listMyRequests(page, pageSize));
    }

    /**
     * POST /api/v1/profile/resignation-requests/{id}/cancel
     * 员工撤销本人仍处于 PENDING 的离职申请；若历史数据曾关联审批实例则一并撤回。
     */
    @PostMapping("/profile/resignation-requests/{id}/cancel")
    public Result<Void> cancelMyRequest(@PathVariable("id") Long id) {
        resignationService.cancelMyRequest(id);
        return Result.success();
    }

    // ---------- HR 管理台：双通道第二阶段 ----------

    /**
     * GET /api/v1/resignation-requests
     * 分页查看员工提交的离职申请（双通道第一阶段产出），已转入正式离职的申请会被过滤，供管理台「员工申请」Tab 受理。
     */
    @GetMapping("/resignation-requests")
    public Result<PageResult<LifecycleDtos.ResignationRequestVO>> listRequests(
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status) {
        return Result.success(resignationService.listRequests(page, pageSize, status));
    }

    /**
     * POST /api/v1/resignations
     * HR 发起正式离职（双通道第二阶段）：落正式离职单并创建审批实例
     * （部门负责人确认交接 → HR 审批）；可关联 {@code requestId}，也可 HR 直提（线下协商）。
     */
    @PostMapping("/resignations")
    public Result<LifecycleDtos.ResignationVO> createResignation(
            @RequestBody LifecycleDtos.ResignationCreateRequest body) {
        return Result.success(resignationService.createResignation(body));
    }

    /**
     * GET /api/v1/resignations
     * 分页查询正式离职单列表（审批中 / 待离职 / 已离职等），供管理台「正式离职」Tab。
     */
    @GetMapping("/resignations")
    public Result<PageResult<LifecycleDtos.ResignationVO>> listResignations(
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status) {
        return Result.success(resignationService.listResignations(page, pageSize, status));
    }

    /**
     * GET /api/v1/resignations/stats
     * 离职管理台顶部统计卡片：待受理员工申请数、审批中、待离职、本月已离职。
     */
    @GetMapping("/resignations/stats")
    public Result<LifecycleDtos.ResignationStatsVO> stats() {
        return Result.success(resignationService.stats());
    }

    /**
     * GET /api/v1/resignations/{id}
     * 查询单条正式离职单详情（离职日、交接人、审批 instanceId、状态等）。
     */
    @GetMapping("/resignations/{id}")
    public Result<LifecycleDtos.ResignationVO> detail(@PathVariable("id") Long id) {
        return Result.success(resignationService.resignationDetail(id));
    }

    /**
     * POST /api/v1/resignations/effect-due
     * 手动触发到期离职生效（联调/补跑）：{@code resignationDate ≤ 今天} 且status=PENDING_RESIGN 的单据改员工状态(40)、禁账号并发 MQ。
     */
    @PostMapping("/resignations/effect-due")
    public Result<Integer> effectDue() {
        return Result.success(resignationService.effectDueResignations(java.time.LocalDate.now()));
    }
}

