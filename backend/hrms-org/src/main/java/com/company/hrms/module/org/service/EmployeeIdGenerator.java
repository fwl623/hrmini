package com.company.hrms.module.org.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.hrms.common.exception.BusinessException;
import com.company.hrms.common.exception.ErrorCode;
import com.company.hrms.module.org.constant.OrgRedisKeys;
import com.company.hrms.module.org.entity.EmployeeNoHistory;
import com.company.hrms.module.org.mapper.OrgEmployeeNoHistoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 工号生成器：YYYY + deptCode(2) + seq(3)。
 * Redis 锁在事务提交后再释放，避免并发读到未提交的 maxSeq。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeIdGenerator {

    private static final long LOCK_SECONDS = 15L;
    private static final int MAX_LOCK_RETRY = 20;

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final OrgEmployeeNoHistoryMapper employeeNoHistoryMapper;
    private final StringRedisTemplate redisTemplate;

    /**
     * 生成并占用工号（写入 history，reuse_flag=0）。
     *
     * @param deptCode   2 位部门编码
     * @param employeeId 可空；入职时可后补
     */
    @Transactional(rollbackFor = Exception.class)
    public String generate(String deptCode, Long employeeId) {
        if (!StringUtils.hasText(deptCode) || !deptCode.matches("^[A-Za-z0-9]{2}$")) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "部门编码须为 2 位字母或数字");
        }
        String code = deptCode.toUpperCase();
        String year = String.valueOf(LocalDate.now().getYear());
        String lockKey = OrgRedisKeys.empSeqLock(year, code);
        String lockValue = UUID.randomUUID().toString();

        if (!tryLock(lockKey, lockValue)) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "工号生成繁忙，请稍后重试");
        }

        boolean unlockRegistered = false;
        try {
            registerUnlockAfterCompletion(lockKey, lockValue);
            unlockRegistered = true;

            EmployeeNoHistory reusable = employeeNoHistoryMapper.selectOneReusable(year, code);
            if (reusable != null) {
                reusable.setReuseFlag(0);
                reusable.setEmployeeId(employeeId);
                employeeNoHistoryMapper.updateById(reusable);
                log.info("复用工号 {} for employeeId={}", reusable.getEmployeeNo(), employeeId);
                return reusable.getEmployeeNo();
            }

            Integer maxSeq = employeeNoHistoryMapper.selectMaxSeq(year, code);
            int next = (maxSeq == null ? 0 : maxSeq) + 1;
            if (next > 999) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "当年该部门工号序号已用尽");
            }
            String employeeNo = year + code + String.format("%03d", next);
            EmployeeNoHistory history = new EmployeeNoHistory();
            history.setEmployeeNo(employeeNo);
            history.setYear(year);
            history.setDeptCode(code);
            history.setEmployeeId(employeeId);
            history.setReuseFlag(0);
            history.setCreatedAt(LocalDateTime.now());
            employeeNoHistoryMapper.insert(history);
            log.info("新生成工号 {} for employeeId={}", employeeNo, employeeId);
            return employeeNo;
        } catch (RuntimeException ex) {
            if (!unlockRegistered) {
                releaseLock(lockKey, lockValue);
            }
            throw ex;
        }
    }

    public String generate(String deptCode) {
        return generate(deptCode, null);
    }

    /**
     * 离职释放工号：将 history 标记为可复用（reuse_flag=1）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void release(String employeeNo) {
        if (!StringUtils.hasText(employeeNo)) {
            return;
        }
        EmployeeNoHistory history = employeeNoHistoryMapper.selectOne(
                new LambdaQueryWrapper<EmployeeNoHistory>()
                        .eq(EmployeeNoHistory::getEmployeeNo, employeeNo.trim())
                        .last("LIMIT 1"));
        if (history == null) {
            log.warn("释放工号未找到历史记录 employeeNo={}", employeeNo);
            return;
        }
        if (history.getReuseFlag() != null && history.getReuseFlag() == 1) {
            return;
        }
        history.setReuseFlag(1);
        employeeNoHistoryMapper.updateById(history);
        log.info("已释放工号可复用 employeeNo={} employeeId={}", employeeNo, history.getEmployeeId());
    }

    /**
     * 事务提交或回滚后再释放锁；无事务时立即释放。
     */
    private void registerUnlockAfterCompletion(String lockKey, String lockValue) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            releaseLock(lockKey, lockValue);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                releaseLock(lockKey, lockValue);
            }
        });
    }

    private boolean tryLock(String lockKey, String lockValue) {
        for (int i = 0; i < MAX_LOCK_RETRY; i++) {
            Boolean ok = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, lockValue, LOCK_SECONDS, TimeUnit.SECONDS);
            if (Boolean.TRUE.equals(ok)) {
                return true;
            }
            try {
                Thread.sleep(50L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private void releaseLock(String lockKey, String lockValue) {
        try {
            redisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(lockKey), lockValue);
        } catch (Exception ex) {
            log.warn("释放工号锁失败 key={}: {}", lockKey, ex.getMessage());
        }
    }
}
