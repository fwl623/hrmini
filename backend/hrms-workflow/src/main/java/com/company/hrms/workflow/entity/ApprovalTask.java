package com.company.hrms.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 表 #33 approval_task
 */
@Data
@TableName("approval_task")
public class ApprovalTask {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long instanceId;
    private Integer nodeOrder;
    private Long assigneeId;
    private Long actualAssigneeId;
    private String status;
    private String comment;
    private LocalDateTime slaDeadline;
    private Integer overdue;
    private LocalDateTime completedAt;
}
