package com.company.hrms.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 表 #34 approval_log
 */
@Data
@TableName("approval_log")
public class ApprovalLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long instanceId;
    private Long taskId;
    private Long operatorId;
    private Long onBehalfOfId;
    private String displayText;
    private String action;
    private String comment;
    private String fromStatus;
    private String toStatus;
    private LocalDateTime createdAt;
}
