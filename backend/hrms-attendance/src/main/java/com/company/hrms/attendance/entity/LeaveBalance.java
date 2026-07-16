package com.company.hrms.attendance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 假期余额表
 * DDL: leave_balance (#46)
 */
@Data
@TableName("leave_balance")
public class LeaveBalance {

    /**
     * 主键ID，自增
     * 唯一标识每条假期余额记录
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 员工ID，关联 employee 表主键
     * 用于标识该假期余额所属的员工
     */
    @TableField("employee_id")
    private Long employeeId;

    /**
     * 假期类型
     * <p>可选值：</p>
     * <ul>
     *   <li><b>ANNUAL</b> - 年假，按年度发放，可用于年假申请</li>
     *   <li><b>COMP_OFF</b> - 调休，由加班等产生，有过期日期</li>
     * </ul>
     */
    @TableField("leave_type")
    private String leaveType;

    /**
     * 假期剩余余额
     * 单位为天（精确到小数，例如 2.5 表示两天半）
     * 年假时此值为该年度剩余年假天数；调休时为此调休剩余天数
     */
    @TableField("balance")
    private BigDecimal balance;

    /**
     * 年假所属年度（格式：yyyy）
     * 仅 leave_type=ANNUAL 时有效，表示该年假余额所属的财政/自然年度
     * 用于按年度结算和结转年假余额
     */
    @TableField("year")
    private Integer year;

    /**
     * 调休过期日期（格式：yyyy-MM-dd）
     * 仅 leave_type=COMP_OFF 时有效，超过此日期未使用的调休余额将自动清零
     */
    @TableField("expire_date")
    private LocalDate expireDate;

    /**
     * 记录最后更新时间（格式：yyyy-MM-dd HH:mm:ss）
     * 每次对该行余额数据进行插入或更新操作时自动填充
     */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
