package com.company.hrms.common.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;

/**
 * 审批终态事件：同进程 {@code @EventListener} 消费；MQ 关闭时不依赖 Broker。
 */
@Getter
public class ApprovalCompletedEvent extends ApplicationEvent {

    private final String processType;
    private final Long instanceId;
    private final Long businessId;
    private final String result;
    private final Long applicantId;
    private final LocalDateTime completedAt;
    private final String comment;

    public ApprovalCompletedEvent(Object source,
                                  String processType,
                                  Long instanceId,
                                  Long businessId,
                                  String result,
                                  Long applicantId,
                                  String comment) {
        super(source);
        this.processType = processType;
        this.instanceId = instanceId;
        this.businessId = businessId;
        this.result = result;
        this.applicantId = applicantId;
        this.completedAt = LocalDateTime.now();
        this.comment = comment;
    }
}
