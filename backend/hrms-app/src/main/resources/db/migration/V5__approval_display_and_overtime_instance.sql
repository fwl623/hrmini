-- 审批实例展示字段落库（替代内存 displays/nodeCache）
ALTER TABLE approval_instance
    ADD COLUMN title VARCHAR(128) NULL COMMENT '审批标题' AFTER business_key,
    ADD COLUMN applicant_name VARCHAR(64) NULL AFTER title,
    ADD COLUMN applicant_dept VARCHAR(64) NULL AFTER applicant_name,
    ADD COLUMN business_no VARCHAR(64) NULL AFTER applicant_dept,
    ADD COLUMN business_summary VARCHAR(256) NULL AFTER business_no,
    ADD COLUMN nodes_json TEXT NULL COMMENT '本实例解析后的审批链 JSON' AFTER business_summary;

-- 加班申请关联审批实例
ALTER TABLE overtime_application
    ADD COLUMN instance_id BIGINT NULL COMMENT '审批实例ID' AFTER status;
