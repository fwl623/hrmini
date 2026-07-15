package com.company.hrms.attendance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 工作日配置
 * DDL: workday_config (#4)
 */
@Data
@TableName("workday_config")
public class WorkdayConfig {

    /**
     * 主键 ID，自增
     * 唯一标识一条工作日配置记录
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 星期几，对应实际自然周中的每一天
     * <p>取值范围及含义：</p>
     * <ul>
     *   <li>1 — 周一 (Monday)</li>
     *   <li>2 — 周二 (Tuesday)</li>
     *   <li>3 — 周三 (Wednesday)</li>
     *   <li>4 — 周四 (Thursday)</li>
     *   <li>5 — 周五 (Friday)</li>
     *   <li>6 — 周六 (Saturday)</li>
     *   <li>7 — 周日 (Sunday)</li>
     * </ul>
     * 该字段联合其他配置（如节假日表）共同决定某一天是否为工作日
     */
    @TableField("day_of_week")
    private Integer dayOfWeek;

    /**
     * 是否为工作日
     * <p>枚举值：</p>
     * <ul>
     *   <li>0 — 非工作日（休息日）</li>
     *   <li>1 — 工作日（默认值）</li>
     * </ul>
     * 本配置仅定义常规周规律，法定节假日调休需配合节假日表处理
     */
    @TableField("is_workday")
    private Integer isWorkday;
}
