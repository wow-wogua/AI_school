-- V13（2026-09-19）：微光信箱家长上传（批2-3 方案A）——来源列区分教师随手拍/家长上传；
-- 家长上传仅进该孩子成长档案（家长+班主任可见），不进班级公开墙与 AI 报告素材
ALTER TABLE t_moment ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'TEACHER' COMMENT 'TEACHER=教师随手拍 / PARENT=家长上传（仅孩子档案）';
