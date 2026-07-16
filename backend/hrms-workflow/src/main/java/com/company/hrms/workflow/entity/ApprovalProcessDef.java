package com.company.hrms.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 表 #12 approval_process_def
 */
@Data
@TableName("approval_process_def")
public class ApprovalProcessDef {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String processType;
    private String name;
    /** 审批节点配置 JSON */
    private String nodesJson;
    private Integer slaHours;
    /** 1=启用 */
    private Integer status;
}
