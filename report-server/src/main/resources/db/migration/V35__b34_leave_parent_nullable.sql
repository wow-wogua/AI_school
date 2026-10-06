-- 批34 修复：批32 起请假改为教师代录（家长只收通知不提交），t_student_leave.parent_id
-- 仅批27 旧行有值，新单不再填 → 列改可空；否则教师代录插入即 500（本地验证链 2026-10-06 实测捕获）
ALTER TABLE t_student_leave MODIFY COLUMN parent_id BIGINT NULL COMMENT '提交家长 user_id（批27 旧流程遗留；批32 起教师代录为空）';
