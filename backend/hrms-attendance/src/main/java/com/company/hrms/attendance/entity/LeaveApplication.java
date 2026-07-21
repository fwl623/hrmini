package com.company.hrms.attendance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 请假申请表
 * DDL: leave_application (#47)
 */
@Data
@TableName("leave_application")
public class LeaveApplication {

    /**
     * 主键ID，自增
     * 唯一标识一条请假申请记录
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 员工ID，关联 employee 表主键
     * 用于标识该请假申请所属的员工，不可为空
     */
    @TableField("employee_id")
    private Long employeeId;

    /**
     * 请假类型
     * 可选值：
     * - ANNUAL：年假
     * - SICK：病假
     * - PERSONAL：事假
     * - MARRIAGE：婚假
     * - MATERNITY：产假
     * - BEREAVEMENT：丧假
     * - COMP_OFF：调休/补休
     */
    @TableField("leave_type")
    private String leaveType;

    /**
     * 请假开始时间
     * 格式：yyyy-MM-dd HH:mm:ss
     * 记录请假周期的起始时刻
     */
    @TableField("start_time")
    private LocalDateTime startTime;

    /**
     * 请假结束时间
     * 格式：yyyy-MM-dd HH:mm:ss
     * 记录请假周期的结束时刻，必须晚于 startTime
     */
    @TableField("end_time")
    private LocalDateTime endTime;

    /**
     * 请假天数，单位为天
     * 支持 0.5 天（半天假），精度为 BigDecimal
     * 计算公式：endTime - startTime 折算为天数
     */
    @TableField("leave_days")
    private BigDecimal leaveDays;

    /**
     * 请假原因
     * 员工填写的请假事由说明文本
     */
    @TableField("reason")
    private String reason;

    /**
     * 工作交接人员工ID，关联 employee 表主键
     * 请假期间负责接手该员工工作的同事，可为空
     */
    @TableField("handover_employee_id")
    private Long handoverEmployeeId;

    /**
     * 附件URL
     * 请假相关证明文件（如病假单、婚假证明等）的存储访问地址
     */
    @TableField("attachment_url")
    private String attachmentUrl;

    /**
     * 审批状态
     * 可选值：
     * - PENDING：待审批
     * - APPROVED：已通过
     * - REJECTED：已驳回
     * - CANCELLED：已撤销
     */
    @TableField("status")
    private String status;

    /** 撤销原因：WITHDRAW(审批中撤回)/REVOKE(通过后撤销) */
    @TableField("cancel_reason")
    private String cancelReason;

    /** 扣减时的余额快照（JSON，审计用） */
    @TableField("deducted_balance_snapshot")
    private String deductedBalanceSnapshot;

    /**
     * 审批实例ID，关联审批流程实例表主键
     */
    @TableField("instance_id")
    private Long instanceId;

    /**
     * 创建时间
     * 格式：yyyy-MM-dd HH:mm:ss
     * 记录该请假申请的提交时间，系统自动生成
     */
    @TableField("created_at")
    private LocalDateTime createdAt;
}
