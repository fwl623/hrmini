package com.company.hrms.attendance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 打卡流水
 * DDL: attendance_record (#43)
 */
@Data
@TableName("attendance_record")
public class AttendanceRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 员工ID
     * <p>关联 employee 表主键，用于标识该考勤记录所属的员工</p>
     */
    @TableField("employee_id")
    private Long employeeId;

    /**
     * 考勤日（打卡日期）
     * <p>格式：yyyy-MM-dd，表示该打卡记录归属的考勤日期，与打卡时间（punch_time）可能跨天</p>
     */
    @TableField("punch_date")
    private LocalDate punchDate;

    /**
     * 打卡时间
     * <p>格式：yyyy-MM-dd HH:mm:ss，精确到秒，记录员工实际打卡的日期时间</p>
     */
    @TableField("punch_time")
    private LocalDateTime punchTime;

    /**
     * 打卡类型
     * <p>标识本次打卡是上班签到还是下班签退</p>
     * <ul>
     *   <li><b>IN</b> - 上班签到（上班卡）</li>
     *   <li><b>OUT</b> - 下班签退（下班卡）</li>
     * </ul>
     */
    @TableField("punch_type")
    private String punchType;

    /**
     * 打卡状态（考勤结果）
     * <p>标识本次打卡的考勤判定结果，由系统根据排班规则自动计算</p>
     * <ul>
     *   <li><b>NORMAL</b> - 正常打卡</li>
     *   <li><b>LATE</b> - 迟到</li>
     *   <li><b>EARLY_LEAVE</b> - 早退</li>
     *   <li><b>ABSENT_HALF</b> - 半日缺勤（如仅打上班卡未打下班卡，或反之）</li>
     *   <li><b>ABSENT</b> - 全天缺勤</li>
     *   <li><b>OVERTIME</b> - 加班</li>
     * </ul>
     */
    @TableField("punch_status")
    private String punchStatus;

    /**
     * 打卡来源（打卡渠道）
     * <p>标识本次打卡操作的发起方式</p>
     * <ul>
     *   <li><b>WEB</b> - PC 网页端打卡</li>
     *   <li><b>APP</b> - 移动 App 端打卡</li>
     *   <li><b>MAKEUP</b> - 补卡（由管理员或审批流程代为补登）</li>
     *   <li><b>CLOCK</b> - 实体打卡机</li>
     *   <li><b>WECHAT</b> - 企业微信集成</li>
     * </ul>
     */
    @TableField("source")
    private String source;

    /**
     * 客户端 IP 地址
     * <p>记录打卡时员工所使用的网络 IP 地址，用于地理定位和异常检测</p>
     * <p>格式：IPv4 点分十进制（如 "192.168.1.100"）或 IPv6 字符串</p>
     */
    @TableField("client_ip")
    private String clientIp;

    /**
     * GPS 地理位置坐标（JSON 格式）
     * <p>记录打卡时设备 GPS 定位信息，用于考勤范围校验</p>
     * <p>预期 JSON 结构：</p>
     * <pre>{@code
     * {
     *   "lat": 39.9042,       // 纬度 (WGS-84)
     *   "lng": 116.4074,      // 经度 (WGS-84)
     *   "alt": 50.0,          // 海拔（米，可选）
     *   "accuracy": 20.0,     // 定位精度（米）
     *   "provider": "gps"     // 定位来源：gps / network / wifi
     * }
     * }</pre>
     */
    @TableField("gps_json")
    private String gpsJson;

    /**
     * 记录创建时间
     * <p>格式：yyyy-MM-dd HH:mm:ss，该记录在数据库中首次插入的时间戳，由数据库自动填充</p>
     */
    @TableField("created_at")
    private LocalDateTime createdAt;
}
