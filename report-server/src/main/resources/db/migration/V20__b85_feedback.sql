-- 批8.5 自助维护：意见反馈（教师/家长/领导均可提交，管理端处理）
CREATE TABLE IF NOT EXISTS t_feedback (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id     BIGINT       NOT NULL COMMENT '提交人 t_user.id',
  role        VARCHAR(16)  NOT NULL COMMENT '提交时角色',
  content     VARCHAR(1000) NOT NULL COMMENT '反馈内容',
  contact     VARCHAR(64)  DEFAULT NULL COMMENT '联系方式（默认账号手机号，提交时可改）',
  status      TINYINT      NOT NULL DEFAULT 0 COMMENT '0=待处理 1=已处理',
  handle_note VARCHAR(1000) DEFAULT NULL COMMENT '处理说明',
  handler_id  BIGINT       DEFAULT NULL COMMENT '处理人 t_user.id',
  create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_status_time (status, create_time)
) COMMENT '意见反馈（批8.5）';
