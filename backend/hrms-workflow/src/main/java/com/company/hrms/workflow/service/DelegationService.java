package com.company.hrms.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.hrms.common.exception.BusinessException;
import com.company.hrms.common.exception.ErrorCode;
import com.company.hrms.common.web.PageResult;
import com.company.hrms.workflow.dto.ApprovalDtos;
import com.company.hrms.workflow.entity.ApprovalDelegation;
import com.company.hrms.workflow.mapper.ApprovalDelegationMapper;
import com.company.hrms.workflow.support.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 审批委托：同时仅 1 条 ACTIVE；委托期内 resolveAssignee 返回被委托人。
 */
@Service
public class DelegationService {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_CANCELLED = "CANCELLED";

    private final ApprovalDelegationMapper delegationMapper;
    private final CurrentUserProvider currentUserProvider;

    public DelegationService(ApprovalDelegationMapper delegationMapper,
                             CurrentUserProvider currentUserProvider) {
        this.delegationMapper = delegationMapper;
        this.currentUserProvider = currentUserProvider;
    }

    public PageResult<ApprovalDtos.DelegationVO> list(long userId, int page, int pageSize) {
        List<ApprovalDelegation> rows = delegationMapper.selectList(new LambdaQueryWrapper<ApprovalDelegation>()
                .eq(ApprovalDelegation::getDelegatorId, userId)
                .orderByDesc(ApprovalDelegation::getId));
        List<ApprovalDtos.DelegationVO> list = rows.stream().map(this::toVo).collect(Collectors.toList());
        int p = page <= 0 ? 1 : page;
        int size = pageSize <= 0 ? 20 : Math.min(pageSize, 100);
        int from = Math.min((p - 1) * size, list.size());
        int to = Math.min(from + size, list.size());
        return PageResult.of(list.subList(from, to), list.size(), p, size);
    }

    @Transactional
    public ApprovalDtos.DelegationVO create(long delegatorId, ApprovalDtos.DelegationFormRequest body) {
        if (body == null || body.getDelegateUserId() == null
                || body.getStartDate() == null || body.getEndDate() == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "被委托人与起止日期必填");
        }
        if (body.getDelegateUserId().equals(delegatorId)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "被委托人不能是本人");
        }
        if (body.getEndDate().isBefore(body.getStartDate())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "结束日期不能早于开始日期");
        }
        expireOverdue(LocalDate.now());
        Long activeCount = delegationMapper.selectCount(new LambdaQueryWrapper<ApprovalDelegation>()
                .eq(ApprovalDelegation::getDelegatorId, delegatorId)
                .eq(ApprovalDelegation::getStatus, STATUS_ACTIVE));
        if (activeCount != null && activeCount > 0) {
            throw new BusinessException(ErrorCode.DELEGATION_CONFLICT);
        }

        ApprovalDelegation row = new ApprovalDelegation();
        row.setDelegatorId(delegatorId);
        row.setDelegateUserId(body.getDelegateUserId());
        row.setStartDate(body.getStartDate());
        row.setEndDate(body.getEndDate());
        row.setReason(body.getReason());
        row.setStatus(STATUS_ACTIVE);
        row.setCreatedAt(LocalDateTime.now());
        delegationMapper.insert(row);
        return toVo(row);
    }

    @Transactional
    public void cancel(long id, long operatorId) {
        ApprovalDelegation row = delegationMapper.selectById(id);
        if (row == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "委托不存在");
        }
        if (!row.getDelegatorId().equals(operatorId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅委托人可取消");
        }
        if (STATUS_CANCELLED.equalsIgnoreCase(row.getStatus())) {
            return;
        }
        row.setStatus(STATUS_CANCELLED);
        row.setCancelledAt(LocalDateTime.now());
        delegationMapper.updateById(row);
    }

    /**
     * 委托期内：actualAssignee = delegateUserId；否则返回原 assignee。
     */
    public long resolveAssignee(long assigneeUserId) {
        return resolveAssignee(assigneeUserId, LocalDate.now());
    }

    public long resolveAssignee(long assigneeUserId, LocalDate onDate) {
        LocalDate day = onDate == null ? LocalDate.now() : onDate;
        expireOverdue(day);
        return findActive(assigneeUserId, day)
                .map(ApprovalDelegation::getDelegateUserId)
                .orElse(assigneeUserId);
    }

    public Optional<ApprovalDelegation> findActive(long delegatorId, LocalDate onDate) {
        LocalDate day = onDate == null ? LocalDate.now() : onDate;
        return delegationMapper.selectList(new LambdaQueryWrapper<ApprovalDelegation>()
                        .eq(ApprovalDelegation::getDelegatorId, delegatorId)
                        .eq(ApprovalDelegation::getStatus, STATUS_ACTIVE)
                        .le(ApprovalDelegation::getStartDate, day)
                        .ge(ApprovalDelegation::getEndDate, day)
                        .orderByDesc(ApprovalDelegation::getId)
                        .last("LIMIT 1"))
                .stream()
                .findFirst();
    }

    /**
     * 将 endDate &lt; onDate 的 ACTIVE 委托自动置为 CANCELLED（含边界：结束日当天仍有效）。
     */
    @Transactional
    public int expireOverdue(LocalDate onDate) {
        LocalDate day = onDate == null ? LocalDate.now() : onDate;
        List<ApprovalDelegation> expired = delegationMapper.selectList(new LambdaQueryWrapper<ApprovalDelegation>()
                .eq(ApprovalDelegation::getStatus, STATUS_ACTIVE)
                .lt(ApprovalDelegation::getEndDate, day));
        int n = 0;
        LocalDateTime now = LocalDateTime.now();
        for (ApprovalDelegation row : expired) {
            row.setStatus(STATUS_CANCELLED);
            row.setCancelledAt(now);
            delegationMapper.updateById(row);
            n++;
        }
        return n;
    }

    private ApprovalDtos.DelegationVO toVo(ApprovalDelegation row) {
        ApprovalDtos.DelegationVO vo = new ApprovalDtos.DelegationVO();
        vo.setId(row.getId());
        vo.setDelegatorId(row.getDelegatorId());
        vo.setDelegatorName(currentUserProvider.displayName(row.getDelegatorId()));
        vo.setDelegateUserId(row.getDelegateUserId());
        vo.setDelegateUserName(currentUserProvider.displayName(row.getDelegateUserId()));
        vo.setStartDate(row.getStartDate() == null ? null : row.getStartDate().toString());
        vo.setEndDate(row.getEndDate() == null ? null : row.getEndDate().toString());
        vo.setReason(row.getReason());
        vo.setStatus(row.getStatus() == null ? null : row.getStatus().toUpperCase(Locale.ROOT));
        vo.setCreatedAt(row.getCreatedAt() == null ? null
                : row.getCreatedAt().toString().replace('T', ' ').substring(0, Math.min(19, row.getCreatedAt().toString().length())));
        return vo;
    }
}
