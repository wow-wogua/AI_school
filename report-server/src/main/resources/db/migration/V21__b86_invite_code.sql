-- 批8.6 家长自助注册绑定：邀请码（一码一学生，重新生成时旧未用码作废）
CREATE TABLE IF NOT EXISTS t_invite_code (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  student_id  BIGINT      NOT NULL COMMENT '学生 t_student.id',
  code        VARCHAR(10) NOT NULL COMMENT '邀请码（8 位大写字母数字，去除易混字符）',
  status      TINYINT     NOT NULL DEFAULT 0 COMMENT '0=未用 1=已用 2=已作废(重新生成)',
  used_by     BIGINT      DEFAULT NULL COMMENT '使用人 t_user.id（家长）',
  create_time DATETIME    DEFAULT CURRENT_TIMESTAMP,
  KEY idx_student (student_id),
  KEY idx_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='家长绑定邀请码（批8.6）';

-- 绑定来源：0=管理端/班主任操作（不限量，爷爷奶奶兜底） 1=家长自助注册（每生上限 2）
ALTER TABLE t_parent_binding ADD COLUMN source TINYINT NOT NULL DEFAULT 0 COMMENT '0=管理端/班主任 1=家长自助注册' AFTER relation;
