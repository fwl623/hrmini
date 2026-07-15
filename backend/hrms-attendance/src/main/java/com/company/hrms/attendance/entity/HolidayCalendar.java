package com.company.hrms.attendance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

/**
 * 法定节假日
 * DDL: holiday_calendar (#5)
 */
@Data
@TableName("holiday_calendar")
public class HolidayCalendar {

    /**
     * 主键 ID，自增
     * 唯一标识一条节假日记录
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 节假日日期
     * 格式：yyyy-MM-dd（例如 2026-10-01）
     * 表示该节假日具体落在哪一天
     */
    @TableField("holiday_date")
    private LocalDate holidayDate;

    /**
     * 节假日名称
     * 例如：元旦、春节、清明节、劳动节、端午节、中秋节、国庆节
     * 用于在前端展示节假日的文字标识
     */
    @TableField("name")
    private String name;
}
