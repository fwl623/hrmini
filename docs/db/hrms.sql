/*
 Navicat Premium Data Transfer

 Source Server         : 82
 Source Server Type    : MySQL
 Source Server Version : 80027 (8.0.27)
 Source Host           : 39.101.67.167:3306
 Source Schema         : hrms

 Target Server Type    : MySQL
 Target Server Version : 80027 (8.0.27)
 File Encoding         : 65001

 Date: 15/07/2026 10:46:02
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for approval_delegation
-- ----------------------------
DROP TABLE IF EXISTS `approval_delegation`;
CREATE TABLE `approval_delegation`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `delegator_id` bigint NOT NULL,
  `delegate_user_id` bigint NOT NULL,
  `start_date` date NOT NULL,
  `end_date` date NOT NULL,
  `reason` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/CANCELLED',
  `cancelled_at` datetime NULL DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_delegator`(`delegator_id` ASC, `status` ASC) USING BTREE,
  INDEX `idx_delegate`(`delegate_user_id` ASC, `status` ASC, `start_date` ASC, `end_date` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '委托审批表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of approval_delegation
-- ----------------------------

-- ----------------------------
-- Table structure for approval_instance
-- ----------------------------
DROP TABLE IF EXISTS `approval_instance`;
CREATE TABLE `approval_instance`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `process_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `business_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '业务表主键',
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `initiator_id` bigint NOT NULL,
  `current_node` int NOT NULL DEFAULT 1,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_type_status`(`process_type` ASC, `status` ASC) USING BTREE,
  INDEX `idx_initiator`(`initiator_id` ASC) USING BTREE,
  INDEX `idx_business`(`process_type` ASC, `business_key` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '审批实例' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of approval_instance
-- ----------------------------

-- ----------------------------
-- Table structure for approval_log
-- ----------------------------
DROP TABLE IF EXISTS `approval_log`;
CREATE TABLE `approval_log`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `instance_id` bigint NOT NULL,
  `task_id` bigint NULL DEFAULT NULL,
  `operator_id` bigint NOT NULL COMMENT '实际操作人',
  `on_behalf_of_id` bigint NULL DEFAULT NULL COMMENT '被代审人',
  `display_text` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '孙强 代 李明 审批',
  `action` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'SUBMIT/APPROVE/REJECT/WITHDRAW/FORWARD',
  `comment` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `from_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `to_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_instance`(`instance_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '审批流转日志' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of approval_log
-- ----------------------------

-- ----------------------------
-- Table structure for approval_process_def
-- ----------------------------
DROP TABLE IF EXISTS `approval_process_def`;
CREATE TABLE `approval_process_def`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `process_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'ONBOARDING/REGULARIZATION/TRANSFER/RESIGNATION/...',
  `name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `nodes_json` json NOT NULL COMMENT '审批节点配置',
  `sla_hours` int NOT NULL DEFAULT 48,
  `status` tinyint NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_type`(`process_type` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '流程定义' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of approval_process_def
-- ----------------------------

-- ----------------------------
-- Table structure for approval_task
-- ----------------------------
DROP TABLE IF EXISTS `approval_task`;
CREATE TABLE `approval_task`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `instance_id` bigint NOT NULL,
  `node_order` int NOT NULL,
  `assignee_id` bigint NOT NULL COMMENT '原审批人',
  `actual_assignee_id` bigint NULL DEFAULT NULL COMMENT '委托解析后的实际审批人',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'PENDING/APPROVED/REJECTED/FORWARDED',
  `comment` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `sla_deadline` datetime NULL DEFAULT NULL,
  `overdue` tinyint NOT NULL DEFAULT 0,
  `completed_at` datetime NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_assignee_status`(`assignee_id` ASC, `status` ASC) USING BTREE,
  INDEX `idx_actual_assignee`(`actual_assignee_id` ASC, `status` ASC) USING BTREE,
  INDEX `idx_instance`(`instance_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '审批任务' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of approval_task
-- ----------------------------

-- ----------------------------
-- Table structure for attendance_daily_summary
-- ----------------------------
DROP TABLE IF EXISTS `attendance_daily_summary`;
CREATE TABLE `attendance_daily_summary`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `employee_id` bigint NOT NULL,
  `summary_date` date NOT NULL,
  `day_status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'NORMAL/LATE/EARLY_LEAVE/ABSENT_HALF/ABSENT/MISSING_IN/MISSING_OUT/LEAVE',
  `clock_in_time` datetime NULL DEFAULT NULL,
  `clock_out_time` datetime NULL DEFAULT NULL,
  `leave_days` decimal(3, 1) NOT NULL DEFAULT 0.0 COMMENT '当日请假天数',
  `overtime_hours` decimal(5, 2) NOT NULL DEFAULT 0.00,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_emp_date`(`employee_id` ASC, `summary_date` ASC) USING BTREE,
  INDEX `idx_date_status`(`summary_date` ASC, `day_status` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '日考勤汇总' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of attendance_daily_summary
-- ----------------------------

-- ----------------------------
-- Table structure for attendance_group
-- ----------------------------
DROP TABLE IF EXISTS `attendance_group`;
CREATE TABLE `attendance_group`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '考勤组名称',
  `shift_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'FIXED/FLEXIBLE/SCHEDULE',
  `work_start_time` time NOT NULL COMMENT '上班时间',
  `work_end_time` time NOT NULL COMMENT '下班时间',
  `lunch_start_time` time NULL DEFAULT '12:00:00' COMMENT '午休开始',
  `lunch_end_time` time NULL DEFAULT '13:00:00' COMMENT '午休结束',
  `flex_start_earliest` time NULL DEFAULT NULL COMMENT '弹性最早打卡',
  `flex_start_latest` time NULL DEFAULT NULL COMMENT '弹性最晚打卡',
  `late_threshold_minutes` int NOT NULL DEFAULT 15 COMMENT '迟到阈值',
  `early_leave_threshold_minutes` int NOT NULL DEFAULT 15 COMMENT '早退阈值',
  `ip_whitelist_json` json NULL COMMENT 'IP白名单',
  `gps_range_json` json NULL COMMENT 'GPS范围 {lat,lng,radiusM}',
  `deleted` tinyint NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '考勤组' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of attendance_group
-- ----------------------------

-- ----------------------------
-- Table structure for attendance_group_member
-- ----------------------------
DROP TABLE IF EXISTS `attendance_group_member`;
CREATE TABLE `attendance_group_member`  (
  `group_id` bigint NOT NULL COMMENT '考勤组ID',
  `employee_id` bigint NOT NULL COMMENT '员工ID',
  PRIMARY KEY (`employee_id`) USING BTREE,
  INDEX `idx_group`(`group_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '员工-考勤组映射（物化）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of attendance_group_member
-- ----------------------------

-- ----------------------------
-- Table structure for attendance_group_scope
-- ----------------------------
DROP TABLE IF EXISTS `attendance_group_scope`;
CREATE TABLE `attendance_group_scope`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `group_id` bigint NOT NULL COMMENT '考勤组ID',
  `scope_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'DEPARTMENT/POSITION/EMPLOYEE',
  `scope_id` bigint NOT NULL COMMENT '对应ID',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_group`(`group_id` ASC) USING BTREE,
  INDEX `idx_scope`(`scope_type` ASC, `scope_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '考勤组适用人员范围' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of attendance_group_scope
-- ----------------------------

-- ----------------------------
-- Table structure for attendance_month_lock
-- ----------------------------
DROP TABLE IF EXISTS `attendance_month_lock`;
CREATE TABLE `attendance_month_lock`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `year_month` char(7) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '账期 YYYY-MM',
  `status` tinyint NOT NULL COMMENT '10=OPEN 20=LOCKED',
  `locked_at` datetime NULL DEFAULT NULL COMMENT '锁定时间',
  `locked_by` bigint NULL DEFAULT NULL COMMENT '锁定人',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_month`(`year_month` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '月考勤锁定' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of attendance_month_lock
-- ----------------------------

-- ----------------------------
-- Table structure for attendance_monthly_summary
-- ----------------------------
DROP TABLE IF EXISTS `attendance_monthly_summary`;
CREATE TABLE `attendance_monthly_summary`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `employee_id` bigint NOT NULL,
  `period` char(7) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '2026-07',
  `should_attend_days` int NOT NULL COMMENT '应出勤',
  `actual_attend_days` decimal(5, 1) NOT NULL COMMENT '实际出勤',
  `late_count` int NOT NULL DEFAULT 0,
  `early_leave_count` int NOT NULL DEFAULT 0,
  `absent_days` decimal(5, 1) NOT NULL DEFAULT 0.0,
  `leave_days` decimal(5, 1) NOT NULL DEFAULT 0.0,
  `overtime_hours` decimal(6, 2) NOT NULL DEFAULT 0.00,
  `annual_balance` decimal(5, 1) NULL DEFAULT NULL COMMENT '年假余额',
  `detail_json` json NULL COMMENT '明细快照',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_emp_period`(`employee_id` ASC, `period` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '月考勤汇总表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of attendance_monthly_summary
-- ----------------------------

-- ----------------------------
-- Table structure for attendance_record
-- ----------------------------
DROP TABLE IF EXISTS `attendance_record`;
CREATE TABLE `attendance_record`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `employee_id` bigint NOT NULL,
  `punch_date` date NOT NULL COMMENT '考勤日',
  `punch_time` datetime NOT NULL COMMENT '打卡时间',
  `punch_type` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'IN/OUT',
  `punch_status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'NORMAL/LATE/EARLY_LEAVE/ABSENT_HALF',
  `source` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'WEB/APP/MAKEUP',
  `client_ip` varchar(45) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `gps_json` json NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_emp_date`(`employee_id` ASC, `punch_date` ASC) USING BTREE,
  INDEX `idx_emp_time`(`employee_id` ASC, `punch_time` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '打卡流水' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of attendance_record
-- ----------------------------

-- ----------------------------
-- Table structure for attendance_supplement
-- ----------------------------
DROP TABLE IF EXISTS `attendance_supplement`;
CREATE TABLE `attendance_supplement`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `employee_id` bigint NOT NULL,
  `makeup_date` date NOT NULL COMMENT '补卡日期',
  `punch_type` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'IN/OUT',
  `makeup_time` datetime NOT NULL COMMENT '补卡时间',
  `reason` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'PENDING/APPROVED/REJECTED',
  `instance_id` bigint NULL DEFAULT NULL COMMENT '审批实例ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_emp_month`(`employee_id` ASC, `created_at` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '补卡申请表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of attendance_supplement
-- ----------------------------

-- ----------------------------
-- Table structure for department
-- ----------------------------
DROP TABLE IF EXISTS `department`;
CREATE TABLE `department`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '部门名称',
  `code` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '部门编码（工号生成用）',
  `parent_id` bigint NULL DEFAULT NULL COMMENT '上级部门ID，NULL=根',
  `path` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '路径枚举，如 /1/3/7/，深度≤5',
  `level` tinyint NOT NULL DEFAULT 1 COMMENT '层级 1~5',
  `head_employee_id` bigint NULL DEFAULT NULL COMMENT '部门负责人 employee_id',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序序号',
  `description` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_code`(`code` ASC) USING BTREE,
  INDEX `idx_parent`(`parent_id` ASC) USING BTREE,
  INDEX `idx_path`(`path`(64) ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '部门表，PRD §3.1' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of department
-- ----------------------------

-- ----------------------------
-- Table structure for employee
-- ----------------------------
DROP TABLE IF EXISTS `employee`;
CREATE TABLE `employee`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID（作 employee_id 业务主键使用，永不复用）',
  `employee_no` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工号，格式：年份(4位)+部门编码(2位)+序号(3位)',
  `user_id` bigint NULL DEFAULT NULL COMMENT '系统账号ID，关联 sys_user.id',
  `name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '姓名',
  `gender` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '性别：MALE/FEMALE',
  `mobile` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '手机号（登录账号）',
  `email` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '邮箱',
  `department_id` bigint NOT NULL COMMENT '所属部门ID，关联 department.id',
  `position_id` bigint NOT NULL COMMENT '职位ID，关联 position.id',
  `grade` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '职级，如P5、M2',
  `manager_id` bigint NULL DEFAULT NULL COMMENT '直属上级ID，关联 employee.id',
  `work_location` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工作地点',
  `hire_date` date NOT NULL COMMENT '入职日期',
  `employment_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用工类型：fulltime/parttime/intern',
  `employment_status` tinyint NOT NULL COMMENT '在职状态：10=试用期 20=正式 30=待离职 40=已离职',
  `last_work_day` date NULL DEFAULT NULL COMMENT '最后工作日',
  `probation_pay_ratio` decimal(3, 2) NULL DEFAULT NULL COMMENT '试用薪资比例 0.80~1.00',
  `deleted` tinyint NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_employee_no`(`employee_no` ASC) USING BTREE,
  UNIQUE INDEX `uk_mobile`(`mobile` ASC) USING BTREE,
  INDEX `idx_dept_status`(`department_id` ASC, `employment_status` ASC) USING BTREE,
  INDEX `idx_hire_date`(`hire_date` ASC) USING BTREE,
  INDEX `idx_name`(`name` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '员工主表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of employee
-- ----------------------------

-- ----------------------------
-- Table structure for employee_bank
-- ----------------------------
DROP TABLE IF EXISTS `employee_bank`;
CREATE TABLE `employee_bank`  (
  `employee_id` bigint NOT NULL COMMENT '员工ID，关联 employee.id',
  `bank_account_enc` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '银行卡号密文（AES-256-GCM）',
  `bank_account_tail` varchar(4) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '银行卡号后四位明文',
  `bank_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '开户行',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`employee_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '员工银行卡信息表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of employee_bank
-- ----------------------------

-- ----------------------------
-- Table structure for employee_contract
-- ----------------------------
DROP TABLE IF EXISTS `employee_contract`;
CREATE TABLE `employee_contract`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `employee_id` bigint NOT NULL COMMENT '员工ID，关联 employee.id',
  `contract_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '合同类型：FIXED=固定期限 UNFIXED=无固定期限 LABOR=劳务合同',
  `contract_expire_date` date NULL DEFAULT NULL COMMENT '合同到期日，固定期限合同必填',
  `probation_salary_ratio` decimal(5, 4) NOT NULL COMMENT '试用期待遇比例，范围0.80~1.00',
  `scheme_id` bigint NOT NULL COMMENT '薪资账套ID，关联 payroll_scheme.id',
  `base_salary` decimal(12, 2) NOT NULL COMMENT '基本工资',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_employee`(`employee_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '员工合同表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of employee_contract
-- ----------------------------

-- ----------------------------
-- Table structure for employee_id_sequence_deprecated
-- ----------------------------
DROP TABLE IF EXISTS `employee_id_sequence_deprecated`;
CREATE TABLE `employee_id_sequence_deprecated`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `year` char(4) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '年份',
  `dept_code` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '部门编码',
  `current_val` int NOT NULL DEFAULT 0 COMMENT '当前序号',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_year_dept`(`year` ASC, `dept_code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '工号序列表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of employee_id_sequence_deprecated
-- ----------------------------

-- ----------------------------
-- Table structure for employee_mobile_change_application
-- ----------------------------
DROP TABLE IF EXISTS `employee_mobile_change_application`;
CREATE TABLE `employee_mobile_change_application`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `instance_id` bigint NULL DEFAULT NULL COMMENT '审批实例ID，关联 approval_instance.id',
  `employee_id` bigint NOT NULL COMMENT '员工ID，关联 employee.id',
  `user_id` bigint NOT NULL COMMENT '系统用户ID，关联 sys_user.id',
  `old_mobile` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '旧手机号',
  `new_mobile` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '新手机号',
  `sms_verified` tinyint NOT NULL DEFAULT 0 COMMENT '提交前已验证新号：0=否 1=是',
  `reason` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '变更原因',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'PENDING/APPROVED/REJECTED/CANCELLED',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_employee`(`employee_id` ASC) USING BTREE,
  INDEX `idx_status`(`status` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '手机号变更申请表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of employee_mobile_change_application
-- ----------------------------

-- ----------------------------
-- Table structure for employee_no_history
-- ----------------------------
DROP TABLE IF EXISTS `employee_no_history`;
CREATE TABLE `employee_no_history`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `employee_no` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工号',
  `year` char(4) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '年份',
  `dept_code` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '部门编码',
  `employee_id` bigint NULL DEFAULT NULL COMMENT '占用该工号的员工id，离职后置空',
  `reuse_flag` tinyint NOT NULL DEFAULT 0 COMMENT '0=占用中 1=可复用',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_reuse`(`reuse_flag` ASC, `year` ASC, `dept_code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '工号复用历史表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of employee_no_history
-- ----------------------------

-- ----------------------------
-- Table structure for employee_personal
-- ----------------------------
DROP TABLE IF EXISTS `employee_personal`;
CREATE TABLE `employee_personal`  (
  `employee_id` bigint NOT NULL COMMENT '员工ID，关联 employee.id',
  `id_number_enc` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '身份证号密文（AES-256-GCM）',
  `id_number_hash` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '身份证号SHA-256哈希（精确检索用）',
  `birthday` date NULL DEFAULT NULL COMMENT '生日',
  `household_address` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '户籍地址',
  `residence_address` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '现居住地址',
  `emergency_contact` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '紧急联系人',
  `emergency_phone` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '紧急联系电话',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`employee_id`) USING BTREE,
  INDEX `idx_id_number_hash`(`id_number_hash` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '员工个人信息表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of employee_personal
-- ----------------------------

-- ----------------------------
-- Table structure for employee_resignation_request
-- ----------------------------
DROP TABLE IF EXISTS `employee_resignation_request`;
CREATE TABLE `employee_resignation_request`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `instance_id` bigint NULL DEFAULT NULL,
  `employee_id` bigint NOT NULL,
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'PENDING/APPROVED/REJECTED/CANCELLED',
  `expected_resign_date` date NOT NULL,
  `reason_category` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'VOLUNTARY/INVOLUNTARY/NEGOTIATED',
  `resignation_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'RESIGN/DISMISS/CONTRACT_EXPIRE/OTHER',
  `reason_detail` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_employee`(`employee_id` ASC) USING BTREE,
  INDEX `idx_status`(`status` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '员工离职申请表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of employee_resignation_request
-- ----------------------------

-- ----------------------------
-- Table structure for employee_salary_history
-- ----------------------------
DROP TABLE IF EXISTS `employee_salary_history`;
CREATE TABLE `employee_salary_history`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `employee_id` bigint NOT NULL COMMENT '员工ID，关联 employee.id',
  `field_name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '变更字段名',
  `old_value` decimal(12, 2) NOT NULL COMMENT '变更前值',
  `new_value` decimal(12, 2) NOT NULL COMMENT '变更后值',
  `effective_date` date NOT NULL COMMENT '生效日期',
  `reason` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '变更原因',
  `operator_id` bigint NOT NULL COMMENT '操作人 employee_id',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_employee`(`employee_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '调薪历史表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of employee_salary_history
-- ----------------------------

-- ----------------------------
-- Table structure for employee_salary_profile
-- ----------------------------
DROP TABLE IF EXISTS `employee_salary_profile`;
CREATE TABLE `employee_salary_profile`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `employee_id` bigint NOT NULL COMMENT '员工ID，关联 employee.id',
  `scheme_id` bigint NOT NULL COMMENT '薪资账套ID，关联 payroll_scheme.id',
  `base_salary` decimal(12, 2) NOT NULL COMMENT '基本工资',
  `allowance_base_json` json NULL COMMENT '各项津贴基数JSON',
  `ss_base` decimal(12, 2) NOT NULL COMMENT '社保基数',
  `hf_base` decimal(12, 2) NOT NULL COMMENT '公积金基数',
  `performance_base` decimal(12, 2) NULL DEFAULT NULL COMMENT '绩效基数',
  `probation_ratio` decimal(5, 4) NOT NULL DEFAULT 1.0000 COMMENT '试用期待遇比例',
  `effective_date` date NOT NULL COMMENT '生效日期',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_employee`(`employee_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '员工薪资档案表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of employee_salary_profile
-- ----------------------------

-- ----------------------------
-- Table structure for employee_transfer_history
-- ----------------------------
DROP TABLE IF EXISTS `employee_transfer_history`;
CREATE TABLE `employee_transfer_history`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `employee_id` bigint NOT NULL COMMENT '员工ID，关联 employee.id',
  `transfer_app_id` bigint NOT NULL COMMENT '调岗申请ID，关联 transfer_application.id',
  `from_department_id` bigint NOT NULL COMMENT '原部门ID，关联 department.id',
  `to_department_id` bigint NOT NULL COMMENT '新部门ID，关联 department.id',
  `from_position_id` bigint NULL DEFAULT NULL COMMENT '原职位ID，关联 position.id',
  `to_position_id` bigint NULL DEFAULT NULL COMMENT '新职位ID，关联 position.id',
  `transfer_date` date NOT NULL COMMENT '调岗日期',
  `reason` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '调岗原因',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_employee`(`employee_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '调岗历史表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of employee_transfer_history
-- ----------------------------

-- ----------------------------
-- Table structure for flyway_schema_history
-- ----------------------------
DROP TABLE IF EXISTS `flyway_schema_history`;
CREATE TABLE `flyway_schema_history`  (
  `installed_rank` int NOT NULL,
  `version` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `description` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `script` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `checksum` int NULL DEFAULT NULL,
  `installed_by` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `installed_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `execution_time` int NOT NULL,
  `success` tinyint(1) NOT NULL,
  PRIMARY KEY (`installed_rank`) USING BTREE,
  INDEX `flyway_schema_history_s_idx`(`success` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of flyway_schema_history
-- ----------------------------
INSERT INTO `flyway_schema_history` VALUES (1, '1', 'init hrms schema', 'SQL', 'V1__init_hrms_schema.sql', 655218781, 'root', '2026-07-14 17:39:13', 6807, 1);

-- ----------------------------
-- Table structure for holiday_calendar
-- ----------------------------
DROP TABLE IF EXISTS `holiday_calendar`;
CREATE TABLE `holiday_calendar`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `holiday_date` date NOT NULL,
  `name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '节假日名称',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_date`(`holiday_date` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '法定节假日' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of holiday_calendar
-- ----------------------------

-- ----------------------------
-- Table structure for import_batch
-- ----------------------------
DROP TABLE IF EXISTS `import_batch`;
CREATE TABLE `import_batch`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `import_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'DEPT/EMPLOYEE/SALARY/ATTENDANCE_SUMMARY',
  `file_name` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `total_rows` int NOT NULL DEFAULT 0,
  `success_rows` int NOT NULL DEFAULT 0,
  `fail_rows` int NOT NULL DEFAULT 0,
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'VALIDATING/VALIDATED/COMMITTED/FAILED',
  `created_by` bigint NOT NULL COMMENT '操作人 sys_user.id',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_type_status`(`import_type` ASC, `status` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '导入批次' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of import_batch
-- ----------------------------

-- ----------------------------
-- Table structure for import_row_error
-- ----------------------------
DROP TABLE IF EXISTS `import_row_error`;
CREATE TABLE `import_row_error`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `batch_id` bigint NOT NULL COMMENT '所属批次 import_batch.id',
  `row_index` int NOT NULL COMMENT 'Excel 行号',
  `column_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '出错的列',
  `error_message` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `raw_data_json` json NULL COMMENT '该行原始数据',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_batch`(`batch_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '导入错误行' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of import_row_error
-- ----------------------------

-- ----------------------------
-- Table structure for leave_application
-- ----------------------------
DROP TABLE IF EXISTS `leave_application`;
CREATE TABLE `leave_application`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `employee_id` bigint NOT NULL,
  `leave_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'ANNUAL/SICK/PERSONAL/MARRIAGE/MATERNITY/BEREAVEMENT/COMP_OFF',
  `start_time` datetime NOT NULL COMMENT '含上午/下午',
  `end_time` datetime NOT NULL COMMENT '含上午/下午',
  `leave_days` decimal(4, 1) NOT NULL COMMENT '支持0.5天',
  `reason` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `handover_employee_id` bigint NULL DEFAULT NULL COMMENT '交接人',
  `attachment_url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '附件URL',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'PENDING/APPROVED/REJECTED/CANCELLED',
  `instance_id` bigint NULL DEFAULT NULL COMMENT '审批实例ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_emp_status`(`employee_id` ASC, `status` ASC) USING BTREE,
  INDEX `idx_date_range`(`start_time` ASC, `end_time` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '请假申请表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of leave_application
-- ----------------------------

-- ----------------------------
-- Table structure for leave_balance
-- ----------------------------
DROP TABLE IF EXISTS `leave_balance`;
CREATE TABLE `leave_balance`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `employee_id` bigint NOT NULL,
  `leave_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'ANNUAL/COMP_OFF',
  `balance` decimal(6, 1) NOT NULL DEFAULT 0.0,
  `year` int NULL DEFAULT NULL COMMENT '年假年度',
  `expire_date` date NULL DEFAULT NULL COMMENT '调休过期日',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_emp_type_year`(`employee_id` ASC, `leave_type` ASC, `year` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '假期余额表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of leave_balance
-- ----------------------------

-- ----------------------------
-- Table structure for login_log
-- ----------------------------
DROP TABLE IF EXISTS `login_log`;
CREATE TABLE `login_log`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '关联 sys_user.id',
  `login_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `login_ip` varchar(45) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_agent` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `device` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '解析自 UA',
  `location` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'IP 归属地，可选',
  `success` tinyint NOT NULL COMMENT '1=成功 0=失败',
  `fail_reason` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user_time`(`user_id` ASC, `login_time` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '登录日志，PRD §9.5' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of login_log
-- ----------------------------

-- ----------------------------
-- Table structure for onboarding_application
-- ----------------------------
DROP TABLE IF EXISTS `onboarding_application`;
CREATE TABLE `onboarding_application`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `instance_id` bigint NULL DEFAULT NULL,
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'DRAFT/APPROVING/APPROVED/REJECTED/ONBOARDED/ABANDONED',
  `name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `gender` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'MALE/FEMALE',
  `mobile` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `email` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `id_number_enc` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '身份证密文',
  `id_number_hash` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'SHA256检索',
  `expected_onboard_date` date NOT NULL,
  `department_id` bigint NOT NULL,
  `position_id` bigint NOT NULL,
  `employment_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'fulltime/parttime/intern',
  `probation_months` int NOT NULL,
  `probation_salary_ratio` decimal(5, 4) NOT NULL,
  `base_salary` decimal(12, 2) NULL DEFAULT NULL COMMENT '约定薪资',
  `actual_onboard_date` date NULL DEFAULT NULL COMMENT '实际入职日',
  `manager_id` bigint NULL DEFAULT NULL,
  `employee_id` bigint NULL DEFAULT NULL COMMENT '审批通过后关联',
  `created_by` bigint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_status`(`status` ASC) USING BTREE,
  INDEX `idx_dept`(`department_id` ASC) USING BTREE,
  INDEX `idx_mobile`(`mobile` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '入职申请表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of onboarding_application
-- ----------------------------

-- ----------------------------
-- Table structure for operation_log
-- ----------------------------
DROP TABLE IF EXISTS `operation_log`;
CREATE TABLE `operation_log`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '关联 sys_user.id',
  `module` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `action` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `target_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `request_ip` varchar(45) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `detail` json NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user_time`(`user_id` ASC, `created_at` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '操作审计日志' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of operation_log
-- ----------------------------

-- ----------------------------
-- Table structure for overtime_application
-- ----------------------------
DROP TABLE IF EXISTS `overtime_application`;
CREATE TABLE `overtime_application`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `employee_id` bigint NOT NULL,
  `overtime_date` date NOT NULL COMMENT '加班日期',
  `start_time` datetime NOT NULL COMMENT '加班开始时间',
  `end_time` datetime NOT NULL COMMENT '加班结束时间',
  `hours` decimal(5, 2) NOT NULL COMMENT '系统计算时长',
  `reason` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '加班原因',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'PENDING/APPROVED/REJECTED',
  `comp_off_hours` decimal(5, 2) NULL DEFAULT NULL COMMENT '折算调休小时',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_emp_date`(`employee_id` ASC, `overtime_date` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '加班申请表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of overtime_application
-- ----------------------------

-- ----------------------------
-- Table structure for overtime_ledger
-- ----------------------------
DROP TABLE IF EXISTS `overtime_ledger`;
CREATE TABLE `overtime_ledger`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `employee_id` bigint NOT NULL,
  `application_id` bigint NULL DEFAULT NULL COMMENT '关联 overtime_application.id',
  `period` char(7) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '归属账期 2026-07',
  `total_hours` decimal(5, 2) NOT NULL COMMENT '审批总加班时长',
  `rate_type` tinyint NOT NULL COMMENT '倍率: 15=1.5倍/20=2.0倍/30=3.0倍',
  `ledger_date` date NOT NULL COMMENT '加班日期',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_emp_period`(`employee_id` ASC, `period` ASC) USING BTREE,
  INDEX `idx_application`(`application_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '加班批准台账' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of overtime_ledger
-- ----------------------------

-- ----------------------------
-- Table structure for pay_tax_bracket
-- ----------------------------
DROP TABLE IF EXISTS `pay_tax_bracket`;
CREATE TABLE `pay_tax_bracket`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tax_year` int NOT NULL COMMENT '纳税年度',
  `min_taxable` decimal(12, 2) NOT NULL COMMENT '起征金额',
  `max_taxable` decimal(12, 2) NULL DEFAULT NULL COMMENT '上限，NULL=无限',
  `rate` decimal(5, 4) NOT NULL COMMENT '税率，如 0.03',
  `quick_deduction` decimal(12, 2) NOT NULL COMMENT '速算扣除数',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_year_range`(`tax_year` ASC, `min_taxable` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 8 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '个税税率表（累计预扣法）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of pay_tax_bracket
-- ----------------------------
INSERT INTO `pay_tax_bracket` VALUES (1, 2024, 0.00, 36000.00, 0.0300, 0.00);
INSERT INTO `pay_tax_bracket` VALUES (2, 2024, 36000.00, 144000.00, 0.1000, 2520.00);
INSERT INTO `pay_tax_bracket` VALUES (3, 2024, 144000.00, 300000.00, 0.2000, 16920.00);
INSERT INTO `pay_tax_bracket` VALUES (4, 2024, 300000.00, 420000.00, 0.2500, 31920.00);
INSERT INTO `pay_tax_bracket` VALUES (5, 2024, 420000.00, 660000.00, 0.3000, 52920.00);
INSERT INTO `pay_tax_bracket` VALUES (6, 2024, 660000.00, 960000.00, 0.3500, 85920.00);
INSERT INTO `pay_tax_bracket` VALUES (7, 2024, 960000.00, NULL, 0.4500, 181920.00);

-- ----------------------------
-- Table structure for pay_tax_ytd_record
-- ----------------------------
DROP TABLE IF EXISTS `pay_tax_ytd_record`;
CREATE TABLE `pay_tax_ytd_record`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `employee_id` bigint NOT NULL,
  `period` char(7) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `taxable_income` decimal(12, 2) NOT NULL COMMENT '应纳税所得额',
  `tax_deducted` decimal(12, 2) NOT NULL COMMENT '本期预扣税额',
  `cumulative_tax` decimal(12, 2) NOT NULL COMMENT '累计预扣税额',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_emp_period`(`employee_id` ASC, `period` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '个税累计预扣记录' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of pay_tax_ytd_record
-- ----------------------------

-- ----------------------------
-- Table structure for payroll_adjustment
-- ----------------------------
DROP TABLE IF EXISTS `payroll_adjustment`;
CREATE TABLE `payroll_adjustment`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `detail_id` bigint NOT NULL,
  `item_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `adjust_amount` decimal(12, 2) NOT NULL,
  `reason` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `operator_id` bigint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_detail`(`detail_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '核算手动调整记录' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of payroll_adjustment
-- ----------------------------

-- ----------------------------
-- Table structure for payroll_batch
-- ----------------------------
DROP TABLE IF EXISTS `payroll_batch`;
CREATE TABLE `payroll_batch`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `period` char(7) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '账期 YYYY-MM',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'DRAFT/CALCULATING/PENDING_CONFIRM/APPROVING/APPROVED/DISTRIBUTED/REJECTED',
  `total_count` int NOT NULL DEFAULT 0,
  `success_count` int NOT NULL DEFAULT 0,
  `gross_total` decimal(14, 2) NULL DEFAULT NULL COMMENT '应发合计',
  `net_total` decimal(14, 2) NULL DEFAULT NULL COMMENT '实发合计',
  `anomaly_count` int NOT NULL DEFAULT 0,
  `instance_id` bigint NULL DEFAULT NULL COMMENT '审批实例',
  `attendance_locked` tinyint NOT NULL DEFAULT 0,
  `created_by` bigint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_period`(`period` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '月度核算批次表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of payroll_batch
-- ----------------------------

-- ----------------------------
-- Table structure for payroll_detail
-- ----------------------------
DROP TABLE IF EXISTS `payroll_detail`;
CREATE TABLE `payroll_detail`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `batch_id` bigint NOT NULL,
  `employee_id` bigint NOT NULL,
  `calc_status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'SUCCESS' COMMENT 'SUCCESS/FAILED',
  `gross_salary` decimal(12, 2) NULL DEFAULT NULL,
  `net_salary` decimal(12, 2) NULL DEFAULT NULL,
  `detail_json` json NOT NULL COMMENT '各薪资项明细',
  `anomaly_flags` json NULL COMMENT '异常标记数组',
  `prev_net_salary` decimal(12, 2) NULL DEFAULT NULL COMMENT '上月实发',
  `manual_adjusted` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_batch_emp`(`batch_id` ASC, `employee_id` ASC) USING BTREE,
  INDEX `idx_batch`(`batch_id` ASC) USING BTREE,
  INDEX `idx_employee`(`employee_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '批次核算明细表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of payroll_detail
-- ----------------------------

-- ----------------------------
-- Table structure for payroll_scheme
-- ----------------------------
DROP TABLE IF EXISTS `payroll_scheme`;
CREATE TABLE `payroll_scheme`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '账套名称',
  `description` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `effective_date` date NOT NULL COMMENT '生效日期',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'enabled' COMMENT 'enabled/disabled',
  `deleted` tinyint NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '薪资账套' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of payroll_scheme
-- ----------------------------

-- ----------------------------
-- Table structure for payroll_scheme_item
-- ----------------------------
DROP TABLE IF EXISTS `payroll_scheme_item`;
CREATE TABLE `payroll_scheme_item`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `scheme_id` bigint NOT NULL COMMENT '关联 payroll_scheme.id',
  `item_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '项目编码',
  `item_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '项目名称',
  `item_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'FIXED/VARIABLE/ATTENDANCE_DEDUCT/SS_DEDUCT/HF_DEDUCT/TAX',
  `calc_rule` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'SpEL 公式或规则描述',
  `base_field` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'ssBase/hfBase/performanceBase',
  `ratio` decimal(8, 4) NULL DEFAULT NULL COMMENT '社保公积金比例',
  `sort_order` int NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_scheme`(`scheme_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '账套工资项目' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of payroll_scheme_item
-- ----------------------------

-- ----------------------------
-- Table structure for payroll_scheme_scope
-- ----------------------------
DROP TABLE IF EXISTS `payroll_scheme_scope`;
CREATE TABLE `payroll_scheme_scope`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `scheme_id` bigint NOT NULL COMMENT '关联 payroll_scheme.id',
  `scope_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'DEPARTMENT/POSITION/JOB_LEVEL',
  `scope_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_scheme`(`scheme_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '账套适用范围' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of payroll_scheme_scope
-- ----------------------------

-- ----------------------------
-- Table structure for payslip_view_log
-- ----------------------------
DROP TABLE IF EXISTS `payslip_view_log`;
CREATE TABLE `payslip_view_log`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `employee_id` bigint NOT NULL,
  `batch_id` bigint NOT NULL,
  `viewed_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '查看时间',
  `verify_method` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'PASSWORD/SMS',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_emp`(`employee_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '工资条查看日志' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of payslip_view_log
-- ----------------------------

-- ----------------------------
-- Table structure for position
-- ----------------------------
DROP TABLE IF EXISTS `position`;
CREATE TABLE `position`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '职位名称',
  `sequence` varchar(4) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'M/P/S',
  `department_id` bigint NULL DEFAULT NULL COMMENT 'NULL=全公司通用',
  `rank_min` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '职级范围-最小值，如P1',
  `rank_max` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '职级范围-最大值，如P10',
  `default_probation_months` int NOT NULL DEFAULT 3 COMMENT '默认试用期（月）',
  `is_standard` tinyint NOT NULL DEFAULT 1 COMMENT '是否标准职位，0→入职二审',
  `description` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `deleted` tinyint NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_dept`(`department_id` ASC) USING BTREE,
  INDEX `idx_sequence`(`sequence` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '职位表，PRD §3.2' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of position
-- ----------------------------

-- ----------------------------
-- Table structure for regularization_application
-- ----------------------------
DROP TABLE IF EXISTS `regularization_application`;
CREATE TABLE `regularization_application`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `instance_id` bigint NULL DEFAULT NULL,
  `employee_id` bigint NOT NULL,
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `probation_start_date` date NOT NULL,
  `probation_end_date` date NOT NULL,
  `performance_evaluation` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `salary_adjustment` decimal(12, 2) NULL DEFAULT NULL,
  `approval_result` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'PASS/EXTEND/FAIL',
  `extend_months` int NULL DEFAULT NULL,
  `created_by` bigint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_employee`(`employee_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '转正申请表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of regularization_application
-- ----------------------------

-- ----------------------------
-- Table structure for resignation_application
-- ----------------------------
DROP TABLE IF EXISTS `resignation_application`;
CREATE TABLE `resignation_application`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `instance_id` bigint NULL DEFAULT NULL,
  `request_id` bigint NOT NULL COMMENT '关联 employee_resignation_request.id',
  `employee_id` bigint NOT NULL,
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'APPROVING/PENDING_RESIGN/REJECTED/RESIGNED',
  `resignation_date` date NOT NULL,
  `reason_category` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'VOLUNTARY/INVOLUNTARY/NEGOTIATED',
  `resignation_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'RESIGN/DISMISS/CONTRACT_EXPIRE/OTHER',
  `reason_detail` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `handover_employee_id` bigint NOT NULL,
  `created_by` bigint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_employee`(`employee_id` ASC) USING BTREE,
  INDEX `idx_request`(`request_id` ASC) USING BTREE,
  INDEX `idx_resign_date`(`resignation_date` ASC, `status` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'HR正式离职申请表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of resignation_application
-- ----------------------------

-- ----------------------------
-- Table structure for sys_dict
-- ----------------------------
DROP TABLE IF EXISTS `sys_dict`;
CREATE TABLE `sys_dict`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `dict_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `dict_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `dict_label` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `sort_order` int NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_type_code`(`dict_type` ASC, `dict_code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 28 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '数据字典' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_dict
-- ----------------------------
INSERT INTO `sys_dict` VALUES (1, 'employment_status', 'PROBATION', '试用期', 1, '2026-07-14 17:39:12', '2026-07-14 17:39:12');
INSERT INTO `sys_dict` VALUES (2, 'employment_status', 'REGULAR', '正式', 2, '2026-07-14 17:39:12', '2026-07-14 17:39:12');
INSERT INTO `sys_dict` VALUES (3, 'employment_status', 'PENDING_RESIGN', '待离职', 3, '2026-07-14 17:39:12', '2026-07-14 17:39:12');
INSERT INTO `sys_dict` VALUES (4, 'employment_status', 'RESIGNED', '已离职', 4, '2026-07-14 17:39:12', '2026-07-14 17:39:12');
INSERT INTO `sys_dict` VALUES (5, 'contract_type', 'FIXED', '固定期限', 1, '2026-07-14 17:39:12', '2026-07-14 17:39:12');
INSERT INTO `sys_dict` VALUES (6, 'contract_type', 'UNFIXED', '无固定期限', 2, '2026-07-14 17:39:12', '2026-07-14 17:39:12');
INSERT INTO `sys_dict` VALUES (7, 'contract_type', 'LABOR', '劳务合同', 3, '2026-07-14 17:39:12', '2026-07-14 17:39:12');
INSERT INTO `sys_dict` VALUES (8, 'position_sequence', 'M', '管理序列', 1, '2026-07-14 17:39:12', '2026-07-14 17:39:12');
INSERT INTO `sys_dict` VALUES (9, 'position_sequence', 'P', '专业序列', 2, '2026-07-14 17:39:12', '2026-07-14 17:39:12');
INSERT INTO `sys_dict` VALUES (10, 'position_sequence', 'S', '支持序列', 3, '2026-07-14 17:39:12', '2026-07-14 17:39:12');
INSERT INTO `sys_dict` VALUES (11, 'leave_type', 'ANNUAL', '年假', 1, '2026-07-14 17:39:12', '2026-07-14 17:39:12');
INSERT INTO `sys_dict` VALUES (12, 'leave_type', 'SICK', '病假', 2, '2026-07-14 17:39:12', '2026-07-14 17:39:12');
INSERT INTO `sys_dict` VALUES (13, 'leave_type', 'PERSONAL', '事假', 3, '2026-07-14 17:39:12', '2026-07-14 17:39:12');
INSERT INTO `sys_dict` VALUES (14, 'leave_type', 'MARRIAGE', '婚假', 4, '2026-07-14 17:39:12', '2026-07-14 17:39:12');
INSERT INTO `sys_dict` VALUES (15, 'leave_type', 'MATERNITY', '产假', 5, '2026-07-14 17:39:12', '2026-07-14 17:39:12');
INSERT INTO `sys_dict` VALUES (16, 'leave_type', 'BEREAVEMENT', '丧假', 6, '2026-07-14 17:39:12', '2026-07-14 17:39:12');
INSERT INTO `sys_dict` VALUES (17, 'leave_type', 'COMP_OFF', '调休', 7, '2026-07-14 17:39:12', '2026-07-14 17:39:12');
INSERT INTO `sys_dict` VALUES (18, 'process_type', 'ONBOARDING', '入职审批', 1, '2026-07-14 17:39:12', '2026-07-14 17:39:12');
INSERT INTO `sys_dict` VALUES (19, 'process_type', 'REGULARIZATION', '转正审批', 2, '2026-07-14 17:39:12', '2026-07-14 17:39:12');
INSERT INTO `sys_dict` VALUES (20, 'process_type', 'TRANSFER', '调岗审批', 3, '2026-07-14 17:39:12', '2026-07-14 17:39:12');
INSERT INTO `sys_dict` VALUES (21, 'process_type', 'RESIGNATION', '离职审批', 4, '2026-07-14 17:39:12', '2026-07-14 17:39:12');
INSERT INTO `sys_dict` VALUES (22, 'process_type', 'RESIGNATION_REQUEST', '员工离职申请', 5, '2026-07-14 17:39:12', '2026-07-14 17:39:12');
INSERT INTO `sys_dict` VALUES (23, 'process_type', 'MOBILE_CHANGE', '手机号变更', 6, '2026-07-14 17:39:12', '2026-07-14 17:39:12');
INSERT INTO `sys_dict` VALUES (24, 'process_type', 'LEAVE', '请假审批', 7, '2026-07-14 17:39:12', '2026-07-14 17:39:12');
INSERT INTO `sys_dict` VALUES (25, 'process_type', 'MAKEUP', '补卡审批', 8, '2026-07-14 17:39:12', '2026-07-14 17:39:12');
INSERT INTO `sys_dict` VALUES (26, 'process_type', 'OVERTIME', '加班审批', 9, '2026-07-14 17:39:12', '2026-07-14 17:39:12');
INSERT INTO `sys_dict` VALUES (27, 'process_type', 'PAYROLL_BATCH', '薪资批次审批', 10, '2026-07-14 17:39:12', '2026-07-14 17:39:12');

-- ----------------------------
-- Table structure for sys_permission
-- ----------------------------
DROP TABLE IF EXISTS `sys_permission`;
CREATE TABLE `sys_permission`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '权限唯一标识，如 employee:create',
  `name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '权限中文名',
  `module` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '归属模块：auth/org/employee/...',
  `type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'MENU/BUTTON/API',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_code`(`code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '权限/菜单表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_permission
-- ----------------------------

-- ----------------------------
-- Table structure for sys_role
-- ----------------------------
DROP TABLE IF EXISTS `sys_role`;
CREATE TABLE `sys_role`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `data_scope` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'ALL/DEPT_TREE/SELF/PAYROLL/NONE_PAYROLL',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_code`(`code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 6 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '角色表，PRD §2.1' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_role
-- ----------------------------
INSERT INTO `sys_role` VALUES (1, 'SYS_ADMIN', '系统管理员', 'NONE_PAYROLL', '2026-07-14 17:39:12', '2026-07-14 17:39:12');
INSERT INTO `sys_role` VALUES (2, 'HR_STAFF', 'HR专员', 'ALL', '2026-07-14 17:39:12', '2026-07-14 17:39:12');
INSERT INTO `sys_role` VALUES (3, 'DEPT_MANAGER', '部门主管', 'DEPT_TREE', '2026-07-14 17:39:12', '2026-07-14 17:39:12');
INSERT INTO `sys_role` VALUES (4, 'FINANCE', '财务专员', 'PAYROLL', '2026-07-14 17:39:12', '2026-07-14 17:39:12');
INSERT INTO `sys_role` VALUES (5, 'EMPLOYEE', '普通员工', 'SELF', '2026-07-14 17:39:12', '2026-07-14 17:39:12');

-- ----------------------------
-- Table structure for sys_role_permission
-- ----------------------------
DROP TABLE IF EXISTS `sys_role_permission`;
CREATE TABLE `sys_role_permission`  (
  `role_id` bigint NOT NULL COMMENT '关联 sys_role.id',
  `permission_id` bigint NOT NULL COMMENT '关联 sys_permission.id',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`role_id`, `permission_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '角色权限关联' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_role_permission
-- ----------------------------

-- ----------------------------
-- Table structure for sys_user
-- ----------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '手机号（登录账号）',
  `password_hash` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `password_changed_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'PRD §11.2 90天轮换',
  `employee_id` bigint NULL DEFAULT NULL COMMENT '关联员工ID，入职审批通过后写入',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '1=启用 0=禁用',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_username`(`username` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '系统用户表，PRD §2.1' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_user
-- ----------------------------

-- ----------------------------
-- Table structure for sys_user_role
-- ----------------------------
DROP TABLE IF EXISTS `sys_user_role`;
CREATE TABLE `sys_user_role`  (
  `user_id` bigint NOT NULL COMMENT '关联 sys_user.id',
  `role_id` bigint NOT NULL COMMENT '关联 sys_role.id',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`, `role_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户角色关联' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_user_role
-- ----------------------------

-- ----------------------------
-- Table structure for transfer_application
-- ----------------------------
DROP TABLE IF EXISTS `transfer_application`;
CREATE TABLE `transfer_application`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `instance_id` bigint NULL DEFAULT NULL,
  `employee_id` bigint NOT NULL,
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `from_department_id` bigint NOT NULL,
  `new_department_id` bigint NOT NULL,
  `new_position_id` bigint NULL DEFAULT NULL,
  `new_job_level` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `new_manager_id` bigint NULL DEFAULT NULL,
  `salary_adjustment` decimal(12, 2) NULL DEFAULT NULL,
  `effective_date` date NOT NULL,
  `reason` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `created_by` bigint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_employee`(`employee_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '调岗申请表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of transfer_application
-- ----------------------------

-- ----------------------------
-- Table structure for workday_config
-- ----------------------------
DROP TABLE IF EXISTS `workday_config`;
CREATE TABLE `workday_config`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `day_of_week` tinyint NOT NULL COMMENT '1=周一..7=周日',
  `is_workday` tinyint NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_dow`(`day_of_week` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 8 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '工作日配置' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of workday_config
-- ----------------------------
INSERT INTO `workday_config` VALUES (1, 1, 1);
INSERT INTO `workday_config` VALUES (2, 2, 1);
INSERT INTO `workday_config` VALUES (3, 3, 1);
INSERT INTO `workday_config` VALUES (4, 4, 1);
INSERT INTO `workday_config` VALUES (5, 5, 1);
INSERT INTO `workday_config` VALUES (6, 6, 0);
INSERT INTO `workday_config` VALUES (7, 7, 0);

SET FOREIGN_KEY_CHECKS = 1;
