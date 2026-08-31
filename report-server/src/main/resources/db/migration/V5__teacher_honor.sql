CREATE TABLE IF NOT EXISTS t_teacher_honor (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  teacher_id  BIGINT NOT NULL COMMENT '教师（t_user.id）',
  name        VARCHAR(128) NOT NULL COMMENT '奖项名称',
  level       VARCHAR(32) DEFAULT NULL COMMENT '级别（国家级/省级/市级/区级/校级）',
  issuer      VARCHAR(128) DEFAULT NULL COMMENT '颁发单位',
  honor_date  DATE DEFAULT NULL COMMENT '获奖日期',
  file_url    VARCHAR(255) NOT NULL COMMENT 'MinIO 对象名 teacher-honor/{teacherId}/{uuid}.{ext}',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_teacher (teacher_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='教师成就（证书/荣誉）';
