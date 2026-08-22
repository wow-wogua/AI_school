-- V2（2026-08-22）：审计日志表 + AI 任务 token 用量列
CREATE TABLE IF NOT EXISTS t_audit_log (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id     BIGINT DEFAULT NULL,
  username    VARCHAR(64) DEFAULT NULL,
  method      VARCHAR(8)  NOT NULL,
  uri         VARCHAR(255) NOT NULL,
  body        VARCHAR(512) DEFAULT NULL COMMENT '请求体摘要（密码类接口与 multipart 不记录）',
  status      INT NOT NULL,
  ip          VARCHAR(64) DEFAULT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_time (create_time),
  KEY idx_user (user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审计日志（写操作请求级）';

ALTER TABLE t_ai_task ADD COLUMN prompt_tokens INT DEFAULT NULL COMMENT 'LLM 输入 token 用量';
ALTER TABLE t_ai_task ADD COLUMN completion_tokens INT DEFAULT NULL COMMENT 'LLM 输出 token 用量';
