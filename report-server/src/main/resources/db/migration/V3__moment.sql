-- V3（2026-08-29）：微光信箱——随手拍记录学生闪光时刻（一条微光可关联多名学生）
CREATE TABLE IF NOT EXISTS t_moment (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  teacher_id  BIGINT NOT NULL COMMENT '记录教师（t_user.id）',
  class_id    BIGINT NOT NULL COMMENT '班级',
  photo_url   VARCHAR(255) NOT NULL COMMENT 'MinIO 对象名 moment/{classId}/{uuid}.{ext}',
  note        VARCHAR(500) DEFAULT NULL COMMENT '教师备注',
  scene_tag   VARCHAR(32) NOT NULL COMMENT '场景标签（课堂专注/作业优秀/劳动实践/艺术风采/运动健将/助人为乐/文明礼仪/进步之星）',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_class_time (class_id, create_time),
  KEY idx_teacher (teacher_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='微光信箱·随手拍';

CREATE TABLE IF NOT EXISTS t_moment_student (
  moment_id   BIGINT NOT NULL,
  student_id  BIGINT NOT NULL,
  PRIMARY KEY (moment_id, student_id),
  KEY idx_student (student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='微光·学生关联';
