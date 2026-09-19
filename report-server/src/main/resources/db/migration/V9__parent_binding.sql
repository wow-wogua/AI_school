-- V9（2026-09-19）：家长账号基座——学生-家长绑定关系（家长账号存于 t_user，role='PARENT'，一号可绑多名学生）
CREATE TABLE IF NOT EXISTS t_parent_binding (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  parent_user_id BIGINT NOT NULL COMMENT '家长账号 →t_user（role=PARENT）',
  student_id     BIGINT NOT NULL COMMENT '→t_student',
  relation       VARCHAR(20) NOT NULL DEFAULT '家长' COMMENT '与学生关系：父/母/家长（学校可自定义）',
  create_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_parent_student (parent_user_id, student_id),
  KEY idx_student (student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学生-家长绑定';
