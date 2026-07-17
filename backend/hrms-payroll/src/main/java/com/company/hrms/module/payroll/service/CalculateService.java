package com.company.hrms.module.payroll.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.hrms.payroll.entity.PayrollAdjustment;
import com.company.hrms.payroll.entity.PayrollBatch;
import com.company.hrms.payroll.entity.PayrollDetail;
import com.company.hrms.payroll.mapper.PayrollAdjustmentMapper;
import com.company.hrms.payroll.mapper.PayrollBatchMapper;
import com.company.hrms.payroll.mapper.PayrollDetailMapper;
import com.company.hrms.common.exception.BusinessException;
import com.company.hrms.common.exception.ErrorCode;
import com.company.hrms.common.web.PageParam;
import com.company.hrms.common.web.PageResult;
import com.company.hrms.module.payroll.dto.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CalculateService {

    private final PayrollBatchMapper batchMapper;
    private final PayrollDetailMapper detailMapper;
    private final PayrollAdjustmentMapper adjustmentMapper;
    private final ObjectMapper objectMapper;

    // ========== 批次管理 ==========

    public PayrollBatch createBatch(String period, Long operatorId) {
        // 检查是否已存在
        PayrollBatch exist = batchMapper.selectOne(new LambdaQueryWrapper<PayrollBatch>()
                .eq(PayrollBatch::getPeriod, period));
        if (exist != null) {
            throw new BusinessException(ErrorCode.PAYROLL_BATCH_EXISTS, "该账期批次已存在");
        }
        PayrollBatch batch = new PayrollBatch();
        batch.setPeriod(period);
        batch.setStatus("DRAFT");
        batch.setTotalCount(0);
        batch.setSuccessCount(0);
        batch.setGrossTotal(BigDecimal.ZERO);
        batch.setNetTotal(BigDecimal.ZERO);
        batch.setAnomalyCount(0);
        batch.setCreatedBy(operatorId);
        batchMapper.insert(batch);
        log.info("创建批次: id={}, period={}", batch.getId(), period);
        return batch;
    }

    public com.baomidou.mybatisplus.core.metadata.IPage<PayrollBatch> pageBatches(PageParam pageParam, String period) {
        LambdaQueryWrapper<PayrollBatch> wrapper = new LambdaQueryWrapper<PayrollBatch>()
                .orderByDesc(PayrollBatch::getCreatedAt);
        if (period != null) {
            wrapper.eq(PayrollBatch::getPeriod, period);
        }
        return batchMapper.selectPage(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageParam.getPage(), pageParam.getPageSize()),
                wrapper);
    }

    public PayrollBatch getBatch(Long id) {
        PayrollBatch batch = batchMapper.selectById(id);
        if (batch == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "批次不存在");
        }
        return batch;
    }

    @Transactional(rollbackFor = Exception.class)
    public void calculate(Long id) {
        PayrollBatch batch = batchMapper.selectById(id);
        if (batch == null) throw new BusinessException(ErrorCode.PARAM_INVALID, "批次不存在");
        if (!"DRAFT".equals(batch.getStatus())) {
            throw new BusinessException(ErrorCode.PAYROLL_IN_PROGRESS, "仅草稿状态可开始计算");
        }
        batch.setStatus("CALCULATING");
        batchMapper.updateById(batch);

        // Mock 核算: 模拟数据
        try {
            Thread.sleep(100);
            batch.setStatus("PENDING_CONFIRM");
            batch.setTotalCount(50);
            batch.setSuccessCount(48);
            batch.setAnomalyCount(2);
            batch.setGrossTotal(new BigDecimal("780000.00"));
            batch.setNetTotal(new BigDecimal("649000.00"));
            batch.setAttendanceLocked(1);
            batchMapper.updateById(batch);

            // 生成 Mock 明细
            for (int i = 1; i <= 50; i++) {
                PayrollDetail detail = new PayrollDetail();
                detail.setBatchId(id);
                detail.setEmployeeId((long) i);
                detail.setCalcStatus("SUCCESS");
                detail.setGrossSalary(new BigDecimal("15600.00"));
                detail.setNetSalary(new BigDecimal("12980.00"));
                detail.setPrevNetSalary(new BigDecimal("12800.00"));
                detail.setManualAdjusted(0);
                try { detail.setDetailJson(objectMapper.writeValueAsString(java.util.List.of())); } catch (Exception e) { }
                List<String> flags = new ArrayList<>();
                if (i <= 2) flags.add("LEAVE_HIGH");
                try {
                    detail.setAnomalyFlags(objectMapper.writeValueAsString(flags));
                } catch (JsonProcessingException e) { /* ignore */ }
                detailMapper.insert(detail);
            }
            log.info("核算完成: batchId={}", id);
        } catch (Exception e) {
            batch.setStatus("DRAFT");
            batchMapper.updateById(batch);
            log.error("核算失败: batchId={}", id, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "核算失败");
        }
    }

    public PageResult<PayrollDetailVO> getDetails(Long batchId, PageParam pageParam) {
        LambdaQueryWrapper<PayrollDetail> wrapper = new LambdaQueryWrapper<PayrollDetail>()
                .eq(PayrollDetail::getBatchId, batchId);
        com.baomidou.mybatisplus.core.metadata.IPage<PayrollDetail> page = detailMapper.selectPage(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageParam.getPage(), pageParam.getPageSize()),
                wrapper);

        List<PayrollDetailVO> voList = page.getRecords().stream().map(d -> {
            PayrollDetailVO vo = new PayrollDetailVO();
            vo.setEmployeeId(d.getEmployeeId());
            vo.setEmployeeName(String.valueOf(d.getEmployeeId()));
            vo.setGrossSalary(d.getGrossSalary());
            vo.setNetSalary(d.getNetSalary());
            vo.setCalcStatus(d.getCalcStatus());
            vo.setManualAdjusted(d.getManualAdjusted() == 1);
            // 解析异常标记
            if (d.getAnomalyFlags() != null) {
                try {
                    vo.setAnomalyFlags(objectMapper.readValue(d.getAnomalyFlags(), List.class));
                } catch (Exception e) { /* ignore */ }
            } else {
                vo.setAnomalyFlags(Collections.emptyList());
            }
            return vo;
        }).collect(Collectors.toList());

        return PageResult.of(voList, page.getTotal(), pageParam);
    }

    public ChartDataVO getChartData(Long batchId) {
        ChartDataVO vo = new ChartDataVO();
        ChartDataVO.CostTrendItem t = new ChartDataVO.CostTrendItem();
        t.setPeriod("2026-07");
        t.setGrossTotal(780000);
        vo.getCostTrend().add(t);
        ChartDataVO.DeptDistItem d = new ChartDataVO.DeptDistItem();
        d.setDeptName("技术部");
        d.setGrossTotal(200000);
        vo.getDeptDistribution().add(d);
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    public void adjust(Long batchId, Long detailId, AdjustmentDTO dto, Long operatorId) {
        PayrollDetail detail = detailMapper.selectById(detailId);
        if (detail == null || !detail.getBatchId().equals(batchId)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "核算明细不存在");
        }
        PayrollAdjustment adj = new PayrollAdjustment();
        adj.setDetailId(detailId);
        adj.setItemCode(dto.getItemCode());
        adj.setAdjustAmount(BigDecimal.valueOf(dto.getAdjustAmount()));
        adj.setReason(dto.getReason());
        adj.setOperatorId(operatorId);
        adjustmentMapper.insert(adj);

        detail.setManualAdjusted(1);
        detailMapper.updateById(detail);
        log.info("手工调整: batchId={}, detailId={}, amount={}", batchId, detailId, dto.getAdjustAmount());
    }

    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        PayrollBatch batch = batchMapper.selectById(id);
        if (batch == null) throw new BusinessException(ErrorCode.PARAM_INVALID, "批次不存在");
        if (!"PENDING_CONFIRM".equals(batch.getStatus())) {
            throw new BusinessException(ErrorCode.PAYROLL_IN_PROGRESS, "仅待确认状态可提交审批");
        }
        batch.setStatus("APPROVING");
        batchMapper.updateById(batch);
        log.info("提交审批: batchId={}", id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void distribute(Long id) {
        PayrollBatch batch = batchMapper.selectById(id);
        if (batch == null) throw new BusinessException(ErrorCode.PARAM_INVALID, "批次不存在");
        if (!"APPROVED".equals(batch.getStatus())) {
            throw new BusinessException(ErrorCode.PAYROLL_IN_PROGRESS, "仅已通过状态可发放");
        }
        batch.setStatus("DISTRIBUTED");
        batchMapper.updateById(batch);
        log.info("发放确认: batchId={}", id);
    }
}
