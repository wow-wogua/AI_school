-- 批11：场地申请（OA 第四类型 VENUE，级数可配同物资/请假）+ 谈心记录（独立 CRUD 不走审批流）
INSERT INTO t_sys_config (cfg_key, cfg_value) VALUES
  ('oa_venue_levels', '1'), ('oa_venue_l1', ''), ('oa_venue_l2', ''), ('oa_venue_l3', '')
ON DUPLICATE KEY UPDATE cfg_key = cfg_key;

-- 场地字典（管理端 CRUD；申请单 detail 存场地名快照，删改场地不影响历史单据）
CREATE TABLE IF NOT EXISTS t_venue (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(50) NOT NULL COMMENT '场地名称',
  location VARCHAR(100) DEFAULT NULL COMMENT '位置说明',
  capacity INT DEFAULT NULL COMMENT '容纳人数',
  status INT NOT NULL DEFAULT 1 COMMENT '1 启用 / 0 停用',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_venue_name (name),
  KEY idx_venue_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '场地字典（批11）';

-- 谈心记录（教师对可见班级学生；家长不可见）
CREATE TABLE IF NOT EXISTS t_talk (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  teacher_id BIGINT NOT NULL COMMENT '记录教师 user_id',
  student_id BIGINT NOT NULL COMMENT '学生 id',
  talk_date DATE NOT NULL COMMENT '谈心日期',
  talk_type VARCHAR(20) NOT NULL DEFAULT '学业' COMMENT '学业/心理/纪律/生活/其他',
  content VARCHAR(500) NOT NULL COMMENT '谈心内容',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_talk_teacher (teacher_id),
  KEY idx_talk_student (student_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '谈心记录（批11）';
