-- V4（2026-08-29）：教师档案——工号/性别/任教学科/职称/职务/教龄/照片/简介/入职年月（一人一行，跟 t_user.id）
CREATE TABLE IF NOT EXISTS t_teacher_profile (
  user_id        BIGINT PRIMARY KEY COMMENT '教师账号（t_user.id）',
  employee_no    VARCHAR(32) DEFAULT NULL COMMENT '工号（校内唯一）',
  gender         VARCHAR(4)  DEFAULT NULL COMMENT '性别（男/女）',
  subject_id     BIGINT       DEFAULT NULL COMMENT '任教学科（t_subject.id）',
  title          VARCHAR(16) DEFAULT NULL COMMENT '职称（二级/一级/高级/正高级教师等）',
  duty           VARCHAR(32) DEFAULT NULL COMMENT '职务（班主任/年级组长/教研组长等）',
  teaching_years INT          DEFAULT NULL COMMENT '教龄（年）',
  photo_url      VARCHAR(255) DEFAULT NULL COMMENT 'MinIO 对象名 teacher/{userId}/{uuid}.{ext}',
  intro          VARCHAR(500) DEFAULT NULL COMMENT '个人简介',
  hire_date      DATE         DEFAULT NULL COMMENT '入职年月',
  update_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_employee_no (employee_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='教师档案';
