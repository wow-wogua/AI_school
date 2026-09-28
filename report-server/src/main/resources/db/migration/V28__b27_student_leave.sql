-- 批27：学生请假（家长替孩子提交，独立单级工单——OA 引擎是教职工口径不复用）
-- 单级审批拍板（9-26）：任何一位教师批准即生效，本班老师优先不强制；门卫登记离校/返校
CREATE TABLE IF NOT EXISTS t_student_leave (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  student_id BIGINT NOT NULL COMMENT '学生 id',
  parent_id BIGINT NOT NULL COMMENT '提交家长 user_id',
  leave_type VARCHAR(20) NOT NULL DEFAULT '病假' COMMENT '病假/事假/其他',
  start_date DATE NOT NULL COMMENT '请假开始日',
  end_date DATE NOT NULL COMMENT '请假结束日（含当天）',
  reason VARCHAR(300) NOT NULL COMMENT '请假事由',
  photos VARCHAR(600) DEFAULT NULL COMMENT '凭证照片 objectName JSON 数组（≤3 张，MinIO student_leave/ 前缀）',
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING 待审批 / APPROVED 已批准 / REJECTED 已驳回 / CANCELLED 家长撤回',
  approver_id BIGINT DEFAULT NULL COMMENT '审批教师 user_id（任意一位教师）',
  approve_note VARCHAR(200) DEFAULT NULL COMMENT '审批意见（驳回时必填）',
  approve_time DATETIME DEFAULT NULL,
  leave_time DATETIME DEFAULT NULL COMMENT '实际离校时间（门卫登记）',
  leave_guard_id BIGINT DEFAULT NULL COMMENT '登记离校的门卫 user_id',
  return_time DATETIME DEFAULT NULL COMMENT '实际返校时间（门卫登记）',
  return_guard_id BIGINT DEFAULT NULL COMMENT '登记返校的门卫 user_id',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_sl_student (student_id),
  KEY idx_sl_parent (parent_id),
  KEY idx_sl_status (status),
  KEY idx_sl_date (start_date, end_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '学生请假单（批27，家长提交+单级审批+门卫登记）';
