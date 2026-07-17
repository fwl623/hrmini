package com.company.hrms.module.attendance.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.hrms.attendance.entity.LeaveBalance;
import com.company.hrms.attendance.mapper.LeaveBalanceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 调休过期清零 Job
 *
 * 每月 1 日 02:00 执行，将已过期的调休余额清零。
 * 补偿机制：支持手动触发（直接调用 run 方法）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompensatoryLeaveExpireJob {

    private final LeaveBalanceMapper leaveBalanceMapper;

    /**
     * 每月 1 日 02:00 执行
     */
    @Scheduled(cron = "0 0 2 1 * ?")
    public void run() {
        log.info("开始执行调休过期清零 Job...");
        LocalDate now = LocalDate.now();
        try {
            // 查询所有已过期的调休记录
            List<LeaveBalance> expiredList = leaveBalanceMapper.selectList(
                    new LambdaQueryWrapper<LeaveBalance>()
                            .eq(LeaveBalance::getLeaveType, "COMP_OFF")
                            .lt(LeaveBalance::getExpireDate, now)
                            .gt(LeaveBalance::getBalance, BigDecimal.ZERO)
            );

            int count = 0;
            for (LeaveBalance lb : expiredList) {
                lb.setBalance(BigDecimal.ZERO);
                leaveBalanceMapper.updateById(lb);
                count++;
                log.info("调休过期清零: id={}, empId={}, expireDate={}",
                        lb.getId(), lb.getEmployeeId(), lb.getExpireDate());
            }

            log.info("调休过期清零 Job 执行完成，共处理 {} 条记录", count);
        } catch (Exception e) {
            log.error("调休过期清零 Job 执行失败", e);
        }
    }
}
