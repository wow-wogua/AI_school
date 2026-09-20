-- V16（2026-09-20）：批4 成绩改造——考试录入开关（方案A 录入窗口）
-- 开(1)=教师可录、可见自己录入的成绩；关(0)=教师全不可见（ADMIN/LEADER 不受限）
ALTER TABLE t_exam ADD COLUMN entry_open TINYINT NOT NULL DEFAULT 1 COMMENT '录入窗口开关：1开=教师可录可见自己录入；0关=教师不可见（ADMIN/LEADER 不受限）';
