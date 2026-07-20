-- ============================================================
-- V9: AI 智能助理 — 知识库元数据 + 权限种子
-- 向量存 Qdrant，不在 MySQL
-- ============================================================

CREATE TABLE IF NOT EXISTS ai_knowledge_doc (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    title         VARCHAR(128)  NOT NULL,
    file_name     VARCHAR(256)  NOT NULL,
    status        VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/READY/FAILED',
    enabled       TINYINT       NOT NULL DEFAULT 1,
    chunk_count   INT           NOT NULL DEFAULT 0,
    error_message VARCHAR(512)  NULL,
    created_by    BIGINT        NULL,
    created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_status (status),
    KEY idx_enabled (enabled)
) COMMENT='AI 知识库文档元数据';

CREATE TABLE IF NOT EXISTS ai_chat_log (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id       BIGINT        NOT NULL,
    question      VARCHAR(512)  NOT NULL,
    route_pushed  VARCHAR(256)  NULL,
    created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_user (user_id)
) COMMENT='AI 对话摘要日志（可选）';

INSERT INTO sys_permission (code, name, module, type) VALUES
    ('ai:chat', 'AI对话', 'ai', 'API'),
    ('ai:knowledge:manage', 'AI知识库管理', 'ai', 'API');

-- ai:chat → 全部角色
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE p.code = 'ai:chat'
  AND r.code IN ('SYS_ADMIN', 'HR_STAFF', 'DEPT_MANAGER', 'FINANCE', 'FINANCE_MANAGER', 'EMPLOYEE');

-- ai:knowledge:manage → 仅 SYS_ADMIN
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE p.code = 'ai:knowledge:manage'
  AND r.code = 'SYS_ADMIN';
