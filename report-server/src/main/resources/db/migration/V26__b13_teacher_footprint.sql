-- 批13 教师成长足迹（原始需求三：公开课/听课/奖项/讲座/读书笔记/工作室六维）
-- 五类入本表；奖项维度聚合既有 t_teacher_honor（不双轨，风采墙保持独立）
CREATE TABLE t_teacher_footprint (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  teacher_id BIGINT NOT NULL,
  type VARCHAR(16) NOT NULL COMMENT 'OPEN_CLASS|OBSERVE|LECTURE|READING|STUDIO',
  title VARCHAR(128) NOT NULL,
  foot_date DATE NOT NULL,
  place VARCHAR(100) NULL,
  note VARCHAR(500) NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_fp_teacher (teacher_id),
  KEY idx_fp_type (type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='teacher growth footprint';
