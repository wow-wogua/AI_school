-- 批10：教师请假（复用 OA 引擎，仅配置键）+ 报修单（文字+拍照凭证，独立工单不走审批流）
-- 请假=OA 第三类型 LEAVE，级数可配同物资（oa_leave_levels 1-3 默认 1）
INSERT INTO t_sys_config (cfg_key, cfg_value) VALUES
  ('oa_leave_levels', '1'), ('oa_leave_l1', ''), ('oa_leave_l2', ''), ('oa_leave_l3', '')
ON DUPLICATE KEY UPDATE cfg_key = cfg_key;

-- 报修单：提交（教师端文字+照片凭证）→ 处理（管理端完成/不予受理）
CREATE TABLE IF NOT EXISTS t_repair (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  reporter_id BIGINT NOT NULL COMMENT '报修人 user_id',
  location VARCHAR(100) NOT NULL COMMENT '故障地点/设施',
  description VARCHAR(500) NOT NULL COMMENT '故障描述',
  photos VARCHAR(600) DEFAULT NULL COMMENT '凭证照片 objectName JSON 数组（≤3 张，MinIO）',
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING 待处理 / DONE 已完成 / REJECTED 不予受理',
  handler_id BIGINT DEFAULT NULL COMMENT '处理人',
  handle_note VARCHAR(300) DEFAULT NULL COMMENT '处理说明',
  handle_time DATETIME DEFAULT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_repair_reporter (reporter_id),
  KEY idx_repair_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '报修单（批10）';
