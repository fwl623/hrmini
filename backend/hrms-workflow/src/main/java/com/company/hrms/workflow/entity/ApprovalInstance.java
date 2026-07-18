package com.company.hrms.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 表 #32 approval_instance
 */
@Data
@TableName("approval_instance")
public class ApprovalInstance {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String processType;
    private String businessKey;
    private String title;
    private String applicantName;
    private String applicantDept;
    private String businessNo;
    private String businessSummary;
    private String nodesJson;
    private String status;
    private Long initiatorId;
    private Integer currentNode;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
