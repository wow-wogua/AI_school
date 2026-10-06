-- 批34：素养维度案B —— 九格切换为 6 大核心素养 + 3 校本扬长格，18 要点全量二级指标。
-- 格 id 保持不变（t_grid_stat_* / t_class_grid_avg / t_grade_grid_avg 按 grid_id 聚合，平滑过渡）；
-- t_indicator 无外键、name 无唯一键（仅 PK id + idx_grid），按 id 覆写 + 补插 15-24，幂等可重放。
UPDATE t_grid SET name = '人文底蕴' WHERE id = 1;
UPDATE t_grid SET name = '科学精神' WHERE id = 2;
UPDATE t_grid SET name = '学会学习' WHERE id = 3;
UPDATE t_grid SET name = '健康生活' WHERE id = 4;
UPDATE t_grid SET name = '责任担当' WHERE id = 5;
UPDATE t_grid SET name = '实践创新' WHERE id = 6;
UPDATE t_grid SET name = '学业扬长' WHERE id = 7;
UPDATE t_grid SET name = '艺术特长' WHERE id = 8;
UPDATE t_grid SET name = '体育锻炼' WHERE id = 9;

INSERT INTO t_indicator (id, grid_id, name, direction, default_score) VALUES
(1, 1, '人文积淀', '正', 1),
(2, 1, '人文情怀', '正', 1),
(3, 1, '审美情趣', '正', 1),
(4, 2, '理性思维', '正', 1),
(5, 2, '批判质疑', '正', 1),
(6, 2, '勇于探究', '正', 1),
(7, 3, '乐学善学', '正', 1),
(8, 3, '勤于反思', '正', 1),
(9, 3, '信息意识', '正', 1),
(10, 4, '珍爱生命', '正', 1),
(11, 4, '健全人格', '正', 1),
(12, 4, '自我管理', '正', 1),
(13, 5, '社会责任', '正', 1),
(14, 5, '国家认同', '正', 1),
(15, 5, '国际理解', '正', 1),
(16, 6, '劳动意识', '正', 1),
(17, 6, '问题解决', '正', 1),
(18, 6, '技术运用', '正', 1),
(19, 7, '课堂表现', '正', 1),
(20, 7, '作业表现', '正', 1),
(21, 8, '艺术测评', '正', 1),
(22, 8, '特长展示', '正', 1),
(23, 9, '体育训练', '正', 1),
(24, 9, '健康体制', '正', 1)
ON DUPLICATE KEY UPDATE grid_id = VALUES(grid_id), name = VALUES(name),
  direction = VALUES(direction), default_score = VALUES(default_score);

-- 九格介绍页新文案（REPLACE 未命中 = 已迁移过，天然幂等）
UPDATE t_report_template SET sections = REPLACE(sections,
  '“扬长教育”是我校的育人特色：发现每一位学生的长处，让长处更长、亮点更亮。品德铸魂，行为立范，身心砺志，劳动立勤，学业启思，创新促悟，艺术润美，特长扬长，体育强健。五育并举，扬长出彩，如同九色光谱，照亮每个孩子独一无二的出彩人生。',
  '“扬长教育”是我校的育人特色：发现每一位学生的长处，让长处更长、亮点更亮。人文底蕴奠定底色，科学精神启迪智慧，学会学习终身受益，健康生活滋养身心，责任担当涵养品格，实践创新激发活力；学业扬长、艺术特长、体育锻炼三大校本扬长格，让优势更优、特长更特。素养为基，扬长出彩，如同九色光谱，照亮每个孩子独一无二的出彩人生。')
WHERE id = 1;

-- 冗余名串同步：t_coin_income.module 为内联「格-指标」文本（无 join），历史行换新名保证展示一致
UPDATE t_coin_income SET module = REPLACE(module, '学业水平-学科水平', '学业扬长-课堂表现');
UPDATE t_coin_income SET module = REPLACE(module, '学业水平-作业表现', '学业扬长-作业表现');
UPDATE t_coin_income SET module = REPLACE(module, '学业水平-课堂表现', '学会学习-乐学善学');
UPDATE t_coin_income SET module = REPLACE(module, '行为习惯-行为表现', '责任担当-社会责任');
UPDATE t_coin_income SET module = REPLACE(module, '品德修养-文化认同', '人文底蕴-人文积淀');
UPDATE t_coin_income SET module = REPLACE(module, '实践创新-管理能力', '实践创新-问题解决');
UPDATE t_coin_income SET module = REPLACE(module, '劳动实践-劳动习惯', '健康生活-自我管理');

-- 雷达页优势/待努力维度串（JSON 数组内格名）同步
UPDATE t_student_analysis SET radar_advantages = REPLACE(radar_advantages, '"学业水平"', '"学业扬长"'),
                              radar_to_improve = REPLACE(radar_to_improve, '"学业水平"', '"学业扬长"');
UPDATE t_student_analysis SET radar_advantages = REPLACE(radar_advantages, '"行为习惯"', '"学会学习"'),
                              radar_to_improve = REPLACE(radar_to_improve, '"行为习惯"', '"学会学习"');
UPDATE t_student_analysis SET radar_advantages = REPLACE(radar_advantages, '"身心健康"', '"人文底蕴"'),
                              radar_to_improve = REPLACE(radar_to_improve, '"身心健康"', '"人文底蕴"');
