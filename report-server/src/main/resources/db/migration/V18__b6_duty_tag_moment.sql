-- V18（2026-09-20 批6）：①值班排班+开关（漏项C1：仅当日值班老师可做日常评价）②微光场景标签字典（漏项H：教师可自建）
-- ③t_moment.photo_url 放开 NULL（漏项D：加分评价自动同步微光 EVAL_SYNC 无照片，仅进学生档案/家长孩子流，不进班级墙）
CREATE TABLE IF NOT EXISTS t_sys_config (
  cfg_key     VARCHAR(50) PRIMARY KEY,
  cfg_value   VARCHAR(200) NOT NULL,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统开关（键值，管理端可改）';

INSERT INTO t_sys_config (cfg_key, cfg_value) VALUES ('duty_check', '0')
  ON DUPLICATE KEY UPDATE cfg_value = cfg_value;

CREATE TABLE IF NOT EXISTS t_duty_schedule (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  duty_date   DATE NOT NULL COMMENT '值班日',
  teacher_id  BIGINT NOT NULL COMMENT '值班教师（t_user.id）',
  note        VARCHAR(100) DEFAULT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_date_teacher (duty_date, teacher_id),
  KEY idx_date (duty_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='日常评价值班排班（开关开启后仅当日值班教师可评）';

CREATE TABLE IF NOT EXISTS t_moment_tag (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  name           VARCHAR(32) NOT NULL COMMENT '场景标签名',
  sort           INT NOT NULL DEFAULT 0,
  create_user_id BIGINT DEFAULT NULL COMMENT '创建教师（种子为 NULL）',
  create_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='微光场景标签字典（教师可自建）';

INSERT INTO t_moment_tag (name, sort) VALUES
  ('课堂专注', 1), ('作业优秀', 2), ('劳动实践', 3), ('艺术风采', 4),
  ('运动健将', 5), ('助人为乐', 6), ('文明礼仪', 7), ('进步之星', 8)
ON DUPLICATE KEY UPDATE sort = VALUES(sort);

ALTER TABLE t_moment MODIFY COLUMN photo_url VARCHAR(255) NULL
  COMMENT 'MinIO 对象名 moment/{classId}/{uuid}.{ext}；EVAL_SYNC（加分同步）无照片';
