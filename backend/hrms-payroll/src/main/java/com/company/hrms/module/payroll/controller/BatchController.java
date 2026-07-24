package com.company.hrms.module.payroll.controller;

import com.company.hrms.common.exception.BusinessException;
import com.company.hrms.common.exception.ErrorCode;
import com.company.hrms.common.security.SecurityUtils;
import com.company.hrms.common.util.ExcelExportUtil;
import com.company.hrms.common.web.PageParam;
import com.company.hrms.common.web.PageResult;
import com.company.hrms.common.web.Result;
import com.company.hrms.module.payroll.dto.*;
import com.company.hrms.module.payroll.job.PayrollEventPublisher;
import com.company.hrms.module.payroll.service.CalculateService;
import com.company.hrms.payroll.entity.PayrollBatch;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/payroll/batches")
@RequiredArgsConstructor
public class BatchController {

    private final CalculateService calculateService;
    private final PayrollEventPublisher payrollEventPublisher;

    /** 与 {@code PayrollCalculateConsumer} 同开关：MQ 关闭时直接同步 */
    @Value("${hrms.rabbitmq.enabled:false}")
    private boolean rabbitEnabled;

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

    /**
     * 开始/重试核算。策略：
     * <ol>
     *   <li>草稿 → 优先投递 MQ 异步；MQ 关/投递失败 → 同步降级</li>
     *   <li>已卡在 CALCULATING → 不再等 MQ，直接同步重算</li>
     * </ol>
     */
    @PostMapping("/{id}/calculate")
    public Result<Map<String, String>> calculate(@PathVariable Long id) {
        PayrollBatch batch = calculateService.getBatch(id);
        boolean stuckCalculating = "CALCULATING".equals(batch.getStatus());
        if (!"DRAFT".equals(batch.getStatus()) && !stuckCalculating) {
            throw new BusinessException(ErrorCode.PAYROLL_IN_PROGRESS, "仅草稿或计算中状态可开始/重试计算");
        }

        if ("DRAFT".equals(batch.getStatus())) {
            calculateService.validateBeforeCalculate(batch);
            batch.setStatus("CALCULATING");
            calculateService.updateBatchStatus(batch);
        }

        // 已卡死：异步消息多半丢失或消费失败，直接同步降级
        if (stuckCalculating) {
            calculateService.calculate(id);
            return Result.success(Map.of(
                    "message", "异步未完成，已降级同步重算完成",
                    "mode", "sync-fallback"));
        }

        // 正常发起：先尝试异步
        if (rabbitEnabled && payrollEventPublisher.sendCalculate(id, batch.getPeriod())) {
            return Result.success(Map.of(
                    "message", "计算任务已提交，请稍后刷新",
                    "mode", "async"));
        }

        // MQ 关闭 / RabbitTemplate 不可用 / 发送失败 → 同步降级
        calculateService.calculate(id);
        return Result.success(Map.of(
                "message", "异步不可用，已降级同步计算完成",
                "mode", "sync-fallback"));
    }

    @GetMapping("/{id}/details")
    public Result<PageResult<PayrollDetailVO>> details(@PathVariable Long id, PageParam pageParam) {
        return Result.success(calculateService.getDetails(id, pageParam));
    }

    @GetMapping("/{id}/details/export-excel")
    public ResponseEntity<byte[]> exportDetails(@PathVariable Long id) {
        List<PayrollDetailExportVO> list = calculateService.exportDetails(id);
        byte[] bytes = ExcelExportUtil.generateExcelBytes("批次明细", list, PayrollDetailExportVO.class);
        String fileName = URLEncoder.encode("批次明细-" + id, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename*=utf-8''" + fileName + ".xlsx")
                .body(bytes);
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
