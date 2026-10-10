-- 批44① 任课表导入：学科字典补齐（综合实践/人工智能，分工表在用而 t_subject 原无）
INSERT INTO t_subject (name, short_name, type, sort, regular_sort)
SELECT '综合实践', '综合', '国家课程', 16, 16 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM t_subject WHERE name = '综合实践');
INSERT INTO t_subject (name, short_name, type, sort, regular_sort)
SELECT '人工智能', 'AI', '校本课程', 17, 17 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM t_subject WHERE name = '人工智能');
