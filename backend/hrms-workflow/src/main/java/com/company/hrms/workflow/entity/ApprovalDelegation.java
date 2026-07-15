package com.company.hrms.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 表 #35 approval_delegation
 */
@Data
@TableName("approval_delegation")
public class ApprovalDelegation {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long delegatorId;
    private Long delegateUserId;
    private LocalDate startDate;
    private LocalDate endDate;
    private String reason;
    private String status;
    private LocalDateTime cancelledAt;
    private LocalDateTime createdAt;
}
