-- V17（2026-09-20）：批5 家长版报告——t_report 加家长版对象列（方案A 家长版去成绩板块）
-- 教师生成报告时同任务双渲染：file_url=教师版（现状不变），parent_file_url=家长版（删成绩板块 15 页）
ALTER TABLE t_report ADD COLUMN parent_file_url VARCHAR(300) DEFAULT NULL COMMENT '家长版PDF对象名（去成绩板块；NULL=未生成/渲染失败）';
