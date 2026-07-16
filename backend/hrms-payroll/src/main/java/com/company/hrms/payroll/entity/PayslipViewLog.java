package com.company.hrms.payroll.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工资条查看日志
 * DDL: payslip_view_log (#53)
 * 记录员工/用户查阅工资条的完整操作轨迹，包括查看时间、验证方式等信息，
 * 用于审计和合规追溯。
 */
@Data
@TableName("payslip_view_log")
public class PayslipViewLog {

    /**
     * 主键ID
     * <p>自增主键，唯一标识一条工资条查看日志记录。</p>
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 员工ID
     * <p>关联 employee 表主键，标识查看工资条所属的员工。</p>
     */
    @TableField("employee_id")
    private Long employeeId;

    /**
     * 批次ID
     * <p>关联 payslip_batch 表主键，标识本次查看对应的工资发放批次。</p>
     */
    @TableField("batch_id")
    private Long batchId;

    /**
     * 查看时间
     * <p>员工点击查看工资条的具体时间戳。</p>
     * <p>格式：yyyy-MM-dd HH:mm:ss</p>
     */
    @TableField("viewed_at")
    private LocalDateTime viewedAt;

    /**
     * 验证方式
     * <p>员工查看工资条时使用的身份验证方式。</p>
     * <ul>
     *   <li><strong>PASSWORD</strong> - 密码验证：员工通过登录密码验证身份后查看</li>
     *   <li><strong>SMS</strong> - 短信验证：员工通过手机短信验证码验证身份后查看</li>
     * </ul>
     */
    @TableField("verify_method")
    private String verifyMethod;
}
