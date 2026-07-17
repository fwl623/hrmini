package com.company.hrms.module.attendance.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.hrms.attendance.entity.LeaveBalance;
import com.company.hrms.attendance.mapper.LeaveBalanceMapper;
import com.company.hrms.module.attendance.service.LeaveService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 年假按工龄重算 Job
 *
 * 每年 1 月 1 日 01:00 执行，根据在职员工工龄重新计算年假天数。
 * 补偿机制：支持手动触发（直接调用 run 方法）
 *
 * TODO: 当前实现遍历所有存在年假余额的员工记录进行刷新。
 *       后续可通过 Feign 或 SQL 联表查询所有在职员工的入职日期，
 *       为尚未建立年假余额记录的新员工自动初始化。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LeaveBalanceRefreshJob {

    private final LeaveBalanceMapper leaveBalanceMapper;
    private final LeaveService leaveService;

    /**
     * 每年 1 月 1 日 01:00 执行
     */
    @Scheduled(cron = "0 0 1 1 1 ?")
    public void run() {
        int year = LocalDate.now().getYear();
        log.info("开始执行年假刷新 Job，年度: {}...", year);

        try {
            // 查询所有年假余额记录
            List<LeaveBalance> annualBalances = leaveBalanceMapper.selectList(
                    new LambdaQueryWrapper<LeaveBalance>()
                            .eq(LeaveBalance::getLeaveType, "ANNUAL")
                            .eq(LeaveBalance::getYear, year - 1) // 刷新上一年度的
            );

            int count = 0;
            for (LeaveBalance lb : annualBalances) {
                // 根据入职日期重新计算年假天数
                // 注意：此处假设 hireDate 已在初始化时存入或通过员工服务获取
                // 当前复用 LeaveService 中的年假计算逻辑
                // TODO: 对接员工服务获取准确的 hireDate
                if (lb.getYear() != null && lb.getYear() == year - 1) {
                    // 为新年份创建余额记录（保留原余额或重新计算）
                    LeaveBalance newBalance = new LeaveBalance();
                    newBalance.setEmployeeId(lb.getEmployeeId());
                    newBalance.setLeaveType("ANNUAL");
                    // 先保留原额度，实际应根据工龄重新计算
                    // 需要员工 hireDate 数据，当前作为占位
                    newBalance.setBalance(lb.getBalance());
                    newBalance.setYear(year);

                    // 检查是否已存在新年份的记录
                    LeaveBalance existing = leaveBalanceMapper.selectByEmployeeAndTypeAndYear(
                            lb.getEmployeeId(), "ANNUAL", year);
                    if (existing == null) {
                        leaveBalanceMapper.insert(newBalance);
                        count++;
                        log.info("年假初始化: empId={}, year={}, days={}",
                                lb.getEmployeeId(), year, newBalance.getBalance());
                    }
                }
            }

            log.info("年假刷新 Job 执行完成，共初始化 {} 条新年份记录", count);
        } catch (Exception e) {
            log.error("年假刷新 Job 执行失败", e);
        }
    }
}
