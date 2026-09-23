-- 批8 真实名册导入：学生宿舍信息 + 学科字典补齐（化学/科学/心理）
ALTER TABLE t_student
  ADD COLUMN dorm_building VARCHAR(32) DEFAULT NULL COMMENT '宿舍楼（布鲁森/毓秀楼/培英楼）',
  ADD COLUMN dorm_room     VARCHAR(16) DEFAULT NULL COMMENT '宿舍号',
  ADD COLUMN dorm_bed      VARCHAR(16) DEFAULT NULL COMMENT '床位号';

-- 教师通讯录含化学/科学/心理科组，t_subject 原无（name 有 UNIQUE，先查后插幂等）
INSERT INTO t_subject (name, short_name, type, sort, regular_sort)
SELECT '化学', '化学', '国家课程', 13, 13 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM t_subject WHERE name = '化学');
INSERT INTO t_subject (name, short_name, type, sort, regular_sort)
SELECT '科学', '科学', '国家课程', 14, 14 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM t_subject WHERE name = '科学');
INSERT INTO t_subject (name, short_name, type, sort, regular_sort)
SELECT '心理', '心理', '国家课程', 15, 15 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM t_subject WHERE name = '心理');
