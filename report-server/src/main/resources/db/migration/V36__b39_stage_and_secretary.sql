-- 批39③④：成绩学段隔离 + 请假四级审批（书记终审）
-- ③ t_grade.stage：学段（PRIMARY=小学部 / JUNIOR=初中部），按年级名称预填；
--    新建年级未填 stage 时后端视为不限制（安全默认，不锁人）
ALTER TABLE t_grade ADD COLUMN stage VARCHAR(8) NULL COMMENT '学段：PRIMARY=小学部 JUNIOR=初中部';
UPDATE t_grade SET stage = CASE
    WHEN name REGEXP 'G[789]|初|七|八|九' THEN 'JUNIOR'
    WHEN name REGEXP 'G[1-6]|[一二三四五六]年级|小' THEN 'PRIMARY'
    ELSE NULL END;

-- ③ t_user.stage_scope：领导分管学段（NULL=全部学段；仅 LEADER 使用，管理端可改）
ALTER TABLE t_user ADD COLUMN stage_scope VARCHAR(8) NULL COMMENT '领导分管学段：PRIMARY/JUNIOR，NULL=全部';
