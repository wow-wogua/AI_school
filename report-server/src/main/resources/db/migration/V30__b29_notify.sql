-- 批29：通知中心 + 企微群机器人 + 探活告警 + 体检周报
-- t_notification：App 内通知中心（点对点待办/结果类，广播类仍走内容模块）
CREATE TABLE IF NOT EXISTS t_notification (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id     BIGINT NOT NULL COMMENT '接收人',
  type        VARCHAR(32)  NOT NULL COMMENT 'OA_TODO/OA_RESULT/LEAVE_TODO/LEAVE_RESULT/REGISTER_TODO/SYSTEM/ALERT',
  title       VARCHAR(200) NOT NULL,
  content     VARCHAR(500),
  link        VARCHAR(200) COMMENT '点击直达的前端路由（可空）',
  read_time   DATETIME COMMENT 'NULL=未读',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_user_read (user_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知中心（批29）';

-- 外发通道/告警配置（管理端「群机器人与告警」页签读写）
INSERT INTO t_sys_config (cfg_key, cfg_value) VALUES
  ('wecom_webhook_url', ''),
  ('wecom_enabled', '0'),
  ('wecom_push_approvals', '1'),
  ('alert_enabled', '1'),
  ('alert_interval_min', '5'),
  ('weekly_report_enabled', '1'),
  ('weekly_report_day', '5')
ON DUPLICATE KEY UPDATE cfg_value = cfg_value;
