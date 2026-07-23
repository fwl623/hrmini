package com.company.hrms.module.payroll.controller;

import com.company.hrms.common.exception.BusinessException;
import com.company.hrms.common.exception.ErrorCode;
import com.company.hrms.common.security.SecurityUtils;
import com.company.hrms.common.web.PageParam;
import com.company.hrms.common.web.PageResult;
import com.company.hrms.common.web.Result;
import com.company.hrms.module.payroll.dto.*;
import com.company.hrms.module.payroll.job.PayrollEventPublisher;
import com.company.hrms.module.payroll.service.CalculateService;
import com.company.hrms.payroll.entity.PayrollBatch;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/payroll/batches")
@RequiredArgsConstructor
public class BatchController {

    private final CalculateService calculateService;
    private final PayrollEventPublisher payrollEventPublisher;

    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody BatchCreateDTO dto) {
        Long operatorId = SecurityUtils.getCurrentUser().getEmployeeId();
        var batch = calculateService.createBatch(dto.getPeriod(), operatorId);
        return Result.success(Map.of("id", batch.getId(), "status", batch.getStatus()));
    }

    @GetMapping
    public Result<Object> list(PageParam pageParam,
                               @RequestParam(required = false) String period) {
        var page = calculateService.pageBatches(pageParam, period);
        return Result.success(Map.of("list", page.getRecords(), "total", page.getTotal()));
    }

    @GetMapping("/{id}")
    public Result<Object> detail(@PathVariable Long id) {
        var batch = calculateService.getBatch(id);
        return Result.success(Map.of(
                "id", batch.getId(), "period", batch.getPeriod(),
                "status", batch.getStatus(), "totalCount", batch.getTotalCount(),
                "successCount", batch.getSuccessCount(), "anomalyCount", batch.getAnomalyCount(),
                "progress", "DISTRIBUTED".equals(batch.getStatus()) ? 100 :
                           "PENDING_CONFIRM".equals(batch.getStatus()) ? 100 :
                           "DRAFT".equals(batch.getStatus()) ? 0 : 60
        ));
    }

    @PostMapping("/{id}/calculate")
    public Result<Map<String, String>> calculate(@PathVariable Long id) {
        // 异步核算：通过 MQ 发送消息，消费者异步执行核算
        PayrollBatch batch = calculateService.getBatch(id);
        if (!"DRAFT".equals(batch.getStatus())) {
            throw new BusinessException(ErrorCode.PAYROLL_IN_PROGRESS, "仅草稿状态可开始计算");
        }
        // 前置校验（同步执行）
        calculateService.validateBeforeCalculate(batch);
        // 状态改为 CALCULATING 后异步算
        batch.setStatus("CALCULATING");
        calculateService.updateBatchStatus(batch);
        payrollEventPublisher.sendCalculate(id, batch.getPeriod());
        return Result.success(Map.of("message", "计算任务已提交"));
    }

    @GetMapping("/{id}/details")
    public Result<PageResult<PayrollDetailVO>> details(@PathVariable Long id, PageParam pageParam) {
        return Result.success(calculateService.getDetails(id, pageParam));
    }

    @GetMapping("/{id}/chart-data")
    public Result<ChartDataVO> chartData(@PathVariable Long id) {
        return Result.success(calculateService.getChartData(id));
    }

    @PutMapping("/{id}/details/{detailId}")
    public Result<java.util.Map<String, String>> adjust(@PathVariable Long id, @PathVariable Long detailId,
                               @RequestBody AdjustmentDTO dto) {
        Long operatorId = SecurityUtils.getCurrentUser().getEmployeeId();
        calculateService.adjust(id, detailId, dto, operatorId);
        return Result.success(Map.of("manualAdjusted", "true"));
    }

    @PostMapping("/{id}/submit")
    public Result<Map<String, String>> submit(@PathVariable Long id) {
        calculateService.submit(id);
        return Result.success(Map.of("status", "APPROVING"));
    }

    @PostMapping("/{id}/approve")
    public Result<Map<String, String>> approve(@PathVariable Long id) {
        calculateService.approve(id);
        return Result.success(Map.of("status", "APPROVED"));
    }

    @PostMapping("/{id}/distribute")
    public Result<Map<String, String>> distribute(@PathVariable Long id) {
        calculateService.distribute(id);
        return Result.success(Map.of("status", "DISTRIBUTED"));
    }
}
