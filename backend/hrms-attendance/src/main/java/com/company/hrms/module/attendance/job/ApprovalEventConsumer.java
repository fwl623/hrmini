package com.company.hrms.module.attendance.job;

import com.company.hrms.module.attendance.service.LeaveService;
import com.company.hrms.module.attendance.service.PunchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 审批事件消费者（MQ）
 * 监听 hrms.approval.attendance 队列，处理请假/加班/补卡审批结果
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RabbitListener(queues = "hrms.approval.attendance")
public class ApprovalEventConsumer {

    private final LeaveService leaveService;
    private final PunchService punchService;

    /**
     * 处理审批事件消息
     * <p>审批通过 → 确认扣减余额 / 补卡确认</p>
     * <p>审批驳回 → 恢复预扣余额</p>
     *
     * @param message 审批事件消息内容
     */
    @RabbitHandler
    public void handleApprovalEvent(Map<String, Object> message) {
        log.info("收到审批事件消息: {}", message);
        // TODO: 实现审批事件消费逻辑
        // 审批通过 → 调用 LeaveService 确认扣减或 PunchService 补卡确认
        // 审批驳回 → 调用 LeaveService 恢复预扣余额
    }
}
