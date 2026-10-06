-- V32（2026-10-06）：二次优化角色基建——新增 级长/学成中心主任/生活老师/招采 四角色
-- + 年级岗位绑定表（级长-年级；生活老师分年级方案到位后复用同表，duty=DORM）
-- + 请假分级阈值配置化（政策数字不写死：管理端可改）
CREATE TABLE IF NOT EXISTS t_grade_binding (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id     BIGINT NOT NULL COMMENT '→t_user',
  grade_id    BIGINT NOT NULL COMMENT '→t_grade',
  duty        VARCHAR(20) NOT NULL DEFAULT 'GRADE_LEADER' COMMENT '岗位：GRADE_LEADER=级长 / DORM=生活老师分年级（预留）',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_grade_duty (user_id, grade_id, duty),
  KEY idx_grade_duty (grade_id, duty)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='年级岗位绑定';

INSERT INTO t_sys_config (cfg_key, cfg_value) VALUES
('leave_level1_days', '3'),
('leave_level2_days', '7'),
('leave_max_days', '30')
ON DUPLICATE KEY UPDATE cfg_key = cfg_key;

-- 开发种子便利（生产无 jizhang 账号，此语句自动空跑）：本地演示级长绑定初一
INSERT INTO t_grade_binding (user_id, grade_id, duty)
SELECT id, 1, 'GRADE_LEADER' FROM t_user WHERE username = 'jizhang' LIMIT 1
ON DUPLICATE KEY UPDATE user_id = user_id;
