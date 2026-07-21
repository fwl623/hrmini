package com.company.hrms.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.company.hrms.common.exception.BusinessException;
import com.company.hrms.common.exception.ErrorCode;
import com.company.hrms.common.web.PageResult;
import com.company.hrms.workflow.dto.ApprovalDtos;
import com.company.hrms.workflow.entity.ApprovalDelegation;
import com.company.hrms.workflow.entity.ApprovalTask;
import com.company.hrms.workflow.mapper.ApprovalDelegationMapper;
import com.company.hrms.workflow.mapper.ApprovalTaskMapper;
import com.company.hrms.workflow.mapper.OrgLookupMapper;
import com.company.hrms.workflow.support.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 审批委托：同时仅 1 条 ACTIVE；委托期内 resolveAssignee 返回被委托人。
 * 被委托人须具备审批权限（角色或 permission）。
 * 创建时移交委托人当前 PENDING 待办；取消时回退。
 */
@Service
public class DelegationService {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_CANCELLED = "CANCELLED";

    private final ApprovalDelegationMapper delegationMapper;
    private final ApprovalTaskMapper taskMapper;
    private final CurrentUserProvider currentUserProvider;
    private final OrgLookupMapper orgLookupMapper;

    public DelegationService(ApprovalDelegationMapper delegationMapper,
                             ApprovalTaskMapper taskMapper,
                             CurrentUserProvider currentUserProvider,
                             OrgLookupMapper orgLookupMapper) {
        this.delegationMapper = delegationMapper;
        this.taskMapper = taskMapper;
        this.currentUserProvider = currentUserProvider;
        this.orgLookupMapper = orgLookupMapper;
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
        if (orgLookupMapper.countUserHasApproverCapability(body.getDelegateUserId()) <= 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "只能委托给有审批权限的启用账号");
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
        // 立即移交：委托人名下尚未转交的 PENDING 待办 → 被委托人
        handOverPendingTasks(delegatorId, body.getDelegateUserId());
        return toVo(row);
    }

    /**
     * 搜索可被委托的审批人（排除本人）。
     * keyword 可为空，返回前若干条供下拉初始展示。
     */
    public List<ApprovalDtos.DelegateCandidateVO> searchDelegateCandidates(long currentUserId, String keyword) {
        String kw = keyword == null ? "" : keyword.trim();
        if (kw.length() > 64) {
            kw = kw.substring(0, 64);
        }
        List<Map<String, Object>> rows = orgLookupMapper.searchDelegateCandidates(kw, currentUserId);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<ApprovalDtos.DelegateCandidateVO> list = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Long userId = toLong(row.get("userId"));
            if (userId == null) {
                continue;
            }
            ApprovalDtos.DelegateCandidateVO vo = new ApprovalDtos.DelegateCandidateVO();
            vo.setUserId(userId);
            vo.setName(row.get("name") == null ? null : String.valueOf(row.get("name")));
            vo.setUsername(row.get("username") == null ? null : String.valueOf(row.get("username")));
            vo.setEmpNo(row.get("empNo") == null ? null : String.valueOf(row.get("empNo")));
            vo.setDepartment(row.get("department") == null ? null : String.valueOf(row.get("department")));
            list.add(vo);
        }
        return list;
    }

    private static Long toLong(Object id) {
        if (id instanceof Number n) {
            return n.longValue();
        }
        if (id == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(id));
        } catch (NumberFormatException ignored) {
            return null;
        }
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
        reclaimPendingTasks(row.getDelegatorId(), row.getDelegateUserId());
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

    /**
     * 当前日期下，谁把审批委托给了 {@code delegateUserId}（用于待办可见范围）。
     */
    public List<Long> findActiveDelegatorIdsFor(long delegateUserId) {
        LocalDate day = LocalDate.now();
        expireOverdue(day);
        return delegationMapper.selectList(new LambdaQueryWrapper<ApprovalDelegation>()
                        .eq(ApprovalDelegation::getDelegateUserId, delegateUserId)
                        .eq(ApprovalDelegation::getStatus, STATUS_ACTIVE)
                        .le(ApprovalDelegation::getStartDate, day)
                        .ge(ApprovalDelegation::getEndDate, day))
                .stream()
                .map(ApprovalDelegation::getDelegatorId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());
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

    /** 创建委托：把委托人当前 PENDING 待办的实际审批人改为被委托人 */
    private void handOverPendingTasks(long delegatorId, long delegateUserId) {
        List<ApprovalTask> pending = taskMapper.selectList(new LambdaQueryWrapper<ApprovalTask>()
                .eq(ApprovalTask::getAssigneeId, delegatorId)
                .eq(ApprovalTask::getStatus, "PENDING")
                .and(w -> w.isNull(ApprovalTask::getActualAssigneeId)
                        .or()
                        .eq(ApprovalTask::getActualAssigneeId, delegatorId)));
        for (ApprovalTask task : pending) {
            task.setActualAssigneeId(delegateUserId);
            taskMapper.updateById(task);
        }
    }

    /** 取消委托：回退此前因委托写入的 actualAssigneeId */
    private void reclaimPendingTasks(long delegatorId, long delegateUserId) {
        List<ApprovalTask> pending = taskMapper.selectList(new LambdaQueryWrapper<ApprovalTask>()
                .eq(ApprovalTask::getAssigneeId, delegatorId)
                .eq(ApprovalTask::getActualAssigneeId, delegateUserId)
                .eq(ApprovalTask::getStatus, "PENDING"));
        for (ApprovalTask task : pending) {
            taskMapper.update(null, new LambdaUpdateWrapper<ApprovalTask>()
                    .eq(ApprovalTask::getId, task.getId())
                    .set(ApprovalTask::getActualAssigneeId, null));
        }
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
            reclaimPendingTasks(row.getDelegatorId(), row.getDelegateUserId());
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
