package com.company.hrms.payroll.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 批次核算明细表
 * DDL: payroll_detail (#54)
 */
@Data
@TableName("payroll_detail")
public class PayrollDetail {

    /**
     * 主键ID
     * 自增主键，唯一标识每条核算明细记录
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 批次ID
     * 关联 payroll_batch 表主键，标识该明细所属的核算批次
     */
    @TableField("batch_id")
    private Long batchId;

    /**
     * 员工ID
     * 关联 employee 表主键，标识该明细对应的员工
     */
    @TableField("employee_id")
    private Long employeeId;

    /**
     * 核算状态
     * <p>取值范围：</p>
     * <ul>
     *   <li><b>SUCCESS</b> - 核算成功</li>
     *   <li><b>FAILED</b> - 核算失败</li>
     * </ul>
     */
    @TableField("calc_status")
    private String calcStatus;

    /**
     * 应发工资（税前）
     * 单位为元，保留两位小数
     * 包含基本工资、津贴、奖金等所有应发项目合计
     */
    @TableField("gross_salary")
    private BigDecimal grossSalary;

    /**
     * 实发工资（税后）
     * 单位为元，保留两位小数
     * 应发工资扣除个税、社保、公积金等扣款项后的实际发放金额
     */
    @TableField("net_salary")
    private BigDecimal netSalary;

    /**
     * 各薪资项明细（JSON）
     * <p>预期 JSON 结构示例：</p>
     * <pre>
     * [
     *   {
     *     "itemName": "基本工资",
     *     "amount": 5000.00,
     *     "type": "EARNING"   // EARNING-应发项, DEDUCTION-扣款项
     *   },
     *   {
     *     "itemName": "个税",
     *     "amount": -150.00,
     *     "type": "DEDUCTION"
     *   }
     * ]
     * </pre>
     */
    @TableField("detail_json")
    private String detailJson;

    /**
     * 异常标记数组（JSON）
     * <p>记录核算过程中产生的异常或警告标记，预期格式：</p>
     * <pre>
     * ["SOCIAL_SECURITY_MISMATCH", "ATTENDANCE_INCOMPLETE", "TAX_THRESHOLD_CROSSED"]
     * </pre>
     * <p>常见标记枚举：</p>
     * <ul>
     *   <li><b>SOCIAL_SECURITY_MISMATCH</b> - 社保基数异常</li>
     *   <li><b>ATTENDANCE_INCOMPLETE</b> - 考勤数据不完整</li>
     *   <li><b>TAX_THRESHOLD_CROSSED</b> - 跨税率档位</li>
     *   <li><b>MANUAL_ADJUST_EXISTS</b> - 存在手工调整</li>
     *   <li><b>PREV_NET_MISMATCH</b> - 与上月实发差异过大</li>
     * </ul>
     */
    @TableField("anomaly_flags")
    private String anomalyFlags;

    /**
     * 上月实发工资
     * 单位为元，保留两位小数
     * 用于环比对比及异常检测，协助判断本月数据是否存在明显偏差
     */
    @TableField("prev_net_salary")
    private BigDecimal prevNetSalary;

    /**
     * 是否手工调整
     * <p>取值范围：</p>
     * <ul>
     *   <li><b>0</b> - 否（系统自动核算，未手工修改）</li>
     *   <li><b>1</b> - 是（已人工干预调整）</li>
     * </ul>
     */
    @TableField("manual_adjusted")
    private Integer manualAdjusted;
}
